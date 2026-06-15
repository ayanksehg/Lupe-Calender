package com.example.myapplication.ui.home;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Builds the windowed, recurrence-expanded list shown on Home. Pure: no Firestore/LiveData. */
public final class DisplayedListBuilder {

    private static final String DT = "MM/dd/yyyy HH:mm";
    private static final String D = "MM/dd/yyyy";

    private DisplayedListBuilder() {}

    private static List<Event> filterFood(List<Event> in, boolean foodOnly) {
        if (!foodOnly) return in;
        List<Event> out = new ArrayList<>();
        for (Event e : in) {
            if (EventType.fromString(e.type).showsInFoodCalendar()) out.add(e);
        }
        return out;
    }

    private static long parse(String date, String time) {
        String t = ("All Day".equals(time) || time == null) ? "00:00" : time;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DT, Locale.getDefault());
            Date d = sdf.parse(date + " " + t);
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String millisToDate(long millis) {
        return new SimpleDateFormat(D, Locale.getDefault()).format(new Date(millis));
    }

    /** Display-only copy of a series at a specific occurrence date (keeps series id). */
    private static Event occurrenceCopy(Event series, String occurrenceDate) {
        Event e = new Event(series.title, series.description, occurrenceDate,
                series.time, series.location, series.circleCode);
        e.id = series.id;
        e.recurrence = series.recurrence;
        e.recurrenceEndDate = series.recurrenceEndDate;
        e.type = series.type;
        return e;
    }

    /** True if a Google event (possibly "All Day") falls within (now, windowEnd]. */
    private static boolean googleInWindow(Event g, long now, long windowEnd) {
        if ("All Day".equals(g.time)) {
            // include while the day has not fully passed and it starts on/before the window end
            long startOfDay = parse(g.date, "00:00");
            if (startOfDay < 0) return false;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(startOfDay);
            c.set(Calendar.HOUR_OF_DAY, 23);
            c.set(Calendar.MINUTE, 59);
            c.set(Calendar.SECOND, 59);
            long endOfDay = c.getTimeInMillis();
            return endOfDay > now && startOfDay <= windowEnd;
        }
        long t = parse(g.date, g.time);
        return t > now && t <= windowEnd;
    }

    public static List<Event> build(List<Event> rawEvents, List<Event> googleEvents,
                                    ViewWindow window, long now) {
        return build(rawEvents, googleEvents, window, now, false);
    }

    public static List<Event> build(List<Event> rawEventsIn, List<Event> googleEventsIn,
                                    ViewWindow window, long now, boolean foodOnly) {
        List<Event> rawEvents = filterFood(rawEventsIn, foodOnly);
        List<Event> googleEvents = filterFood(googleEventsIn, foodOnly);
        List<Event> result = new ArrayList<>();
        long windowEnd = window.windowEnd(now);

        if (window == ViewWindow.EVENT) {
            Event best = null;
            long bestT = Long.MAX_VALUE;
            for (Event e : rawEvents) {
                long t = RecurrenceExpander.nextOccurrenceAfter(e.date, e.time, e.recurrence,
                        e.recurrenceEndDate, now);
                if (t > 0 && t < bestT) {
                    bestT = t;
                    best = occurrenceCopy(e, millisToDate(t));
                }
            }
            for (Event g : googleEvents) {
                long t = parse(g.date, g.time);
                if (t > now && t < bestT) {
                    bestT = t;
                    best = g;
                }
            }
            if (best != null) result.add(best);
            return result;
        }

        for (Event e : rawEvents) {
            for (String date : RecurrenceExpander.occurrenceDatesInWindow(e.date, e.time,
                    e.recurrence, e.recurrenceEndDate, now, windowEnd)) {
                result.add(occurrenceCopy(e, date));
            }
        }
        for (Event g : googleEvents) {
            if (googleInWindow(g, now, windowEnd)) result.add(g);
        }

        Collections.sort(result, Comparator.comparingLong(ev -> {
            long t = parse(ev.date, ev.time);
            return t < 0 ? Long.MAX_VALUE : t;
        }));
        return result;
    }
}
