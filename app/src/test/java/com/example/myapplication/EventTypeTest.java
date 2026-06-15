package com.example.myapplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.myapplication.ui.home.EventType;

import org.junit.Test;

public class EventTypeTest {

    @Test
    public void fromStringDefaultsToActivity() {
        assertEquals(EventType.ACTIVITY, EventType.fromString(null));
        assertEquals(EventType.ACTIVITY, EventType.fromString(""));
        assertEquals(EventType.ACTIVITY, EventType.fromString("garbage"));
        assertEquals(EventType.ACTIVITY, EventType.fromString("ACTIVITY"));
    }

    @Test
    public void fromStringParsesKnownValues() {
        assertEquals(EventType.FOOD, EventType.fromString("FOOD"));
        assertEquals(EventType.ACTIVITY_FOOD, EventType.fromString("ACTIVITY_FOOD"));
    }

    @Test
    public void showsInFoodCalendarTruthTable() {
        assertFalse(EventType.ACTIVITY.showsInFoodCalendar());
        assertTrue(EventType.FOOD.showsInFoodCalendar());
        assertTrue(EventType.ACTIVITY_FOOD.showsInFoodCalendar());
    }
}
