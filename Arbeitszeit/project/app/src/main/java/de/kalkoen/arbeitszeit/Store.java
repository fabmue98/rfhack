package de.kalkoen.arbeitszeit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zentraler Datenbestand (Singleton pro Prozess), geteilt von Activity und Widget.
 * Alle Zugriffe laufen auf dem Main-Thread.
 */
final class Store {
    static final String PREFS = "arbeitszeit_prefs";
    private static final String KEY_DATA = "days_json";
    private static final String KEY_DATA_CORRUPT = "days_json_corrupt";
    /** Alter Float-Schlüssel, bleibt zur Abwärtskompatibilität erhalten. */
    private static final String KEY_HOURLY_WAGE = "hourly_wage";
    private static final String KEY_HOURLY_WAGE_STR = "hourly_wage_str";
    private static final String KEY_DATA_VERSION = "data_version";
    private static final int DATA_VERSION = 3;

    private static Store instance;

    static synchronized Store get(Context context) {
        if (instance == null) {
            Store s = new Store(context.getApplicationContext());
            instance = s;
            s.init();
        }
        return instance;
    }

    private final Context app;
    final LinkedHashMap<String, DayRecord> days = new LinkedHashMap<String, DayRecord>();
    double hourlyWage = 0.0;
    String currentDayKey;
    /** Wird bei jedem Speichern erhöht (für Rückgängig-Prüfung). */
    long revision = 0L;

    private Store(Context app) {
        this.app = app;
    }

    // ---------------------------------------------------------------- laden / speichern

    private void init() {
        boolean changed = load();
        long now = System.currentTimeMillis();
        currentDayKey = DayTime.dayKey(now);
        changed |= normalizeOldActiveRecords(now);
        if (!days.containsKey(currentDayKey)) {
            ensureDay(currentDayKey);
            changed = true;
        }
        if (changed) save();
    }

    /** @return true, wenn beim Laden Daten migriert oder repariert wurden. */
    private boolean load() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int storedVersion = prefs.getInt(KEY_DATA_VERSION, 1);
        boolean changed = storedVersion < DATA_VERSION;

        String wageStr = prefs.getString(KEY_HOURLY_WAGE_STR, null);
        if (wageStr != null) {
            try {
                hourlyWage = Double.parseDouble(wageStr);
            } catch (NumberFormatException ex) {
                hourlyWage = Math.round(prefs.getFloat(KEY_HOURLY_WAGE, 0f) * 100.0) / 100.0;
            }
        } else {
            // Früher als float gespeichert: auf Cent runden, um Float-Artefakte zu entfernen.
            hourlyWage = Math.round(prefs.getFloat(KEY_HOURLY_WAGE, 0f) * 100.0) / 100.0;
        }

        days.clear();
        String raw = prefs.getString(KEY_DATA, "{}");
        try {
            days.putAll(DayJson.parse(raw).days);
        } catch (JSONException ex) {
            // Defekte Daten nicht stillschweigend überschreiben, sondern sichern.
            prefs.edit().putString(KEY_DATA_CORRUPT, raw).apply();
            days.clear();
            changed = true;
        }

