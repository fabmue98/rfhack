package de.kalkoen.arbeitszeit;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Gemeinsame Hilfen für die Tests: feste Zeitzone und lesbare Zeitstempel. */
final class TestTime {
    private TestTime() {}

    static void useBerlin() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        Locale.setDefault(Locale.GERMANY);
    }

    /** Format "yyyy-MM-dd HH:mm" in Europe/Berlin. */
    static long ts(String text) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
            return f.parse(text).getTime();
        } catch (Exception ex) {
            throw new IllegalArgumentException(text, ex);
        }
    }

    static final long MIN = 60000L;
    static final long HOUR = 60L * MIN;
}
