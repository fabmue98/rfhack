package de.kalkoen.arbeitszeit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reine Rechenlogik für Arbeitsblöcke und Pausen. Keine Android-Abhängigkeiten,
 * damit sie per JUnit testbar ist.
 */
final class Intervals {
    private Intervals() {}

    static long endOf(TimeEntry e, long now) {
        return e.end == 0L ? now : e.end;
    }

    static long sum(List<TimeEntry> entries, long now) {
        long total = 0L;
        for (TimeEntry entry : entries) {
            long end = endOf(entry, now);
            if (end > entry.start) total += end - entry.start;
        }
        return Math.max(0L, total);
    }

    /** Pausenzeit, die tatsächlich innerhalb von Arbeitsblöcken liegt. */
    static long effectiveBreak(DayRecord record, long now) {
        long total = 0L;
        for (TimeEntry pause : record.breaks) {
            long pauseEnd = endOf(pause, now);
            if (pauseEnd <= pause.start) continue;
            for (TimeEntry work : record.work) {
                long start = Math.max(pause.start, work.start);
                long end = Math.min(pauseEnd, endOf(work, now));
                if (end > start) total += end - start;
            }
        }
        return Math.max(0L, total);
    }

    static long net(DayRecord record, long now) {
        return Math.max(0L, sum(record.work, now) - effectiveBreak(record, now));
    }

    /** Lohn auf Cent gerundet. */
    static double wage(DayRecord record, double fallbackRate, long now) {
        double rate = record.rate < 0.0 ? fallbackRate : record.rate;
        double raw = (net(record, now) / 3600000.0) * rate;
        return Math.round(raw * 100.0) / 100.0;
    }

    static boolean hasOverlap(List<TimeEntry> entries, long start, long end, TimeEntry ignored, long now) {
        for (TimeEntry e : entries) {
            if (e == ignored) continue;
            if (start < endOf(e, now) && end > e.start) return true;
        }
        return false;
    }

    /** Liegt das Intervall komplett innerhalb zusammenhängender Arbeitszeit? */
    static boolean isCoveredByWork(DayRecord record, long start, long end, long now) {
        if (end <= start) return false;
        ArrayList<TimeEntry> sorted = new ArrayList<TimeEntry>(record.work);
        sort(sorted);
        long coveredUntil = start;
        for (TimeEntry work : sorted) {
            long workEnd = endOf(work, now);
            if (workEnd <= coveredUntil) continue;
            if (work.start > coveredUntil) break;
            coveredUntil = Math.max(coveredUntil, workEnd);
            if (coveredUntil >= end) return true;
        }
        return false;
    }

    /** Schneidet Pausen auf die vorhandene Arbeitszeit zu und entfernt überstehende Teile. */
    static void cleanupBreaksToWork(DayRecord record, long now) {
        ArrayList<TimeEntry> cleaned = new ArrayList<TimeEntry>();
        boolean hadActiveBreak = findActive(record.breaks) != null
                && DayRecord.STATE_PAUSED.equals(record.state);

        for (TimeEntry pause : record.breaks) {
            long pauseEnd = endOf(pause, now);
            for (TimeEntry work : record.work) {
                long workEnd = endOf(work, now);
                long start = Math.max(pause.start, work.start);
                long end = Math.min(pauseEnd, workEnd);
                if (end > start) {
                    boolean keepActive = hadActiveBreak && pause.end == 0L && work.end == 0L && end == now;
                    cleaned.add(new TimeEntry(start, keepActive ? 0L : end));
                }
            }
        }

        sort(cleaned);
        cleaned = mergeBreaks(cleaned, now);
        record.breaks.clear();
        record.breaks.addAll(cleaned);
        sanitizeState(record, now);
    }

    static ArrayList<TimeEntry> mergeBreaks(List<TimeEntry> source, long now) {
        ArrayList<TimeEntry> result = new ArrayList<TimeEntry>();
        for (TimeEntry current : source) {
            if (result.isEmpty()) {
                result.add(current);
                continue;
            }
            TimeEntry last = result.get(result.size() - 1);
            long lastEnd = endOf(last, now);
            long curEnd = endOf(current, now);
            if (current.start <= lastEnd) {
                boolean active = last.end == 0L || current.end == 0L;
                last.end = active ? 0L : Math.max(lastEnd, curEnd);
            } else {
                result.add(current);
            }
        }
        return result;
    }

    /**
     * Leitet den Tageszustand aus den offenen Einträgen ab: offener Arbeitsblock =
     * läuft, zusätzlich offene Pause = Pause. Verwaiste offene Pausen werden geschlossen.
     */
    static void sanitizeState(DayRecord record, long now) {
        TimeEntry activeWork = findActive(record.work);
        TimeEntry activeBreak = findActive(record.breaks);
        if (activeWork != null) {
            record.state = activeBreak != null ? DayRecord.STATE_PAUSED : DayRecord.STATE_WORKING;
        } else {
            if (activeBreak != null) activeBreak.end = Math.max(now, activeBreak.start);
            record.state = DayRecord.STATE_STOPPED;
        }
    }

    static void sortEntriesOf(DayRecord record) {
        sort(record.work);
        sort(record.breaks);
    }

    static void sort(List<TimeEntry> list) {
        Collections.sort(list, new Comparator<TimeEntry>() {
            @Override
            public int compare(TimeEntry a, TimeEntry b) {
                return Long.compare(a.start, b.start);
            }
        });
    }

    static TimeEntry findActive(List<TimeEntry> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).end == 0L) return list.get(i);
        }
        return null;
    }

    static void closeActive(List<TimeEntry> list, long end) {
        TimeEntry active = findActive(list);
        if (active != null) active.end = Math.max(end, active.start);
    }
}
