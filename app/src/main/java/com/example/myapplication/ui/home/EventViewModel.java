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
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration listenerRegistration;

    public LiveData<List<Event>> getEvents() {
        return events;
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
                        for (QueryDocumentSnapshot doc : value) {

                            Event event = doc.toObject(Event.class);
                            event.id = doc.getId();
                            if (parseDateTimeToMillis(event.date, event.time)<= System.currentTimeMillis()){
                                FirebaseFirestore.getInstance().collection("events").document(event.getId()).delete();
                                continue;
                            }
                            eventList.add(event);
                        }
                        events.setValue(eventList);
                        fetchEventsForCircle(circleCode);
                    }
                });
    }

    public void fetchGoogleCalendarEvents(String calendarId) {
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
                            
                            List<Event> googleEvents = parseCalendarItems(items);
                            Log.d("CalendarAPI", "Parsed " + googleEvents.size() + " Google Calendar events.");

                            // Get the current list (which contains Firestore events)
                            List<Event> currentEvents = events.getValue();
                            if (currentEvents == null) currentEvents = new ArrayList<>();

                            // Rebuild the merged list, dropping any previously-fetched Google
                            // Calendar events so repeated refreshes don't create duplicates.
                            List<Event> mergedList = new ArrayList<>();
                            for (Event e : currentEvents) {
                                if (!"google_calendar".equals(e.circleCode)) {
                                    mergedList.add(e);
                                }
                            }
                            mergedList.addAll(googleEvents);

                            events.postValue(mergedList);
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
                    if (doc.exists() && doc.getString("calendarId") != null) {
                        String calendarId = doc.getString("calendarId");
                        fetchGoogleCalendarEvents(calendarId);
                    } else {
                        // No calendar linked for this circle; nothing to fetch.
                        isRefreshing.postValue(false);
                    }
                })
                .addOnFailureListener(e -> isRefreshing.postValue(false));
    }

    private List<Event> parseCalendarItems(List<CalendarEvent> items) {
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

                events.add(new Event(
                        ce.summary != null ? ce.summary : "No Title",
                        ce.description != null ? ce.description : "",
                        date,
                        time,
                        ce.location != null ? ce.location : "",
                        "google_calendar"
                ));
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
