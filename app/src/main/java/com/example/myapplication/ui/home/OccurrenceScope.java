package com.example.myapplication.ui.home;

/** Scope of a favorite/mandatory action on a (possibly recurring) event. */
public enum OccurrenceScope {
    /** Only the selected occurrence. */
    INSTANCE,
    /** The selected occurrence and every later one. */
    FORWARD,
    /** The whole series. */
    SERIES
}
