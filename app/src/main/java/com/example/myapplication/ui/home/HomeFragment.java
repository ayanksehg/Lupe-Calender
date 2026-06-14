package com.example.myapplication.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.FontScaleHelper;
import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.databinding.EventcardBinding;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.example.myapplication.ui.start.Mode;

import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private EventViewModel eventViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        ViewGroup llContainer = binding.layoutContainer;

        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);


        binding.swipeRefresh.setOnRefreshListener(eventViewModel::refreshGoogleCalendarEvents);
        eventViewModel.getIsRefreshing().observe(getViewLifecycleOwner(), refreshing -> {
            if (binding != null) {
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing));
            }
        });

        eventViewModel.getEvents().observe(getViewLifecycleOwner(), events -> {
            llContainer.removeAllViews();
            if (events == null || events.isEmpty()) {
                binding.emptyState.setVisibility(View.VISIBLE);
                llContainer.setVisibility(View.GONE);
            } else {
                binding.emptyState.setVisibility(View.GONE);
                llContainer.setVisibility(View.VISIBLE);
                Mode selectedMode = eventViewModel.getSelectedMode().getValue();

                List<Event> sorted = new ArrayList<>(events);
                Collections.sort(sorted, Comparator.comparingLong(this::sortKey));

                String previousDate = null;
                for (Event e : sorted) {
                    if (e.date != null && !e.date.equals(previousDate)) {
                        addDateHeader(llContainer, e.date);
                        previousDate = e.date;
                    }
                    addCard(llContainer, e, selectedMode, eventViewModel);
                }
            }
        });

        eventViewModel.getRawEvents().observe(getViewLifecycleOwner(), rawEvents -> {
            if (rawEvents == null) return;
            long now = System.currentTimeMillis();
            for (Event e : rawEvents) {
                if (e.circleCode != null && e.circleCode.equals("google_calendar")) continue;
                long next = RecurrenceExpander.nextOccurrenceAfter(
                        e.date, e.time, e.recurrence, e.recurrenceEndDate, now);
                if (next <= 0) continue;
                ((MainActivity) requireActivity()).scheduleEventNotification(
                        next, e.title, e.getId().hashCode(), e.getId(),
                        e.recurrence == null ? "NONE" : e.recurrence,
                        e.recurrenceEndDate);
            }
        });

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FontScaleHelper.applyFontScale(view, requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (eventViewModel != null) {
            eventViewModel.refreshGoogleCalendarEvents();
        }
    }

    private String recurrenceLabel(String recurrence) {
        if (recurrence == null || "NONE".equals(recurrence) || recurrence.isEmpty()) return null;
        switch (recurrence) {
            case "DAILY":   return "Repeats daily";
            case "WEEKLY":  return "Repeats weekly";
            case "MONTHLY": return "Repeats monthly";
            case "YEARLY":  return "Repeats yearly";
            default:        return null;
        }
    }

    private long parseDateTimeToMillis(String date, String time) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault());
            Date parsed = sdf.parse(date + " " + time);
            return parsed.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    private long sortKey(Event event) {
        String timeForSort = ("All Day".equals(event.time) || event.time == null) ? "00:00" : event.time;
        long millis = parseDateTimeToMillis(event.date, timeForSort);
        return millis < 0 ? Long.MAX_VALUE : millis;
    }

    private void addDateHeader(ViewGroup container, String date) {
        View header = LayoutInflater.from(container.getContext())
                .inflate(R.layout.event_date_header, container, false);
        TextView label = header.findViewById(R.id.text_date_header);
        label.setText(formatHeaderDate(date));
        container.addView(header);
    }

    private String formatHeaderDate(String date) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
            Date parsed = in.parse(date);
            return parsed == null ? date : out.format(parsed);
        } catch (Exception e) {
            return date;
        }
    }


    private void addCard(ViewGroup container, Event event, Mode mode, EventViewModel eventViewModel) {
        EventcardBinding eventcardBinding = EventcardBinding.inflate(
                LayoutInflater.from(container.getContext()), container, false);

        eventcardBinding.textTitle.setText(event.title);
        eventcardBinding.textDate.setText(event.date);
        eventcardBinding.textTime.setText(event.time);

        String recurLabel = recurrenceLabel(event.recurrence);
        if (recurLabel != null) {
            eventcardBinding.textRecurrence.setText(recurLabel);
            eventcardBinding.textRecurrence.setVisibility(View.VISIBLE);
        } else {
            eventcardBinding.textRecurrence.setVisibility(View.GONE);
        }

        Button btnDelete = eventcardBinding.btnDelete;

        if(mode == Mode.ADMIN){
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> eventViewModel.deleteEvent(event));
        }else{
            btnDelete.setVisibility(View.GONE);
        }

        container.addView(eventcardBinding.getRoot());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


    }

