package com.calendar.lupe;

import static org.junit.Assert.assertEquals;

import com.calendar.lupe.ui.home.CalendarFilter;
import com.calendar.lupe.ui.home.DisplayedListBuilder;
import com.calendar.lupe.ui.home.Event;
import com.calendar.lupe.ui.home.ViewWindow;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DisplayedListBuilderTest {

    private long millis(int y, int m1to12, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.set(y, m1to12 - 1, d, h, min, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private Event recurring(String title, String date, String time, String rec, String end) {
        Event e = new Event(title, "", date, time, "", "circle1");
        e.recurrence = rec;
        e.recurrenceEndDate = end;
        return e;
    }

    @Test
    public void dailyEventExpandsAcrossWeekWindow() {
        List<Event> raw = new ArrayList<>();
        raw.add(recurring("Meds", "06/01/2026", "08:00", "DAILY", null));
        long now = millis(2026, 6, 15, 0, 0); // Monday
        List<Event> shown = DisplayedListBuilder.build(raw, new ArrayList<>(), ViewWindow.WEEK, now);
        // 06/15..06/20 (Saturday) = 6 cards
        assertEquals(6, shown.size());
        assertEquals("06/15/2026", shown.get(0).date);
        assertEquals("Meds", shown.get(0).title);
    }

    @Test
    public void eventWindowShowsSingleSoonest() {
        List<Event> raw = new ArrayList<>();
        raw.add(recurring("Daily", "06/01/2026", "08:00", "DAILY", null));
        Event oneOff = new Event("Doctor", "", "06/15/2026", "07:00", "", "circle1");
        raw.add(oneOff);
        long now = millis(2026, 6, 15, 0, 0);
        List<Event> shown = DisplayedListBuilder.build(raw, new ArrayList<>(), ViewWindow.EVENT, now);
        assertEquals(1, shown.size());
        assertEquals("Doctor", shown.get(0).title); // 07:00 beats the 08:00 daily
    }

    @Test
    public void occurrenceCopiesCarrySeriesId() {
        List<Event> raw = new ArrayList<>();
        Event series = recurring("Meds", "06/15/2026", "08:00", "DAILY", null);
        series.id = "series-123";
        raw.add(series);
        long now = millis(2026, 6, 15, 0, 0);
        List<Event> shown = DisplayedListBuilder.build(raw, new ArrayList<>(), ViewWindow.DAY, now);
        assertEquals(1, shown.size());
        assertEquals("series-123", shown.get(0).id);
        assertEquals("DAILY", shown.get(0).recurrence);
    }

    @Test
    public void dayWindowExcludesFutureOneOff() {
        List<Event> raw = new ArrayList<>();
        raw.add(new Event("Tomorrow", "", "06/16/2026", "10:00", "", "circle1"));
        long now = millis(2026, 6, 15, 9, 0);
        List<Event> shown = DisplayedListBuilder.build(raw, new ArrayList<>(), ViewWindow.DAY, now);
        assertEquals(0, shown.size());
    }

    private Event typed(String title, String date, String time, String type) {
        Event e = new Event(title, "", date, time, "", "circle1");
        e.type = type;
        return e;
    }

    @Test
    public void foodFilterKeepsFoodAndComboDropsActivity() {
        List<Event> raw = new ArrayList<>();
        raw.add(typed("Bingo", "06/15/2026", "10:00", "ACTIVITY"));
        raw.add(typed("Lunch", "06/15/2026", "12:00", "FOOD"));
        raw.add(typed("Tea + chat", "06/15/2026", "14:00", "ACTIVITY_FOOD"));
        long now = millis(2026, 6, 15, 9, 0);
        List<Event> food = DisplayedListBuilder.build(
                raw, new ArrayList<>(), ViewWindow.DAY, now, true);
        assertEquals(2, food.size());
        assertEquals("Lunch", food.get(0).title);
        assertEquals("Tea + chat", food.get(1).title);
    }

    @Test
    public void eventsCalendarKeepsAllTypes() {
        List<Event> raw = new ArrayList<>();
        raw.add(typed("Bingo", "06/15/2026", "10:00", "ACTIVITY"));
        raw.add(typed("Lunch", "06/15/2026", "12:00", "FOOD"));
        long now = millis(2026, 6, 15, 9, 0);
        List<Event> all = DisplayedListBuilder.build(
                raw, new ArrayList<>(), ViewWindow.DAY, now, false);
        assertEquals(2, all.size());
    }

    @Test
    public void activitiesFilterKeepsActivityAndComboDropsFood() {
        List<Event> raw = new ArrayList<>();
        raw.add(typed("Bingo", "06/15/2026", "10:00", "ACTIVITY"));
        raw.add(typed("Lunch", "06/15/2026", "12:00", "FOOD"));
        raw.add(typed("Tea + chat", "06/15/2026", "14:00", "ACTIVITY_FOOD"));
        long now = millis(2026, 6, 15, 9, 0);
        List<Event> activities = DisplayedListBuilder.build(
                raw, new ArrayList<>(), ViewWindow.DAY, now, CalendarFilter.ACTIVITIES);
        assertEquals(2, activities.size());
        assertEquals("Bingo", activities.get(0).title);
        assertEquals("Tea + chat", activities.get(1).title);
    }

    @Test
    public void allFilterKeepsEveryType() {
        List<Event> raw = new ArrayList<>();
        raw.add(typed("Bingo", "06/15/2026", "10:00", "ACTIVITY"));
        raw.add(typed("Lunch", "06/15/2026", "12:00", "FOOD"));
        raw.add(typed("Tea + chat", "06/15/2026", "14:00", "ACTIVITY_FOOD"));
        long now = millis(2026, 6, 15, 9, 0);
        List<Event> all = DisplayedListBuilder.build(
                raw, new ArrayList<>(), ViewWindow.DAY, now, CalendarFilter.ALL);
        assertEquals(3, all.size());
    }

    @Test
    public void foodFilterAppliesBeforeEventCollapse() {
        // Soonest overall is an ACTIVITY; food calendar must still surface the soonest FOOD.
        List<Event> raw = new ArrayList<>();
        raw.add(typed("Early walk", "06/15/2026", "07:00", "ACTIVITY"));
        raw.add(typed("Brunch", "06/15/2026", "11:00", "FOOD"));
        long now = millis(2026, 6, 15, 6, 0);
        List<Event> food = DisplayedListBuilder.build(
                raw, new ArrayList<>(), ViewWindow.EVENT, now, true);
        assertEquals(1, food.size());
        assertEquals("Brunch", food.get(0).title);
    }
}
