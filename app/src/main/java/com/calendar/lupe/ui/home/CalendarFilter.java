package com.calendar.lupe.ui.home;


public enum CalendarFilter {

    ACTIVITIES,

    FOOD,

    ALL;

    public boolean shows(EventType type) {
        switch (this) {
            case FOOD:       return type.showsInFoodCalendar();
            case ACTIVITIES: return type.showsInActivityCalendar();
            case ALL:
            default:         return true;
        }
    }


    public static CalendarFilter fromMode(String mode) {
        if ("FOOD".equals(mode)) return FOOD;
        if ("ALL".equals(mode)) return ALL;
        return ACTIVITIES;
    }
}
