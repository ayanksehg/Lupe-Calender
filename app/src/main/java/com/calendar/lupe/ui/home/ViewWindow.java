package com.calendar.lupe.ui.home;

import java.util.Calendar;

public enum ViewWindow {
    EVENT, DAY, WEEK, MONTH;

    public static ViewWindow fromString(String s) {
        if (s == null) return DAY;
        switch (s) {
            case "EVENT": return EVENT;
            case "WEEK":  return WEEK;
            case "MONTH": return MONTH;
            case "DAY":   return DAY;
            default:      return DAY;
        }
    }


    public long windowEnd(long now) {
        if (this == EVENT) {
            return Long.MAX_VALUE;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        switch (this) {
            case WEEK:
                c.setFirstDayOfWeek(Calendar.SUNDAY);
                c.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
                break;
            case MONTH:
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                break;
            case DAY:
            default:
                break;
        }
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }
}
