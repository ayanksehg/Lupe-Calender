package com.calendar.lupe.ui.home;

public enum EventType {
    ACTIVITY, FOOD, ACTIVITY_FOOD;


    public static EventType fromString(String s) {
        if (s == null) return ACTIVITY;
        switch (s) {
            case "FOOD":          return FOOD;
            case "ACTIVITY_FOOD": return ACTIVITY_FOOD;
            case "ACTIVITY":      return ACTIVITY;
            default:              return ACTIVITY;
        }
    }


    public boolean showsInFoodCalendar() {
        return this == FOOD || this == ACTIVITY_FOOD;
    }

  
    public boolean showsInActivityCalendar() {
        return this == ACTIVITY || this == ACTIVITY_FOOD;
    }
}
