package com.calendar.lupe;

import java.util.Random;


public enum LoaderVariant {
    CLOCK,
    CALENDAR;

    public static LoaderVariant pick(Random r) {
        return r.nextBoolean() ? CLOCK : CALENDAR;
    }
}
