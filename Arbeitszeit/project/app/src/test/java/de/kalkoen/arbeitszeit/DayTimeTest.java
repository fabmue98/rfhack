package de.kalkoen.arbeitszeit;

import static de.kalkoen.arbeitszeit.TestTime.ts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

public class DayTimeTest {
    @BeforeClass
    public static void setUp() {
        TestTime.useBerlin();
    }

    @Test
    public void validatesDayKeysStrictly() {
        assertTrue(DayTime.isValidKey("2026-10-05"));
        assertFalse(DayTime.isValidKey("2026-02-30"));
        assertFalse(DayTime.isValidKey("2026-1-5"));
        assertFalse(DayTime.isValidKey("hallo"));
        assertFalse(DayTime.isValidKey(null));
    }

    @Test
    public void dayKeyRoundTrip() {
        long t = ts("2026-10-05 13:45");
        assertEquals("2026-10-05", DayTime.dayKey(t));
        assertEquals(ts("2026-10-05 00:00"), DayTime.millisForDayKey("2026-10-05"));
    }

    @Test
    public void weekStartsOnMonday() {
        long monday = ts("2026-10-05 00:00");
        assertEquals(monday, DayTime.startOfWeek(ts("2026-10-05 09:00")));
        assertEquals(monday, DayTime.startOfWeek(ts("2026-10-11 23:30")));   // Sonntag
        assertEquals(ts("2026-10-12 00:00"), DayTime.startOfWeek(ts("2026-10-12 00:00")));
    }

    @Test
    public void nextDayAcrossDstChangeIsMidnight() {
        // Sommerzeit beginnt am 2026-03-29: der Tag hat nur 23 Stunden.
        long next = DayTime.startOfNextDay(ts("2026-03-29 10:00"));
        assertEquals(ts("2026-03-30 00:00"), next);
        assertEquals(23L * 3600000L, next - ts("2026-03-29 00:00"));
    }

    @Test
    public void addDaysKeepsLocalTime() {
        assertEquals(ts("2026-03-30 06:00"), DayTime.addDays(ts("2026-03-29 06:00"), 1));
    }

    @Test
    public void monthHelpers() {
        assertEquals("2026-10-", DayTime.monthPrefix(2026, 9));
        assertEquals(ts("2026-10-01 00:00"), DayTime.monthStart(2026, 9));
        assertEquals(2026, DayTime.yearOf(ts("2026-10-05 12:00")));
        assertEquals(9, DayTime.monthOf(ts("2026-10-05 12:00")));
        assertEquals("Oktober 2026", DayTime.monthLabel(2026, 9));
    }

    @Test
    public void timeOnDay() {
        assertEquals(ts("2026-10-05 08:15"), DayTime.timeOnDay("2026-10-05", 8, 15));
    }
}
