package de.kalkoen.arbeitszeit;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Locale;

/** Datums- und Tagesschlüssel-Hilfen (Tagesschlüssel = yyyy-MM-dd in lokaler Zeit). */
final class DayTime {
    private DayTime() {}

    static boolean isValidKey(String key) {
        if (key == null || !key.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        return millisForDayKey(key) > 0L;
    }

    static String dayKey(long millis) {
        return Fmt.date("yyyy-MM-dd", Locale.US, millis);
    }

    /** Beginn des Tages (00:00) oder 0, falls der Schlüssel ungültig ist. */
    static long millisForDayKey(String key) {
        try {
            return Fmt.parseDate("yyyy-MM-dd", Locale.US, key);
        } catch (ParseException ex) {
            return 0L;
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    static Calendar calendarFor(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return cal;
    }

    static long timeOnDay(String key, int hour, int minute) {
        Calendar cal = calendarFor(millisForDayKey(key));
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    static long startOfDay(long millis) {
        Calendar cal = calendarFor(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    static long startOfNextDay(long millis) {
        return addDays(startOfDay(millis), 1);
    }

    static long addDays(long millis, int days) {
        Calendar cal = calendarFor(millis);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTimeInMillis();
    }

    /** Montag 00:00 der Woche, in der millis liegt. */
    static long startOfWeek(long millis) {
        Calendar cal = Calendar.getInstance(Locale.GERMANY);
        cal.setTimeInMillis(startOfDay(millis));
        int daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        return cal.getTimeInMillis();
    }

    static int yearOf(long millis) {
        return calendarFor(millis).get(Calendar.YEAR);
    }

    /** Monat 0-11 wie bei Calendar. */
    static int monthOf(long millis) {
        return calendarFor(millis).get(Calendar.MONTH);
    }

    /** Präfix "yyyy-MM-" für alle Tagesschlüssel eines Monats. */
    static String monthPrefix(int year, int month) {
        return String.format(Locale.US, "%04d-%02d-", year, month + 1);
    }

    static long monthStart(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, 1, 0, 0, 0);
        return cal.getTimeInMillis();
    }

    static String monthLabel(int year, int month) {
        return Fmt.date("LLLL yyyy", Fmt.DE, monthStart(year, month));
    }
}
