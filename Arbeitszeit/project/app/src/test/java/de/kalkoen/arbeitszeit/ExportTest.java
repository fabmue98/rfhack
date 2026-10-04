package de.kalkoen.arbeitszeit;

import static de.kalkoen.arbeitszeit.TestTime.ts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedHashMap;

public class ExportTest {
    private static final long NOW = ts("2026-10-31 20:00");

    @BeforeClass
    public static void setUp() {
        TestTime.useBerlin();
    }

    private static DayRecord day(String date, String start, String end, double rate) {
        DayRecord r = new DayRecord();
        r.work.add(new TimeEntry(ts(date + " " + start), ts(date + " " + end)));
        r.rate = rate;
        return r;
    }

    @Test
    public void csvContainsGermanNumbersAndSum() {
        LinkedHashMap<String, DayRecord> days = new LinkedHashMap<String, DayRecord>();
        DayRecord a = day("2026-10-05", "08:00", "12:30", 12.5);
        a.breaks.add(new TimeEntry(ts("2026-10-05 10:00"), ts("2026-10-05 10:30")));
        a.markPaid(ts("2026-10-20 12:00"));
        days.put("2026-10-05", a);
        days.put("2026-10-06", day("2026-10-06", "09:00", "11:00", 12.5));
        days.put("2026-09-30", day("2026-09-30", "09:00", "11:00", 12.5)); // anderer Monat

        String csv = Export.csv(days, 2026, 9, 12.5, NOW);
        String[] lines = csv.split("\r\n");
        assertEquals(4, lines.length); // Kopf + 2 Tage + Summe
        assertTrue(lines[0].startsWith("Datum;Wochentag;Zeiten;"));
        assertEquals("05.10.2026;Montag;08:00-12:30;4:00;0:30;4,00;12,50;50,00;bezahlt;20.10.2026;", lines[1]);
        assertTrue(lines[2].startsWith("06.10.2026;Dienstag;09:00-11:00;2:00;0:00;2,00;12,50;25,00;offen;;"));
        assertEquals("Summe;;;6:00;;6,00;;75,00;;;", lines[3]);
        assertFalse(csv.contains("30.09.2026"));
    }

    @Test
    public void escapesSeparatorsQuotesAndFormulas() {
        assertEquals("\"a;b\"", Export.escape("a;b"));
        assertEquals("\"sag \"\"hallo\"\"\"", Export.escape("sag \"hallo\""));
        assertEquals("'=SUMME(A1)", Export.escape("=SUMME(A1)"));
        assertEquals("'-1", Export.escape("-1"));
        assertEquals("", Export.escape(null));
        assertEquals("normal", Export.escape("normal"));
    }

    @Test
    public void multipleBlocksAreListed() {
        LinkedHashMap<String, DayRecord> days = new LinkedHashMap<String, DayRecord>();
        DayRecord r = day("2026-10-05", "08:00", "12:00", 10);
        r.work.add(new TimeEntry(ts("2026-10-05 13:00"), ts("2026-10-05 17:00")));
        days.put("2026-10-05", r);
        assertTrue(Export.csv(days, 2026, 9, 10, NOW).contains("08:00-12:00 / 13:00-17:00"));
    }
}
