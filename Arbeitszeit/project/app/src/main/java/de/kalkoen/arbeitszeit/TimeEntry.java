package de.kalkoen.arbeitszeit;

/** Ein Zeitintervall in Millisekunden. end == 0 bedeutet: läuft noch. */
final class TimeEntry {
    long start;
    long end;

    TimeEntry(long start, long end) {
        this.start = start;
        this.end = end;
    }

    TimeEntry copy() {
        return new TimeEntry(start, end);
    }
}
