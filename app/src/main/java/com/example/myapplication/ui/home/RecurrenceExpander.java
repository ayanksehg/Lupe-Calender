package com.example.myapplication.ui.home;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure occurrence date math for recurring events. No Android/Firestore dependencies. */
public final class RecurrenceExpander {

    private static final String DT = "MM/dd/yyyy HH:mm";
    private static final String D = "MM/dd/yyyy";
    private static final int GUARD = 100000;

    private RecurrenceExpander() {}

    private static boolean isNone(String recurrence) {
        return recurrence == null || recurrence.isEmpty() || "NONE".equals(recurrence);
    }

    private static long parse(String date, String time) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DT, Locale.getDefault());
            Date d = sdf.parse(date + " " + time);
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    private static Calendar toCalendar(String date, String time) {
        long t = parse(date, time);
        if (t < 0) return null;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c;
    }

    /** Inclusive end-of-day cutoff for the recurrence end date, or Long.MAX_VALUE if none. */
    private static long endLimitMillis(String endDate) {
        if (endDate == null || endDate.trim().isEmpty()) return Long.MAX_VALUE;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(D, Locale.getDefault());
            Date d = sdf.parse(endDate.trim());
            if (d == null) return Long.MAX_VALUE;
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            c.set(Calendar.HOUR_OF_DAY, 23);
            c.set(Calendar.MINUTE, 59);
            c.set(Calendar.SECOND, 59);
            c.set(Calendar.MILLISECOND, 999);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private static void step(Calendar c, String recurrence) {
        switch (recurrence) {
            case "EVERY_OTHER_DAY": c.add(Calendar.DAY_OF_MONTH, 2); break;
            case "WEEKLY":  c.add(Calendar.DAY_OF_MONTH, 7); break;
            case "MONTHLY": c.add(Calendar.MONTH, 1); break;
            case "YEARLY":  c.add(Calendar.YEAR, 1); break;
            case "DAILY":
            default:        c.add(Calendar.DAY_OF_MONTH, 1); break;
        }
    }

    private static boolean isExcluded(Set<String> excluded, String date) {
        return excluded != null && excluded.contains(date);
    }

    /** Next occurrence strictly after {@code afterMillis}, or -1 if past the end date / unparseable. */
    public static long nextOccurrenceAfter(String baseDate, String time, String recurrence,
                                           String endDate, long afterMillis) {
        return nextOccurrenceAfter(baseDate, time, recurrence, endDate, afterMillis, null);
    }

    /** As above, skipping any occurrence whose "MM/dd/yyyy" date is in {@code excluded}. */
    public static long nextOccurrenceAfter(String baseDate, String time, String recurrence,
                                           String endDate, long afterMillis, Set<String> excluded) {
        if (isNone(recurrence)) {
            long t = parse(baseDate, time);
            if (t <= afterMillis || isExcluded(excluded, baseDate)) return -1;
            return t;
        }
        Calendar c = toCalendar(baseDate, time);
        if (c == null) return -1;
        long endLimit = endLimitMillis(endDate);
        SimpleDateFormat d = new SimpleDateFormat(D, Locale.getDefault());
        int guard = 0;
        while (c.getTimeInMillis() <= afterMillis || isExcluded(excluded, d.format(c.getTime()))) {
            step(c, recurrence);
            if (++guard > GUARD || c.getTimeInMillis() > endLimit) return -1;
        }
        return (c.getTimeInMillis() > endLimit) ? -1 : c.getTimeInMillis();
    }

    /** "MM/dd/yyyy" dates of occurrences in (now, min(windowEnd, endDate)]. */
    public static List<String> occurrenceDatesInWindow(String baseDate, String time, String recurrence,
                                                       String endDate, long now, long windowEnd) {
        return occurrenceDatesInWindow(baseDate, time, recurrence, endDate, now, windowEnd, null);
    }

    /** As above, omitting any occurrence whose "MM/dd/yyyy" date is in {@code excluded}. */
    public static List<String> occurrenceDatesInWindow(String baseDate, String time, String recurrence,
                                                       String endDate, long now, long windowEnd,
                                                       Set<String> excluded) {
        List<String> out = new ArrayList<>();
        if (isNone(recurrence)) {
            long t = parse(baseDate, time);
            if (t > now && t <= windowEnd && !isExcluded(excluded, baseDate)) out.add(baseDate);
            return out;
        }
        Calendar c = toCalendar(baseDate, time);
        if (c == null) return out;
        long cap = Math.min(windowEnd, endLimitMillis(endDate));
        SimpleDateFormat d = new SimpleDateFormat(D, Locale.getDefault());
        int guard = 0;
        while (c.getTimeInMillis() <= now) {
            step(c, recurrence);
            if (++guard > GUARD) return out;
        }
        while (c.getTimeInMillis() <= cap) {
            String date = d.format(c.getTime());
            if (!isExcluded(excluded, date)) out.add(date);
            step(c, recurrence);
            if (++guard > GUARD) break;
        }
        return out;
    }

    /** The day before {@code date} ("MM/dd/yyyy"), or null if unparseable. */
    public static String dayBefore(String date) {
        if (date == null) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(D, Locale.getDefault());
            Date d = sdf.parse(date.trim());
            if (d == null) return null;
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            c.add(Calendar.DAY_OF_MONTH, -1);
            return sdf.format(c.getTime());
        } catch (Exception e) {
            return null;
        }
    }
}
