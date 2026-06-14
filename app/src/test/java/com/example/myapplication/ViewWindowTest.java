package com.example.myapplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.myapplication.ui.home.ViewWindow;

import org.junit.Test;

import java.util.Calendar;

public class ViewWindowTest {

    private long millis(int year, int month1to12, int day, int hour, int min) {
        Calendar c = Calendar.getInstance();
        c.set(year, month1to12 - 1, day, hour, min, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void fromStringDefaultsToDay() {
        assertEquals(ViewWindow.DAY, ViewWindow.fromString(null));
        assertEquals(ViewWindow.DAY, ViewWindow.fromString("nonsense"));
        assertEquals(ViewWindow.WEEK, ViewWindow.fromString("WEEK"));
        assertEquals(ViewWindow.EVENT, ViewWindow.fromString("EVENT"));
        assertEquals(ViewWindow.MONTH, ViewWindow.fromString("MONTH"));
    }

    @Test
    public void eventWindowIsUnbounded() {
        assertEquals(Long.MAX_VALUE, ViewWindow.EVENT.windowEnd(millis(2026, 6, 15, 10, 0)));
    }

    @Test
    public void dayWindowEndsAtEndOfSameDay() {
        long now = millis(2026, 6, 15, 10, 30);
        long end = ViewWindow.DAY.windowEnd(now);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(end);
        assertEquals(15, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(23, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(59, c.get(Calendar.MINUTE));
    }

    @Test
    public void weekWindowEndsOnSaturday() {
        long now = millis(2026, 6, 15, 10, 30); // a Monday
        long end = ViewWindow.WEEK.windowEnd(now);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(end);
        assertEquals(Calendar.SATURDAY, c.get(Calendar.DAY_OF_WEEK));
        assertEquals(23, c.get(Calendar.HOUR_OF_DAY));
        assertTrue(end > now);
    }

    @Test
    public void monthWindowEndsOnLastDayOfMonth() {
        long now = millis(2026, 6, 15, 10, 30); // June has 30 days
        long end = ViewWindow.MONTH.windowEnd(now);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(end);
        assertEquals(30, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.JUNE, c.get(Calendar.MONTH));
        assertEquals(23, c.get(Calendar.HOUR_OF_DAY));
    }
}
