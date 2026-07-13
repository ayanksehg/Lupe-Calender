package com.calendar.lupe;


public final class TimeFormat {

    private TimeFormat() {}

    /** "HH:mm" -> "h[:mm] AM/PM" (drops ":00" on the hour). "All Day"/null/unknown pass through. */
    public static String format(String time) {
        if (time == null || time.isEmpty() || "All Day".equals(time)) {
            return time == null ? "" : time;
        }
        String t = time.trim();
        int colon = t.indexOf(':');
        if (colon <= 0) return time;
        try {
            int h = Integer.parseInt(t.substring(0, colon));
            String min = t.substring(colon + 1);
            if (min.length() != 2) return time;
            String period = h >= 12 ? "PM" : "AM";
            int h12 = h % 12;
            if (h12 == 0) h12 = 12;
            return "00".equals(min) ? h12 + " " + period : h12 + ":" + min + " " + period;
        } catch (NumberFormatException e) {
            return time;
        }
    }
}
