package de.kalkoen.arbeitszeit;

import static de.kalkoen.arbeitszeit.TestTime.HOUR;
import static de.kalkoen.arbeitszeit.TestTime.MIN;
import static de.kalkoen.arbeitszeit.TestTime.ts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;

public class IntervalsTest {
    private static final long NOW = ts("2026-10-05 18:00");

    @BeforeClass
    public static void setUp() {
        TestTime.useBerlin();
    }

    private static DayRecord day(String workStart, String workEnd) {
        DayRecord r = new DayRecord();
        r.work.add(new TimeEntry(ts("2026-10-05 " + workStart), ts("2026-10-05 " + workEnd)));
        return r;
    }

    private static void addBreak(DayRecord r, String start, String end) {
        r.breaks.add(new TimeEntry(ts("2026-10-05 " + start), ts("2026-10-05 " + end)));
    }

    @Test
    public void breakInsideWorkIsSubtracted() {
        DayRecord r = day("08:00", "16:00");
        addBreak(r, "12:00", "12:30");
        assertEquals(30 * MIN, Intervals.effectiveBreak(r, NOW));
        assertEquals(7 * HOUR + 30 * MIN, Intervals.net(r, NOW));
    }

    @Test
    public void breakOutsideWorkIsIgnored() {
        DayRecord r = day("08:00", "12:00");
        addBreak(r, "13:00", "13:30");
        assertEquals(0L, Intervals.effectiveBreak(r, NOW));
        assertEquals(4 * HOUR, Intervals.net(r, NOW));
    }

    @Test
    public void breakPartlyOutsideOnlyCountsOverlap() {
        DayRecord r = day("08:00", "12:00");
        addBreak(r, "11:30", "12:30");
        assertEquals(30 * MIN, Intervals.effectiveBreak(r, NOW));
    }

    @Test
    public void openBlockRunsUntilNow() {
        DayRecord r = new DayRecord();
        r.work.add(new TimeEntry(ts("2026-10-05 16:00"), 0L));
        assertEquals(2 * HOUR, Intervals.net(r, NOW));
    }

    @Test
    public void wageIsRoundedToCents() {
        DayRecord r = day("08:00", "09:20");
        r.rate = 12.34;
        // 1h20m * 12,34 = 16,4533...
        assertEquals(16.45, Intervals.wage(r, 0.0, NOW), 0.0);
    }

    @Test
    public void wageUsesFallbackRateWhenDayHasNone() {
        DayRecord r = day("08:00", "10:00");
        assertEquals(30.0, Intervals.wage(r, 15.0, NOW), 0.0);
    }

    @Test
    public void cleanupTrimsBreakToShortenedWork() {
        DayRecord r = day("08:00", "12:00");
        addBreak(r, "11:30", "12:30");
        Intervals.cleanupBreaksToWork(r, NOW);
        assertEquals(1, r.breaks.size());
        assertEquals(ts("2026-10-05 11:30"), r.breaks.get(0).start);
        assertEquals(ts("2026-10-05 12:00"), r.breaks.get(0).end);
    }

    @Test
    public void cleanupRemovesBreakOutsideWork() {
        DayRecord r = day("08:00", "12:00");
        addBreak(r, "13:00", "13:30");
        Intervals.cleanupBreaksToWork(r, NOW);
        assertTrue(r.breaks.isEmpty());
    }

    @Test
    public void cleanupSplitsBreakAcrossTwoBlocks() {
        DayRecord r = day("08:00", "12:00");
        r.work.add(new TimeEntry(ts("2026-10-05 13:00"), ts("2026-10-05 17:00")));
        addBreak(r, "11:30", "13:30");
        Intervals.cleanupBreaksToWork(r, NOW);
        assertEquals(2, r.breaks.size());
        assertEquals(60 * MIN, Intervals.effectiveBreak(r, NOW));
    }

    @Test
    public void coveredByAdjacentBlocks() {
        DayRecord r = day("08:00", "12:00");
        r.work.add(new TimeEntry(ts("2026-10-05 12:00"), ts("2026-10-05 16:00")));
        assertTrue(Intervals.isCoveredByWork(r, ts("2026-10-05 11:30"), ts("2026-10-05 12:30"), NOW));
    }

