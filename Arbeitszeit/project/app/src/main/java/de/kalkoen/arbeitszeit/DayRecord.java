package de.kalkoen.arbeitszeit;

import java.util.ArrayList;

/** Alle Daten eines Arbeitstags. */
final class DayRecord {
    static final String STATE_STOPPED = "STOPPED";
    static final String STATE_WORKING = "WORKING";
    static final String STATE_PAUSED = "PAUSED";

    String state = STATE_STOPPED;
    final ArrayList<TimeEntry> work = new ArrayList<TimeEntry>();
    final ArrayList<TimeEntry> breaks = new ArrayList<TimeEntry>();
    boolean paid = false;
    long paidAt = 0L;
    /** Stundenlohn an diesem Tag; negativ = noch nicht festgelegt. */
    double rate = -1.0;
    String note = "";

    boolean isRunning() {
        return STATE_WORKING.equals(state) || STATE_PAUSED.equals(state);
    }

    boolean hasMeaningfulData() {
        return !work.isEmpty() || !breaks.isEmpty();
    }

    void markUnpaid() {
        paid = false;
        paidAt = 0L;
    }

    void markPaid(long when) {
        paid = true;
        paidAt = when;
    }

    DayRecord copy() {
        DayRecord c = new DayRecord();
        c.state = state;
        c.paid = paid;
        c.paidAt = paidAt;
        c.rate = rate;
        c.note = note;
        for (TimeEntry e : work) c.work.add(e.copy());
        for (TimeEntry e : breaks) c.breaks.add(e.copy());
        return c;
    }
}