        long now = System.currentTimeMillis();
        for (DayRecord record : days.values()) {
            if (storedVersion < 2) migrateV1ToV2(record, now);
            if (record.rate < 0.0) record.rate = hourlyWage;
            Intervals.sortEntriesOf(record);
            Intervals.sanitizeState(record, now);
        }
        return changed;
    }

    /** Altes Modell: Pausen trennten Arbeitsblöcke; jetzt liegen Pausen innerhalb eines Blocks. */
    private void migrateV1ToV2(DayRecord record, long now) {
        Intervals.sortEntriesOf(record);
        for (TimeEntry pause : record.breaks) {
            long pauseEnd = Intervals.endOf(pause, now);
            TimeEntry before = null;
            TimeEntry after = null;
            for (TimeEntry work : record.work) {
                if (work.end != 0L && Math.abs(work.end - pause.start) <= 2000L) before = work;
                if (Math.abs(work.start - pauseEnd) <= 2000L) after = work;
            }
            if (before != null && after != null && before != after) {
                before.end = after.end;
                record.work.remove(after);
            } else if (pause.end == 0L && DayRecord.STATE_PAUSED.equals(record.state) && before != null) {
                before.end = 0L;
            }
        }
        Intervals.sort(record.work);
    }

    void save() {
        try {
            SharedPreferences.Editor editor = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            editor.putString(KEY_DATA, DayJson.daysToJson(days).toString());
            editor.putString(KEY_HOURLY_WAGE_STR, Double.toString(hourlyWage));
            editor.putFloat(KEY_HOURLY_WAGE, (float) hourlyWage);
            editor.putInt(KEY_DATA_VERSION, DATA_VERSION);
            editor.apply();
        } catch (JSONException ignored) {
            // Wird beim Serialisieren von Zahlen/Strings nicht erwartet.
        }
        revision++;
        TimerWidgetProvider.refresh(app);
    }

    // ---------------------------------------------------------------- Tage

    DayRecord ensureDay(String key) {
        DayRecord record = days.get(key);
        if (record == null) {
            record = new DayRecord();
            record.rate = hourlyWage;
            days.put(key, record);
        } else if (record.rate < 0.0) {
            record.rate = hourlyWage;
        }
        return record;
    }

    DayRecord today() {
        return ensureDay(currentDayKey);
    }

    /** Offene Zustände vergangener Tage am Tagesende abschließen. */
    private boolean normalizeOldActiveRecords(long now) {
        boolean changed = false;
        String todayKey = DayTime.dayKey(now);
        for (Map.Entry<String, DayRecord> mapEntry : days.entrySet()) {
            DayRecord record = mapEntry.getValue();
            if (mapEntry.getKey().equals(todayKey) || !record.isRunning()) continue;
            long end = DayTime.startOfNextDay(DayTime.millisForDayKey(mapEntry.getKey()));
            Intervals.closeActive(record.breaks, end);
            Intervals.closeActive(record.work, end);
            record.state = DayRecord.STATE_STOPPED;
            changed = true;
        }
        return changed;
    }

    /**
     * Tageswechsel: laufende Arbeit/Pause wird um Mitternacht geteilt und am neuen Tag fortgesetzt.
     * @return true, wenn der Tag gewechselt hat
     */
    boolean ensureCurrentDay() {
        long now = System.currentTimeMillis();
        String newKey = DayTime.dayKey(now);
        if (currentDayKey == null) {
            currentDayKey = newKey;
            ensureDay(currentDayKey);
            return false;
        }
        if (currentDayKey.equals(newKey)) return false;

        long boundary = DayTime.startOfDay(now);
        DayRecord oldRecord = ensureDay(currentDayKey);
        String oldState = oldRecord.state;

        if (DayRecord.STATE_WORKING.equals(oldState)) {
            Intervals.closeActive(oldRecord.work, boundary);
        } else if (DayRecord.STATE_PAUSED.equals(oldState)) {
            Intervals.closeActive(oldRecord.breaks, boundary);
            Intervals.closeActive(oldRecord.work, boundary);
        }
        oldRecord.state = DayRecord.STATE_STOPPED;

        currentDayKey = newKey;
        DayRecord newRecord = ensureDay(currentDayKey);
        if (DayRecord.STATE_WORKING.equals(oldState)) {
            newRecord.work.add(new TimeEntry(boundary, 0L));
            newRecord.state = DayRecord.STATE_WORKING;
        } else if (DayRecord.STATE_PAUSED.equals(oldState)) {
            newRecord.work.add(new TimeEntry(boundary, 0L));
            newRecord.breaks.add(new TimeEntry(boundary, 0L));
            newRecord.state = DayRecord.STATE_PAUSED;
        }
        save();
        return true;
    }

    // ---------------------------------------------------------------- Zeiterfassung

    /** Start bzw. Fortsetzen. @return false, wenn bereits gearbeitet wird */
    boolean startOrResume() {
        ensureCurrentDay();
        DayRecord record = today();
        if (DayRecord.STATE_WORKING.equals(record.state)) return false;
        long now = System.currentTimeMillis();

        if (DayRecord.STATE_PAUSED.equals(record.state)) {
            Intervals.closeActive(record.breaks, now);
        } else {
            // Nicht in einen bereits erfassten Block hineinstarten (manuelle Einträge bis max. +1 min).
            long start = now;
            for (TimeEntry e : record.work) {
                if (e.end != 0L && e.end > start) start = e.end;
            }
            record.work.add(new TimeEntry(start, 0L));
        }
        record.state = DayRecord.STATE_WORKING;
        record.markUnpaid();
        save();
        return true;
    }

    /** Pause starten bzw. beenden. @return false, wenn noch nicht gestartet wurde */
    boolean togglePause() {
        ensureCurrentDay();
        DayRecord record = today();
        long now = System.currentTimeMillis();
        if (DayRecord.STATE_WORKING.equals(record.state)) {
            record.breaks.add(new TimeEntry(now, 0L));
            record.state = DayRecord.STATE_PAUSED;
        } else if (DayRecord.STATE_PAUSED.equals(record.state)) {
            Intervals.closeActive(record.breaks, now);
            record.state = DayRecord.STATE_WORKING;
        } else {
            return false;
        }
        record.markUnpaid();
        save();
        return true;
    }

    /** Pause starten (nur wenn gerade gearbeitet wird). */
    boolean startPause() {
        ensureCurrentDay();
        if (!DayRecord.STATE_WORKING.equals(today().state)) return false;
        return togglePause();
    }

    boolean stop() {
        ensureCurrentDay();
        DayRecord record = today();
        long now = System.currentTimeMillis();
        if (DayRecord.STATE_WORKING.equals(record.state)) {
            Intervals.closeActive(record.work, now);
        } else if (DayRecord.STATE_PAUSED.equals(record.state)) {
            Intervals.closeActive(record.breaks, now);
            Intervals.closeActive(record.work, now);
        } else {
            return false;
        }
        record.state = DayRecord.STATE_STOPPED;
        save();
        return true;
    }

    // ---------------------------------------------------------------- Backup

    String backupJson() throws JSONException {
        return DayJson.backupJson(days, hourlyWage);
    }

    /** Ersetzt alle Daten durch den Inhalt des Backups. @return Anzahl importierter Tage */
    int importBackup(DayJson.Parsed parsed) {
        long now = System.currentTimeMillis();
        days.clear();
        days.putAll(parsed.days);
        if (parsed.hourlyWage >= 0.0 && parsed.hourlyWage <= 10000.0) hourlyWage = parsed.hourlyWage;
        for (DayRecord record : days.values()) {
            if (record.rate < 0.0) record.rate = hourlyWage;
            Intervals.sortEntriesOf(record);
            Intervals.sanitizeState(record, now);
        }
        currentDayKey = DayTime.dayKey(now);
        normalizeOldActiveRecords(now);
        ensureDay(currentDayKey);
        save();
        return parsed.days.size();
    }
}
