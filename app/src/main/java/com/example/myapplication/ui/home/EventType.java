package com.example.myapplication.ui.home;

public enum EventType {
    ACTIVITY, FOOD, ACTIVITY_FOOD;

    /** Parse a stored type string, defaulting to ACTIVITY for null/blank/unknown. */
    public static EventType fromString(String s) {
        if (s == null) return ACTIVITY;
        switch (s) {
            case "FOOD":          return FOOD;
            case "ACTIVITY_FOOD": return ACTIVITY_FOOD;
            case "ACTIVITY":      return ACTIVITY;
            default:              return ACTIVITY;
        }
    }

    /** Food and Activity-with-food appear in the Food calendar; plain Activity does not. */
    public boolean showsInFoodCalendar() {
        return this == FOOD || this == ACTIVITY_FOOD;
    }

    /** Activity and Activity-with-food appear in the Activities calendar; plain Food does not. */
    public boolean showsInActivityCalendar() {
        return this == ACTIVITY || this == ACTIVITY_FOOD;
    }
}
