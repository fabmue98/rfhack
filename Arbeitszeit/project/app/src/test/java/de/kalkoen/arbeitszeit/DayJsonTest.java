package de.kalkoen.arbeitszeit;

import static de.kalkoen.arbeitszeit.TestTime.ts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedHashMap;

public class DayJsonTest {
    @BeforeClass
    public static void setUp() {
        TestTime.useBerlin();
    }

    @Test
    public void backupRoundTrip() throws JSONException {
        DayRecord r = new DayRecord();
        r.work.add(new TimeEntry(ts("2026-10-05 08:00"), ts("2026-10-05 16:00")));
        r.breaks.add(new TimeEntry(ts("2026-10-05 12:00"), ts("2026-10-05 12:30")));
        r.rate = 13.37;
        r.note = "Kunde \"Müller\"; Baustelle";
        r.markPaid(ts("2026-10-20 10:00"));
        LinkedHashMap<String, DayRecord> days = new LinkedHashMap<String, DayRecord>();
        days.put("2026-10-05", r);

        DayJson.Parsed parsed = DayJson.parse(DayJson.backupJson(days, 14.5));
        assertEquals(14.5, parsed.hourlyWage, 0.0);
        assertEquals(1, parsed.days.size());
        DayRecord back = parsed.days.get("2026-10-05");
        assertEquals(1, back.work.size());
        assertEquals(1, back.breaks.size());
        assertEquals(13.37, back.rate, 0.0);
        assertEquals(r.note, back.note);
        assertTrue(back.paid);
        assertEquals(ts("2026-10-20 10:00"), back.paidAt);
    }

    @Test
    public void readsLegacyDaysObjectWithoutNewFields() throws JSONException {
        String legacy = "{\"2026-10-05\":{\"state\":\"STOPPED\",\"paid\":false,\"rate\":12.0,"
                + "\"work\":[{\"start\":1000,\"end\":5000}],\"breaks\":[]}}";
        DayJson.Parsed parsed = DayJson.parse(legacy);
        assertEquals(-1.0, parsed.hourlyWage, 0.0);
        DayRecord r = parsed.days.get("2026-10-05");
        assertEquals(1, r.work.size());
        assertEquals("", r.note);
        assertEquals(0L, r.paidAt);
    }

    @Test
    public void skipsInvalidKeysAndBrokenEntries() throws JSONException {
        String raw = "{\"kaputt\":{\"work\":[]},\"2026-02-30\":{\"work\":[]},"
                + "\"2026-10-05\":{\"work\":[{\"start\":0,\"end\":5},{\"start\":9,\"end\":3},{\"start\":10,\"end\":20}]}}";
        DayJson.Parsed parsed = DayJson.parse(raw);
        assertEquals(1, parsed.days.size());
        assertEquals(1, parsed.days.get("2026-10-05").work.size());
        assertFalse(parsed.days.containsKey("kaputt"));
    }

    @Test(expected = JSONException.class)
    public void rejectsNonJson() throws JSONException {
        DayJson.parse("das ist kein json");
    }

    @Test
    public void paidAtIgnoredWhenNotPaid() throws JSONException {
        DayJson.Parsed parsed = DayJson.parse(
                "{\"2026-10-05\":{\"paid\":false,\"paidAt\":123,\"work\":[{\"start\":5,\"end\":9}]}}");
        assertEquals(0L, parsed.days.get("2026-10-05").paidAt);
    }
}