    @Test
    public void notCoveredAcrossGap() {
        DayRecord r = day("08:00", "12:00");
        r.work.add(new TimeEntry(ts("2026-10-05 13:00"), ts("2026-10-05 16:00")));
        assertFalse(Intervals.isCoveredByWork(r, ts("2026-10-05 11:30"), ts("2026-10-05 13:30"), NOW));
    }

    @Test
    public void overlapDetectionIgnoresTheEditedEntry() {
        DayRecord r = day("08:00", "12:00");
        TimeEntry other = new TimeEntry(ts("2026-10-05 13:00"), ts("2026-10-05 15:00"));
        r.work.add(other);
        long s = ts("2026-10-05 11:00");
        long e = ts("2026-10-05 14:00");
        assertTrue(Intervals.hasOverlap(r.work, s, e, null, NOW));
        assertTrue(Intervals.hasOverlap(r.work, s, e, other, NOW));      // trifft noch den ersten Block
        assertFalse(Intervals.hasOverlap(r.work, ts("2026-10-05 12:00"), ts("2026-10-05 13:00"), null, NOW));
    }

    @Test
    public void touchingIntervalsDoNotOverlap() {
        DayRecord r = day("08:00", "12:00");
        assertFalse(Intervals.hasOverlap(r.work, ts("2026-10-05 12:00"), ts("2026-10-05 13:00"), null, NOW));
    }

    @Test
    public void activeEntryOverlapUsesNow() {
        DayRecord r = new DayRecord();
        r.work.add(new TimeEntry(ts("2026-10-05 16:00"), 0L));
        assertTrue(Intervals.hasOverlap(r.work, ts("2026-10-05 17:00"), ts("2026-10-05 17:30"), null, NOW));
        assertFalse(Intervals.hasOverlap(r.work, ts("2026-10-05 18:30"), ts("2026-10-05 19:00"), null, NOW));
    }

    @Test
    public void sanitizeDerivesStateFromOpenEntries() {
        DayRecord r = new DayRecord();
        r.work.add(new TimeEntry(ts("2026-10-05 16:00"), 0L));
        r.state = DayRecord.STATE_STOPPED;
        Intervals.sanitizeState(r, NOW);
        assertEquals(DayRecord.STATE_WORKING, r.state);

        r.breaks.add(new TimeEntry(ts("2026-10-05 17:00"), 0L));
        Intervals.sanitizeState(r, NOW);
        assertEquals(DayRecord.STATE_PAUSED, r.state);
    }

    @Test
    public void sanitizeClosesOrphanOpenBreak() {
        DayRecord r = day("08:00", "12:00");
        r.breaks.add(new TimeEntry(ts("2026-10-05 11:00"), 0L));
        r.state = DayRecord.STATE_PAUSED;
        Intervals.sanitizeState(r, NOW);
        assertEquals(DayRecord.STATE_STOPPED, r.state);
        assertNull(Intervals.findActive(r.breaks));
    }

    @Test
    public void cleanupKeepsRunningPauseActive() {
        DayRecord r = new DayRecord();
        r.work.add(new TimeEntry(ts("2026-10-05 08:00"), 0L));
        r.breaks.add(new TimeEntry(ts("2026-10-05 17:00"), 0L));
        r.state = DayRecord.STATE_PAUSED;
        Intervals.cleanupBreaksToWork(r, NOW);
        assertEquals(DayRecord.STATE_PAUSED, r.state);
        assertNotNull(Intervals.findActive(r.breaks));
    }

    @Test
    public void mergeBreaksJoinsOverlapping() {
        ArrayList<TimeEntry> list = new ArrayList<TimeEntry>();
        list.add(new TimeEntry(ts("2026-10-05 12:00"), ts("2026-10-05 12:20")));
        list.add(new TimeEntry(ts("2026-10-05 12:10"), ts("2026-10-05 12:40")));
        list.add(new TimeEntry(ts("2026-10-05 15:00"), ts("2026-10-05 15:10")));
        ArrayList<TimeEntry> merged = Intervals.mergeBreaks(list, NOW);
        assertEquals(2, merged.size());
        assertEquals(ts("2026-10-05 12:40"), merged.get(0).end);
    }

    @Test
    public void closeActiveNeverEndsBeforeStart() {
        ArrayList<TimeEntry> list = new ArrayList<TimeEntry>();
        list.add(new TimeEntry(ts("2026-10-05 12:00"), 0L));
        Intervals.closeActive(list, ts("2026-10-05 11:00"));
        assertEquals(ts("2026-10-05 12:00"), list.get(0).end);
    }
}
