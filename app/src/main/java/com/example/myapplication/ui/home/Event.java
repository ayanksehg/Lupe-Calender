package com.example.myapplication.ui.home;

import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class Event {

    public String title;
    public String description;
    public String date;
    public String time;
    public String location;
    public String id;
    public String circleCode;
    public String type;
    public String recurrence;
    public String recurrenceEndDate;
    public List<String> excludedDates;

    public boolean importance;          // whole-series mandatory
    public String mandatoryFrom;        // "MM/dd/yyyy" inclusive forward cutoff, or null
    public List<String> mandatoryDates; // per-instance mandatory occurrences ("MM/dd/yyyy")
    TextView Card;


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
        this.importance = false;
        this.type = "ACTIVITY";
    }

    public String getId(){
        return id;
    }


    public boolean isMandatoryOn(String occurrenceDate) {
        if (importance) return true;
        if (mandatoryDates != null && occurrenceDate != null
                && mandatoryDates.contains(occurrenceDate)) return true;
        if (mandatoryFrom != null && occurrenceDate != null) {
            long from = parseDate(mandatoryFrom);
            long when = parseDate(occurrenceDate);
            if (from >= 0 && when >= 0 && when >= from) return true;
        }
        return false;
    }

    private static long parseDate(String date) {
        try {
            Date d = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(date.trim());
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }
}
