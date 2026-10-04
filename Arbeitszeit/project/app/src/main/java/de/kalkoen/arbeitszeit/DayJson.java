package de.kalkoen.arbeitszeit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON-Umwandlung für Tage und Backups. */
final class DayJson {
    static final int BACKUP_FORMAT = 1;

    private DayJson() {}

    static JSONObject toJson(DayRecord r) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("state", r.state);
        obj.put("paid", r.paid);
        obj.put("paidAt", r.paidAt);
        obj.put("rate", r.rate);
        obj.put("note", r.note == null ? "" : r.note);
        obj.put("work", entriesToJson(r.work));
        obj.put("breaks", entriesToJson(r.breaks));
        return obj;
    }

    private static JSONArray entriesToJson(Iterable<TimeEntry> entries) throws JSONException {
        JSONArray array = new JSONArray();
        for (TimeEntry e : entries) {
            JSONObject o = new JSONObject();
            o.put("start", e.start);
            o.put("end", e.end);
            array.put(o);
        }
        return array;
    }

    static DayRecord fromJson(JSONObject obj) {
        DayRecord r = new DayRecord();
        r.state = obj.optString("state", DayRecord.STATE_STOPPED);
        r.paid = obj.optBoolean("paid", false);
        r.paidAt = r.paid ? obj.optLong("paidAt", 0L) : 0L;
        r.rate = obj.has("rate") ? obj.optDouble("rate", -1.0) : -1.0;
        r.note = obj.optString("note", "");
        readEntries(obj.optJSONArray("work"), r.work);
        readEntries(obj.optJSONArray("breaks"), r.breaks);
        return r;
    }

    private static void readEntries(JSONArray array, java.util.List<TimeEntry> target) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            long start = item.optLong("start", 0L);
            long end = item.optLong("end", 0L);
            if (start <= 0L || (end != 0L && end < start)) continue;
            target.add(new TimeEntry(start, end));
        }
    }

    static JSONObject daysToJson(Map<String, DayRecord> days) throws JSONException {
        JSONObject root = new JSONObject();
        for (Map.Entry<String, DayRecord> entry : days.entrySet()) {
            root.put(entry.getKey(), toJson(entry.getValue()));
        }
        return root;
    }

    /** Ergebnis des Einlesens eines Backups oder des gespeicherten Datenbestands. */
    static final class Parsed {
        final LinkedHashMap<String, DayRecord> days = new LinkedHashMap<String, DayRecord>();
        /** Standard-Stundenlohn aus dem Backup; negativ, falls nicht enthalten. */
        double hourlyWage = -1.0;
    }

    /** Liest sowohl das Backup-Format als auch das alte reine Tage-Objekt. */
    static Parsed parse(String raw) throws JSONException {
        JSONObject root = new JSONObject(raw);
        Parsed parsed = new Parsed();
        JSONObject daysObj = root;
        if (root.optJSONObject("days") != null && root.has("format")) {
            daysObj = root.getJSONObject("days");
            parsed.hourlyWage = root.optDouble("hourlyWage", -1.0);
        }
        Iterator<String> keys = daysObj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject dayObj = daysObj.optJSONObject(key);
            if (dayObj == null || !DayTime.isValidKey(key)) continue;
            parsed.days.put(key, fromJson(dayObj));
        }
        return parsed;
    }

    static String backupJson(Map<String, DayRecord> days, double hourlyWage) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "arbeitszeit");
        root.put("format", BACKUP_FORMAT);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("hourlyWage", hourlyWage);
        root.put("days", daysToJson(days));
        return root.toString(2);
    }
}
