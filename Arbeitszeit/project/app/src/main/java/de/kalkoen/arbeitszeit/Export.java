package de.kalkoen.arbeitszeit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/** CSV-Export (Semikolon-getrennt, deutsches Zahlenformat, für Excel/LibreOffice). */
final class Export {
    private Export() {}

    private static final String SEP = ";";
    private static final String NL = "\r\n";

    static String csv(Map<String, DayRecord> days, int year, int month, double fallbackRate, long now) {
        String prefix = DayTime.monthPrefix(year, month);
        ArrayList<String> keys = new ArrayList<String>();
        for (Map.Entry<String, DayRecord> e : days.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue().hasMeaningfulData()) keys.add(e.getKey());
        }
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        row(sb, "Datum", "Wochentag", "Zeiten", "Arbeit (h:mm)", "Pause (h:mm)",
                "Netto (Dezimalstunden)", "Stundenlohn (EUR)", "Lohn (EUR)", "Status", "Bezahlt am", "Notiz");

        long totalMs = 0L;
        double totalWage = 0.0;
        for (String key : keys) {
            DayRecord r = days.get(key);
            long netMs = Intervals.net(r, now);
            double wage = Intervals.wage(r, fallbackRate, now);
            double rate = r.rate < 0.0 ? fallbackRate : r.rate;
            totalMs += netMs;
            totalWage += wage;
            long dayStart = DayTime.millisForDayKey(key);
            row(sb,
                    Fmt.date("dd.MM.yyyy", Fmt.DE, dayStart),
                    Fmt.date("EEEE", Fmt.DE, dayStart),
                    spans(r, now),
                    Fmt.hoursMinutes(netMs),
                    Fmt.hoursMinutes(Intervals.effectiveBreak(r, now)),
                    Fmt.decimal(netMs / 3600000.0),
                    Fmt.decimal(rate),
                    Fmt.decimal(wage),
                    r.paid ? "bezahlt" : "offen",
                    r.paid && r.paidAt > 0L ? Fmt.date("dd.MM.yyyy", Fmt.DE, r.paidAt) : "",
                    r.note);
        }
        row(sb, "Summe", "", "", Fmt.hoursMinutes(totalMs), "", Fmt.decimal(totalMs / 3600000.0), "",
                Fmt.decimal(totalWage), "", "", "");
        return sb.toString();
    }

    private static String spans(DayRecord r, long now) {
        ArrayList<TimeEntry> sorted = new ArrayList<TimeEntry>(r.work);
        Intervals.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (TimeEntry e : sorted) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(Fmt.time(e.start)).append("-").append(e.end == 0L ? "läuft" : Fmt.time(e.end));
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(SEP);
            sb.append(escape(cells[i]));
        }
        sb.append(NL);
    }

    /** Maskiert Trennzeichen/Anführungszeichen und neutralisiert Formel-Präfixe (CSV-Injection). */
    static String escape(String value) {
        if (value == null) return "";
        String v = value;
        if (v.length() > 0) {
            char c = v.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') v = "'" + v;
        }
        if (v.indexOf(';') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
