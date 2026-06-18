package com.example.myapplication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.myapplication.ui.home.Event;

import org.junit.Test;

import java.util.Arrays;

public class EventMandatoryTest {

    private Event ev() {
        return new Event("t", "", "06/16/2026", "10:00", "", "c1");
    }

    @Test
    public void notMandatoryByDefault() {
        assertFalse(ev().isMandatoryOn("06/16/2026"));
    }

    @Test
    public void seriesMandatoryAlwaysTrue() {
        Event e = ev();
        e.importance = true;
        assertTrue(e.isMandatoryOn("01/01/2030"));
    }

    @Test
    public void instanceMandatoryOnlyThatDate() {
        Event e = ev();
        e.mandatoryDates = Arrays.asList("06/16/2026");
        assertTrue(e.isMandatoryOn("06/16/2026"));
        assertFalse(e.isMandatoryOn("06/17/2026"));
    }

    @Test
    public void forwardMandatoryFromDateOnward() {
        Event e = ev();
        e.mandatoryFrom = "06/16/2026";
        assertFalse(e.isMandatoryOn("06/15/2026"));
        assertTrue(e.isMandatoryOn("06/16/2026"));
        assertTrue(e.isMandatoryOn("07/01/2026"));
    }
}
