package com.example.myapplication.ui.home;

import android.widget.TextView;

import java.util.UUID;

public class Event {

    public String title;
    public String description;
    public String date;
    public String time;
    public String location;
    public String id;
    public String circleCode; // Added to filter events by circle
    public String recurrence;          // "NONE"/"DAILY"/"WEEKLY"/"MONTHLY"/"YEARLY"; null == NONE
    public String recurrenceEndDate;   // "MM/dd/yyyy" inclusive, or null/empty == forever
    TextView Card;
    int importance;

    public Event() {
        // Required for Firestore
    }

    public Event(String title, String description, String date, String time, String location, String circleCode){
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.date = date;
        this.time = time;
        this.location = location;
        this.circleCode = circleCode;
        this.recurrence = "NONE";
    }

    public String getId(){
        return id;
    }
}
