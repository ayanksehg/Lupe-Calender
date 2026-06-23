package com.calendar.lupe;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface CalendarService {
    @GET("calendar/v3/calendars/{calendarId}/events")
    Call<CalendarResponse> getEvents(
            @Path("calendarId") String calendarId,
            @Query("key") String apiKey,
            @Query("singleEvents") boolean singleEvents,
            @Query("orderBy") String orderBy,
            @Query("timeMin") String timeMin
    );
}
