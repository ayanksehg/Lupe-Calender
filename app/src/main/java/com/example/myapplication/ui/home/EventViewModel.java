package com.example.myapplication.ui.home;

import android.util.Log;
import android.widget.Toast;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.CalendarEvent;
import com.example.myapplication.CalendarResponse;
import com.example.myapplication.CalendarService;
import com.example.myapplication.ui.start.Mode;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class EventViewModel extends ViewModel {
    private final MutableLiveData<List<Event>> events = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Mode> selectedMode = new MutableLiveData<>(Mode.START);
    private final MutableLiveData<String> currentCircleCode = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);
    private final MutableLiveData<List<Event>> rawEvents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ViewWindow> viewWindow = new MutableLiveData<>(ViewWindow.DAY);
    private final MutableLiveData<Integer> notificationLeadMinutes = new MutableLiveData<>(0);
    private boolean foodOnly = false;
    private String userName = "";
    private List<Event> rawGoogleActivity = new ArrayList<>();
    private List<Event> rawGoogleFood = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration listenerRegistration;

    public LiveData<List<Event>> getEvents() {
        return events;
    }

    public LiveData<List<Event>> getRawEvents() {
        return rawEvents;
    }

    public LiveData<ViewWindow> getViewWindow() {
        return viewWindow;
    }

    public LiveData<Integer> getNotificationLeadMinutes() {
        return notificationLeadMinutes;
    }

    /** Synchronous accessor for the scheduling call site (SlideshowFragment). */
    public int getNotificationLeadMinutesValue() {
        Integer v = notificationLeadMinutes.getValue();
        return v == null ? 0 : v;
    }

    private void rebuildDisplayedList() {
        List<Event> raw = rawEvents.getValue();
        if (raw == null) raw = new ArrayList<>();
        ViewWindow window = viewWindow.getValue();
        if (window == null) window = ViewWindow.DAY;
        List<Event> google = new ArrayList<>();
        google.addAll(rawGoogleActivity);
        google.addAll(rawGoogleFood);
        events.postValue(DisplayedListBuilder.build(
                raw, google, window, System.currentTimeMillis(), foodOnly));
    }

    public LiveData<Boolean> getIsRefreshing() {
        return isRefreshing;
    }

    public void refreshGoogleCalendarEvents() {
        String code = currentCircleCode.getValue();
        if (code == null || code.isEmpty()) {
            isRefreshing.postValue(false);
            return;
        }
        isRefreshing.postValue(true);
        fetchEventsForCircle(code);
    }

    public LiveData<String> getCurrentCircleCode() {
        return currentCircleCode;
    }

    public void setCurrentCircleCode(String code) {
        currentCircleCode.setValue(code);
        loadEvents(code);
    }

    private void loadEvents(String circleCode) {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }

        if (circleCode == null || circleCode.isEmpty()) {
            rawEvents.setValue(new ArrayList<>());
            rawGoogleActivity = new ArrayList<>();
            rawGoogleFood = new ArrayList<>();
            events.setValue(new ArrayList<>());
            return;
        }

        listenerRegistration = db.collection("events")
                .whereEqualTo("circleCode", circleCode)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (value != null) {
                        List<Event> eventList = new ArrayList<>();
                        long now = System.currentTimeMillis();
                        for (QueryDocumentSnapshot doc : value) {
                            Event event = doc.toObject(Event.class);
                            event.id = doc.getId();

                            boolean recurring = event.recurrence != null
                                    && !"NONE".equals(event.recurrence)
                                    && !event.recurrence.isEmpty();

                            if (recurring) {
                                // Delete the series only once it is fully exhausted.
                                if (RecurrenceExpander.nextOccurrenceAfter(event.date, event.time,
                                        event.recurrence, event.recurrenceEndDate, now) == -1) {
                                    db.collection("events").document(event.getId()).delete();
                                    continue;
                                }
                            } else {
                                // One-off: delete once it has passed (unchanged behavior).
                                if (parseDateTimeToMillis(event.date, event.time) <= now) {
                                    db.collection("events").document(event.getId()).delete();
                                    continue;
                                }
                            }
                            eventList.add(event);
                        }
                        rawEvents.setValue(eventList);
                        rebuildDisplayedList();
                        fetchEventsForCircle(circleCode);
                    }
                });
    }

    public void fetchGoogleCalendarEvents(String calendarId) {
        fetchGoogleCalendarEvents(calendarId, "ACTIVITY");
    }

    public void fetchGoogleCalendarEvents(String calendarId, String type) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        CalendarService service = retrofit.create(CalendarService.class);


        String apiKey = BuildConfig.CALENDAR_API_KEY;

        // timeMin is required when using orderBy=startTime
        SimpleDateFormat rfc3339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        rfc3339.setTimeZone(TimeZone.getDefault());
        String timeMin = rfc3339.format(new Date());

        Log.d("CalendarAPI", "Fetching Google Calendar events for ID: " + calendarId + " with timeMin: " + timeMin);

        service.getEvents(calendarId, apiKey, true, "startTime", timeMin)
                .enqueue(new Callback<CalendarResponse>() {
                    @Override
                    public void onResponse(Call<CalendarResponse> call, Response<CalendarResponse> response) {
                        Log.d("CalendarAPI", "Response received. Successful: " + response.isSuccessful() + ", Code: " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            List<CalendarEvent> items = response.body().items;
                            Log.d("CalendarAPI", "Response body items count: " + (items != null ? items.size() : "null"));
                            
                            List<Event> googleEvents = parseCalendarItems(items, type);
                            Log.d("CalendarAPI", "Parsed " + googleEvents.size() + " Google Calendar events.");

                            if ("FOOD".equals(type)) {
                                rawGoogleFood = googleEvents;
                            } else {
                                rawGoogleActivity = googleEvents;
                            }
                            rebuildDisplayedList();
                            isRefreshing.postValue(false);
                        } else {
                            String errorBody = "";
                            try { errorBody = response.errorBody() != null ? response.errorBody().string() : ""; } catch (Exception ignored) {}
                            Log.e("CalendarAPI", "Error fetching Google Calendar events. Code: " + response.code() + " Body: " + errorBody);
                        }
                    }

                    @Override
                    public void onFailure(Call<CalendarResponse> call, Throwable t) {
                        Log.e("CalendarAPI", "Network failure fetching Google Calendar events", t);
                    }
                });
    }

    public void fetchEventsForCircle(String circleCode) {
        FirebaseFirestore.getInstance()
                .collection("circles")
                .document(circleCode)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getString("viewWindow") != null) {
                        // setValue (not postValue) so the value is visible to the rebuild on this
                        // same main-thread callback; postValue would defer and rebuild stale.
                        viewWindow.setValue(ViewWindow.fromString(doc.getString("viewWindow")));
                        rebuildDisplayedList();
                    }
                    if (doc.exists() && doc.getLong("notificationLeadMinutes") != null) {
                        notificationLeadMinutes.setValue(doc.getLong("notificationLeadMinutes").intValue());
                    }
                    String calendarId = doc.exists() ? doc.getString("calendarId") : null;
                    String mealCalendarId = doc.exists() ? doc.getString("mealCalendarId") : null;

                    boolean any = false;
                    if (calendarId != null && !calendarId.isEmpty()) {
                        fetchGoogleCalendarEvents(calendarId, "ACTIVITY");
                        any = true;
                    } else {
                        rawGoogleActivity = new ArrayList<>();
                    }
                    if (mealCalendarId != null && !mealCalendarId.isEmpty()) {
                        fetchGoogleCalendarEvents(mealCalendarId, "FOOD");
                        any = true;
                    } else {
                        rawGoogleFood = new ArrayList<>();
                    }
                    if (!any) {
                        rebuildDisplayedList();
                        isRefreshing.postValue(false);
                    }
                })
                .addOnFailureListener(e -> isRefreshing.postValue(false));
    }

    private List<Event> parseCalendarItems(List<CalendarEvent> items, String type) {
        List<Event> events = new ArrayList<>();
        if (items == null) return events;

        SimpleDateFormat inputDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        SimpleDateFormat inputDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat outputDate = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        SimpleDateFormat outputTime = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (CalendarEvent ce : items) {
            try {
                if (ce.start == null) continue;

                Date start;
                boolean allDay = false;

                if (ce.start.dateTime != null) {
                    start = inputDateTime.parse(ce.start.dateTime);
                } else if (ce.start.date != null) {
                    start = inputDate.parse(ce.start.date);
                    allDay = true;
                } else {
                    continue;
                }

                String date = outputDate.format(start);
                String time = allDay ? "All Day" : outputTime.format(start);

                Event ge = new Event(
                        ce.summary != null ? ce.summary : "No Title",
                        ce.description != null ? ce.description : "",
                        date,
                        time,
                        ce.location != null ? ce.location : "",
                        "google_calendar"
                );
                ge.type = type;
                events.add(ge);
            } catch (Exception e) {
                Log.e("CalendarAPI", "Error parsing calendar item: " + ce.summary, e);
            }
        }

        return events;
    }
    public void deleteEvent(Event event) {
        if (event.getId() != null) {
            db.collection("events").document(event.getId()).delete();
        }
    }

    public void addEvent(Event event) {
        String code = currentCircleCode.getValue();
        if (code != null && !code.isEmpty()) {
            event.circleCode = code;
            db.collection("events").document(event.getId()).set(event);
        }
    }

    private long parseDateTimeToMillis(String date, String time) {
        try{
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault());
            Date parsed = sdf.parse(date +" "+ time);
            return parsed.getTime();
        }
        catch (Exception e){
            return -1;
        }
    }

    public LiveData<Mode> getSelectedMode() {
        return selectedMode;
    }

    public void setSelectedMode(Mode mode) {
        selectedMode.setValue(mode);
    }

    public void setViewWindow(ViewWindow window) {
        viewWindow.setValue(window);
        rebuildDisplayedList();
        String code = currentCircleCode.getValue();
        if (code != null && !code.isEmpty()) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("viewWindow", window.name());
            db.collection("circles").document(code)
                    .set(data, com.google.firebase.firestore.SetOptions.merge());
        }
    }
    public void setNotificationLeadMinutes(int minutes) {
        notificationLeadMinutes.setValue(minutes);
        String code = currentCircleCode.getValue();
        if (code != null && !code.isEmpty()) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("notificationLeadMinutes", minutes);
            db.collection("circles").document(code)
                    .set(data, com.google.firebase.firestore.SetOptions.merge());
        }
    }
    public void setCalendarFilter(boolean foodOnly) {
        this.foodOnly = foodOnly;
        rebuildDisplayedList();
    }

    public void setUserName(String name) {
        this.userName = name == null ? "" : name;
    }

    public String getUserName() {
        return userName;
    }

    /** Admin edit: patch type + description on an existing Firestore event doc. */
    public void updateEvent(String eventId, String type, String description) {
        if (eventId == null || eventId.isEmpty()) return;
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("type", type);
        data.put("description", description == null ? "" : description);
        db.collection("events").document(eventId)
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public LiveData<String> getCircleCode(){
        return currentCircleCode;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}
