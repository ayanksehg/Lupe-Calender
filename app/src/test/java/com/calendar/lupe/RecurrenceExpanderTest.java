package com.calendar.lupe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.calendar.lupe.ui.home.RecurrenceExpander;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecurrenceExpanderTest {

    private long millis(int y, int m1to12, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.set(y, m1to12 - 1, d, h, min, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void nextOccurrenceForOneOffReturnsItWhenFuture() {
        long now = millis(2026, 6, 15, 9, 0);
        long t = RecurrenceExpander.nextOccurrenceAfter("06/16/2026", "10:00", "NONE", null, now);
        assertEquals(millis(2026, 6, 16, 10, 0), t);
    }

    @Test
    public void nextOccurrenceForPastOneOffReturnsMinusOne() {
        long now = millis(2026, 6, 15, 9, 0);
        assertEquals(-1L, RecurrenceExpander.nextOccurrenceAfter("06/14/2026", "10:00", "NONE", null, now));
    }

    @Test
    public void dailyAdvancesPastNow() {
        // base in the past; daily should roll forward to the first slot after now
        long now = millis(2026, 6, 15, 9, 0);
        long t = RecurrenceExpander.nextOccurrenceAfter("06/01/2026", "08:00", "DAILY", null, now);
        assertEquals(millis(2026, 6, 16, 8, 0), t); // 06/15 08:00 is before now (09:00), so next is 06/16
    }

    @Test
    public void recurrenceStopsAfterEndDate() {
        long now = millis(2026, 6, 15, 9, 0);
        long t = RecurrenceExpander.nextOccurrenceAfter("06/01/2026", "08:00", "DAILY", "06/15/2026", now);
        assertEquals(-1L, t); // last valid occurrence 06/15 08:00 is before now; nothing left
    }

    @Test
    public void weeklyWindowExpandsToFourCards() {
        long now = millis(2026, 6, 1, 0, 0);
        long windowEnd = millis(2026, 6, 30, 23, 59);
        List<String> dates = RecurrenceExpander.occurrenceDatesInWindow(
                "06/02/2026", "08:00", "WEEKLY", null, now, windowEnd);
        assertEquals(5, dates.size()); // 06/02, 06/09, 06/16, 06/23, 06/30
        assertEquals("06/02/2026", dates.get(0));
        assertEquals("06/30/2026", dates.get(4));
    }

    @Test
    public void dailyWindowRespectsEndDate() {
        long now = millis(2026, 6, 14, 23, 0);
        long windowEnd = millis(2026, 6, 30, 23, 59);
        List<String> dates = RecurrenceExpander.occurrenceDatesInWindow(
                "06/10/2026", "08:00", "DAILY", "06/17/2026", now, windowEnd);
        // occurrences strictly after now (06/14 23:00) through end date 06/17: 06/15, 06/16, 06/17
        assertEquals(3, dates.size());
        assertEquals("06/15/2026", dates.get(0));
        assertEquals("06/17/2026", dates.get(2));
    }

    @Test
    public void monthlyClampsShortMonths() {
        long now = millis(2026, 1, 31, 9, 0);
        long t = RecurrenceExpander.nextOccurrenceAfter("01/31/2026", "08:00", "MONTHLY", null, now);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH));
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH)); // 2026 is not a leap year
    }

    @Test
    public void excludedDateIsSkippedInWindow() {
        long now = millis(2026, 6, 1, 0, 0);
        long windowEnd = millis(2026, 6, 30, 23, 59);
        Set<String> excluded = new HashSet<>(Arrays.asList("06/16/2026"));
        List<String> dates = RecurrenceExpander.occurrenceDatesInWindow(
                "06/02/2026", "08:00", "WEEKLY", null, now, windowEnd, excluded);
        // 06/02, 06/09, [06/16 skipped], 06/23, 06/30
        assertEquals(4, dates.size());
        assertTrue(!dates.contains("06/16/2026"));
        assertEquals("06/23/2026", dates.get(2));
    }

    @Test
    public void nextOccurrenceSkipsExcludedDate() {
        long now = millis(2026, 6, 15, 9, 0);
        Set<String> excluded = new HashSet<>(Arrays.asList("06/16/2026"));
        long t = RecurrenceExpander.nextOccurrenceAfter(
                "06/01/2026", "08:00", "DAILY", null, now, excluded);
        // 06/16 08:00 would be next but is excluded -> 06/17 08:00
        assertEquals(millis(2026, 6, 17, 8, 0), t);
    }

    @Test
    public void allRemainingExcludedReturnsMinusOne() {
        long now = millis(2026, 6, 15, 9, 0);
        Set<String> excluded = new HashSet<>(Arrays.asList("06/16/2026", "06/17/2026"));
        long t = RecurrenceExpander.nextOccurrenceAfter(
                "06/16/2026", "08:00", "DAILY", "06/17/2026", now, excluded);
        assertEquals(-1L, t);
    }

    @Test
    public void excludedOneOffReturnsMinusOne() {
        long now = millis(2026, 6, 15, 9, 0);
        Set<String> excluded = new HashSet<>(Arrays.asList("06/16/2026"));
        long t = RecurrenceExpander.nextOccurrenceAfter(
                "06/16/2026", "10:00", "NONE", null, now, excluded);
        assertEquals(-1L, t);
    }

    @Test
    public void everyOtherDayAdvancesTwoDays() {
        long now = millis(2026, 6, 15, 9, 0);
        long t = RecurrenceExpander.nextOccurrenceAfter("06/01/2026", "08:00", "EVERY_OTHER_DAY", null, now);
        // base 06/01, +2 day steps land on odd days: ... 06/15 08:00 is before now -> next is 06/17
        assertEquals(millis(2026, 6, 17, 8, 0), t);
    }

    @Test
    public void everyOtherDayWindowSkipsAlternateDays() {
        long now = millis(2026, 6, 9, 0, 0);
        long windowEnd = millis(2026, 6, 17, 23, 59);
        List<String> dates = RecurrenceExpander.occurrenceDatesInWindow(
                "06/10/2026", "08:00", "EVERY_OTHER_DAY", null, now, windowEnd);
        // 06/10, 06/12, 06/14, 06/16
        assertEquals(4, dates.size());
        assertEquals("06/10/2026", dates.get(0));
        assertEquals("06/16/2026", dates.get(3));
    }

    @Test
    public void dayBeforeBasic() {
        assertEquals("06/15/2026", RecurrenceExpander.dayBefore("06/16/2026"));
    }

    @Test
    public void dayBeforeMonthBoundary() {
        assertEquals("05/31/2026", RecurrenceExpander.dayBefore("06/01/2026"));
    }

    @Test
    public void oneOffInWindow() {
        long now = millis(2026, 6, 15, 0, 0);
        long windowEnd = millis(2026, 6, 15, 23, 59);
        List<String> in = RecurrenceExpander.occurrenceDatesInWindow(
                "06/15/2026", "10:00", "NONE", null, now, windowEnd);
        assertEquals(1, in.size());
        List<String> out = RecurrenceExpander.occurrenceDatesInWindow(
                "06/16/2026", "10:00", "NONE", null, now, windowEnd);
        assertTrue(out.isEmpty());
    }
}
