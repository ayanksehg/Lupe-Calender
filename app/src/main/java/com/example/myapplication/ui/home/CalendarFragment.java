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
import com.example.myapplication.databinding.FragmentCalendarBinding;
import com.example.myapplication.ui.start.Mode;

import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private static final int DESC_LIMIT = 120;

    private FragmentCalendarBinding binding;
    private EventViewModel eventViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        ViewGroup llContainer = binding.layoutContainer;

        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);

        String mode = getArguments() != null ? getArguments().getString("calendarMode", "EVENTS") : "EVENTS";
        boolean foodOnly = "FOOD".equals(mode);
        eventViewModel.setCalendarFilter(foodOnly);
        binding.textHome.setText(foodOnly ? "Food" : "Upcoming Events");

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
                int leadMinutes = eventViewModel.getNotificationLeadMinutesValue();
                ((MainActivity) requireActivity()).scheduleEventNotification(
                        next - leadMinutes * 60_000L, e.title, e.getId().hashCode(), e.getId(),
                        e.recurrence == null ? "NONE" : e.recurrence,
                        e.recurrenceEndDate, leadMinutes);
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
        String mode = getArguments() != null ? getArguments().getString("calendarMode", "EVENTS") : "EVENTS";
        if (eventViewModel != null) {
            eventViewModel.setCalendarFilter("FOOD".equals(mode));
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

        EventType type = EventType.fromString(event.type);
        int cardColor;
        String badge;
        switch (type) {
            case FOOD:          cardColor = R.color.food_red;             badge = "Food"; break;
            case ACTIVITY_FOOD: cardColor = R.color.activity_food_purple; badge = "Activity + Food"; break;
            case ACTIVITY:
            default:            cardColor = R.color.watch_blue_dark;      badge = "Activity"; break;
        }
        int colorInt = androidx.core.content.ContextCompat.getColor(container.getContext(), cardColor);
        ((com.google.android.material.card.MaterialCardView) eventcardBinding.getRoot())
                .setCardBackgroundColor(colorInt);
        eventcardBinding.textTypeBadge.setText(badge);
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setCornerRadius(24f);
        badgeBg.setColor(0x33FFFFFF); // translucent white pill over the card color
        eventcardBinding.textTypeBadge.setBackground(badgeBg);

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

        String desc = event.description;
        boolean hasDesc = desc != null && !desc.trim().isEmpty();
        if (hasDesc) {
            final String full = desc.trim();
            eventcardBinding.textDescription.setVisibility(View.VISIBLE);
            if (full.length() > DESC_LIMIT) {
                eventcardBinding.textDescription.setText(full.substring(0, DESC_LIMIT).trim() + "… (more)");
                eventcardBinding.textDescription.setOnClickListener(v -> showDescriptionDialog(event.title, full));
            } else {
                eventcardBinding.textDescription.setText(full);
                eventcardBinding.textDescription.setOnClickListener(null);
            }
            eventcardBinding.getRoot().setOnLongClickListener(v -> {
                showDescriptionDialog(event.title, full);
                return true;
            });
        } else {
            eventcardBinding.textDescription.setVisibility(View.GONE);
            eventcardBinding.getRoot().setOnLongClickListener(null);
        }

        Button btnDelete = eventcardBinding.btnDelete;

        if(mode == Mode.ADMIN){
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> eventViewModel.deleteEvent(event));
        }else{
            btnDelete.setVisibility(View.GONE);
        }

        com.google.android.material.button.MaterialButton btnEdit = eventcardBinding.btnEdit;
        boolean isGoogle = event.circleCode != null && event.circleCode.equals("google_calendar");
        if (mode == Mode.ADMIN && !isGoogle) {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v -> showEditDialog(event, eventViewModel));
        } else {
            btnEdit.setVisibility(View.GONE);
        }

        container.addView(eventcardBinding.getRoot());
    }

    private void showDescriptionDialog(String title, String fullText) {
        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title == null ? "Details" : title)
                        .setMessage(fullText)
                        .setPositiveButton("Close", null)
                        .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                FontScaleHelper.applyFontScale(dialog.getWindow().getDecorView(), requireContext());
            }
        });
        dialog.show();
    }

    private void showEditDialog(Event event, EventViewModel eventViewModel) {
        View body = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_event, null, false);
        android.widget.RadioGroup radioGroup = body.findViewById(R.id.radio_type);
        android.widget.EditText descInput = body.findViewById(R.id.edit_description);

        EventType current = EventType.fromString(event.type);
        switch (current) {
            case FOOD:          radioGroup.check(R.id.radio_food); break;
            case ACTIVITY_FOOD: radioGroup.check(R.id.radio_activity_food); break;
            case ACTIVITY:
            default:            radioGroup.check(R.id.radio_activity); break;
        }
        if (event.description != null) descInput.setText(event.description);

        FontScaleHelper.applyFontScale(body, requireContext());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit event")
                .setView(body)
                .setPositiveButton("Save", (d, w) -> {
                    int checked = radioGroup.getCheckedRadioButtonId();
                    String newType;
                    if (checked == R.id.radio_food) newType = "FOOD";
                    else if (checked == R.id.radio_activity_food) newType = "ACTIVITY_FOOD";
                    else newType = "ACTIVITY";
                    eventViewModel.updateEvent(event.getId(), newType, descInput.getText().toString().trim());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


    }
