package de.kalkoen.arbeitszeit;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Gecachte Formatter. SimpleDateFormat ist teuer und nicht threadsicher, daher
 * laufen alle Zugriffe synchronisiert über diese Klasse. Ändert sich die
 * Zeitzone des Geräts, wird der Formatter neu angelegt.
 */
final class Fmt {
    static final Locale DE = Locale.GERMANY;

    private static final Map<String, SimpleDateFormat> CACHE = new HashMap<String, SimpleDateFormat>();
    private static NumberFormat currency;

    private Fmt() {}

    private static SimpleDateFormat df(String pattern, Locale locale) {
        String cacheKey = pattern + "|" + locale;
        SimpleDateFormat f = CACHE.get(cacheKey);
        String zone = TimeZone.getDefault().getID();
        if (f == null || !f.getTimeZone().getID().equals(zone)) {
            f = new SimpleDateFormat(pattern, locale);
            f.setLenient(false);
            CACHE.put(cacheKey, f);
        }
        return f;
    }

    static synchronized String date(String pattern, Locale locale, long millis) {
        return df(pattern, locale).format(new Date(millis));
    }

    static synchronized long parseDate(String pattern, Locale locale, String text) throws ParseException {
        return df(pattern, locale).parse(text).getTime();
    }

    static synchronized String currency(double value) {
        if (currency == null) currency = NumberFormat.getCurrencyInstance(DE);
        return currency.format(value);
    }

    static String time(long millis) {
        return date("HH:mm", DE, millis);
    }

    /** hh:mm:ss */
    static String duration(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(DE, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** h:mm h */
    static String shortDuration(long ms) {
        return hoursMinutes(ms) + " h";
    }

    /** h:mm */
    static String hoursMinutes(long ms) {
        long totalMinutes = Math.max(0L, ms / 60000L);
        return String.format(DE, "%d:%02d", totalMinutes / 60L, totalMinutes % 60L);
    }

    static String decimal(double value) {
        return String.format(DE, "%.2f", value);
    }
}
