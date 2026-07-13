package com.calendar.lupe.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.calendar.lupe.FontScaleHelper;
import com.calendar.lupe.MainActivity;
import com.calendar.lupe.R;
import com.calendar.lupe.databinding.EventcardBinding;
import com.calendar.lupe.databinding.FragmentCalendarBinding;
import com.calendar.lupe.ui.start.Mode;

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
        eventViewModel.setCalendarFilter(CalendarFilter.fromMode(mode));
        binding.textHome.setText(titleForMode(mode));

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

        eventViewModel.getRawEvents().observe(getViewLifecycleOwner(), rawEvents -> rescheduleAll());
        eventViewModel.getGoogleEvents().observe(getViewLifecycleOwner(), google -> rescheduleAll());

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
            eventViewModel.setCalendarFilter(CalendarFilter.fromMode(mode));
            eventViewModel.refreshGoogleCalendarEvents();
        }
    }

    private String titleForMode(String mode) {
        if ("FOOD".equals(mode)) return "Food";
        if ("ALL".equals(mode)) return "All Events";
        return "Activities";
    }


    private static final int MAX_ALARMS = 200;

    private static class Candidate {
        final long alarmTime;
        final Event event;
        final java.util.ArrayList<String> excluded;
        final boolean google;
        Candidate(long alarmTime, Event event, java.util.ArrayList<String> excluded, boolean google) {
            this.alarmTime = alarmTime;
            this.event = event;
            this.excluded = excluded;
            this.google = google;
        }
    }

    /**
     * Single scheduling pass over Firestore + Google events. Cancels each event's alarm first,
     * then schedules only the soonest {@link #MAX_ALARMS} qualifying ones (Android's per-app alarm
     * limit). The screen is reopened/refreshed often, so later events are scheduled as they approach.
     */
    private void rescheduleAll() {
        if (eventViewModel == null) return;
        long now = System.currentTimeMillis();
        boolean notifyOnly = com.calendar.lupe.FavoritesHelper.isNotifyOnlyFavorites(requireContext());
        int leadMinutes = eventViewModel.getNotificationLeadMinutesValue();
        long leadMs = leadMinutes * 60_000L;
        MainActivity activity = (MainActivity) requireActivity();

        List<Candidate> candidates = new ArrayList<>();

        List<Event> raw = eventViewModel.getRawEvents().getValue();
        if (raw != null) {
            for (Event e : raw) {
                if (e.circleCode != null && e.circleCode.equals("google_calendar")) continue;
                activity.cancelEventNotification(e.getId().hashCode());
                java.util.ArrayList<String> excluded = e.excludedDates == null
                        ? null : new java.util.ArrayList<>(e.excludedDates);
                java.util.Set<String> excludedSet = excluded == null ? null : new java.util.HashSet<>(excluded);
                long next = nextQualifyingOccurrence(e, now, excludedSet, notifyOnly);
                if (next > 0) candidates.add(new Candidate(next - leadMs, e, excluded, false));
            }
        }

        List<Event> google = eventViewModel.getGoogleEvents().getValue();
        if (google != null) {
            for (Event e : google) {
                activity.cancelEventNotification(e.getId().hashCode());
                long t = parseDateTimeToMillis(e.date, e.time);
                if (t <= now) continue;
                boolean fav = com.calendar.lupe.FavoritesHelper.isFavorite(
                        requireContext(), e.getId(), e.date);
                if (notifyOnly && !e.importance && !fav) continue;
                candidates.add(new Candidate(t - leadMs, e, null, true));
            }
        }

        Collections.sort(candidates, Comparator.comparingLong(c -> c.alarmTime));
        int limit = Math.min(candidates.size(), MAX_ALARMS);
        for (int i = 0; i < limit; i++) {
            Candidate c = candidates.get(i);
            Event e = c.event;
            java.util.ArrayList<String> mandatoryDates = e.mandatoryDates == null
                    ? null : new java.util.ArrayList<>(e.mandatoryDates);
            activity.scheduleEventNotification(
                    c.alarmTime, e.title, e.getId().hashCode(), e.getId(),
                    c.google || e.recurrence == null ? "NONE" : e.recurrence,
                    c.google ? null : e.recurrenceEndDate, leadMinutes, c.excluded,
                    e.importance, c.google ? null : e.mandatoryFrom,
                    c.google ? null : mandatoryDates, notifyOnly);
        }
    }

    private long nextQualifyingOccurrence(Event e, long now, java.util.Set<String> excludedSet,
                                          boolean notifyOnly) {
        long after = now;
        for (int i = 0; i < 1000; i++) {
            long next = RecurrenceExpander.nextOccurrenceAfter(
                    e.date, e.time, e.recurrence, e.recurrenceEndDate, after, excludedSet);
            if (next <= 0) return -1;
            if (!notifyOnly) return next;
            String d = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(new Date(next));
            if (e.isMandatoryOn(d)
                    || com.calendar.lupe.FavoritesHelper.isFavorite(requireContext(), e.getId(), d)) {
                return next;
            }
            after = next;
        }
        return -1;
    }

    private String recurrenceLabel(String recurrence) {
        if (recurrence == null || "NONE".equals(recurrence) || recurrence.isEmpty()) return null;
        switch (recurrence) {
            case "DAILY":           return "Repeats daily";
            case "EVERY_OTHER_DAY": return "Repeats every other day";
            case "WEEKLY":          return "Repeats weekly";
            case "MONTHLY":         return "Repeats monthly";
            case "YEARLY":          return "Repeats yearly";
            default:                return null;
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
        badgeBg.setColor(0x33FFFFFF);
        eventcardBinding.textTypeBadge.setBackground(badgeBg);

        eventcardBinding.textTitle.setText(event.title);
        eventcardBinding.textDate.setText(event.date);
        eventcardBinding.textTime.setText(com.calendar.lupe.TimeFormat.format(event.time));

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
            btnDelete.setOnClickListener(v -> showDeleteDialog(event, eventViewModel));
        }else{
            btnDelete.setVisibility(View.GONE);
        }

        com.google.android.material.button.MaterialButton btnEdit = eventcardBinding.btnEdit;


        TextView marker = eventcardBinding.favButton;
        if ("All Day".equals(event.time)) {
            marker.setVisibility(View.GONE);
        } else if (event.isMandatoryOn(event.date)) {
            marker.setVisibility(View.VISIBLE);
            marker.setText("✱");
            marker.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.Goldenrod));
            marker.setClickable(false);
            marker.setOnClickListener(null);
            marker.setContentDescription("Mandatory");
        } else {
            marker.setVisibility(View.VISIBLE);
            marker.setClickable(true);
            marker.setContentDescription("Favorite");
            refreshFavMarker(event, marker);
            marker.setOnClickListener(v -> onFavoriteTap(event, marker));
        }

        if (mode == Mode.ADMIN && !"All Day".equals(event.time)) {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v -> showEditDialog(event, eventViewModel));
        } else {
            btnEdit.setVisibility(View.GONE);
        }

        container.addView(eventcardBinding.getRoot());
    }

    private boolean isRecurring(Event event) {
        return event.recurrence != null && !"NONE".equals(event.recurrence)
                && !event.recurrence.isEmpty();
    }

    /** Delete flow: simple confirm for one-offs, a three-way chooser for recurring series. */
    private void showDeleteDialog(Event event, EventViewModel eventViewModel) {
        int requestCode = event.getId().hashCode();
        MainActivity activity = (MainActivity) requireActivity();

        if (!isRecurring(event)) {
            showThemedDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete this event?")
                    .setMessage(event.title)
                    .setPositiveButton("Delete", (d, w) -> {
                        eventViewModel.deleteEvent(event);
                        activity.cancelEventNotification(requestCode);
                    })
                    .setNegativeButton("Cancel", null));
            return;
        }

        String[] options = {
                "Just this event",
                "This and all future events",
                "All events in the series"
        };
        showThemedDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete recurring event")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: eventViewModel.deleteOccurrence(event.getId(), event.date); break;
                        case 1: eventViewModel.deleteThisAndForward(event.getId(), event.date); break;
                        default: eventViewModel.deleteEvent(event); break;
                    }

                    activity.cancelEventNotification(requestCode);
                })
                .setNegativeButton("Cancel", null));
    }

    private void showThemedDialog(com.google.android.material.dialog.MaterialAlertDialogBuilder builder) {
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                FontScaleHelper.applyFontScale(dialog.getWindow().getDecorView(), requireContext());
            }
        });
        dialog.show();
    }
    private void refreshFavMarker(Event event, TextView marker) {
        boolean fav = com.calendar.lupe.FavoritesHelper.isFavorite(
                requireContext(), event.getId(), event.date);
        marker.setText(fav ? "★" : "☆");
        marker.setTextColor(androidx.core.content.ContextCompat.getColor(
                requireContext(), fav ? R.color.Goldenrod : R.color.white));
    }

    /** Favorite is per-device. One-off toggles; recurring offers the 3 scopes (off clears series). */
    private void onFavoriteTap(Event event, TextView marker) {
        android.content.Context ctx = requireContext();
        boolean fav = com.calendar.lupe.FavoritesHelper.isFavorite(ctx, event.getId(), event.date);

        if (!isRecurring(event)) {
            if (fav) com.calendar.lupe.FavoritesHelper.removeFavoriteSeries(ctx, event.getId());
            else com.calendar.lupe.FavoritesHelper.addFavorite(
                    ctx, event.getId(), event.date, OccurrenceScope.INSTANCE);
            refreshFavMarker(event, marker);
            rescheduleAll();
            return;
        }

        if (fav) {
            showThemedDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Remove favorite?")
                    .setMessage(event.title)
                    .setPositiveButton("Remove", (d, w) -> {
                        com.calendar.lupe.FavoritesHelper.removeFavoriteSeries(ctx, event.getId());
                        refreshFavMarker(event, marker);
                        rescheduleAll();
                    })
                    .setNegativeButton("Cancel", null));
            return;
        }

        String[] options = {
                "Just this event",
                "This and all future events",
                "All events in the series"
        };
        showThemedDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Add to favorites")
                .setItems(options, (d, which) -> {
                    OccurrenceScope scope = which == 0 ? OccurrenceScope.INSTANCE
                            : which == 1 ? OccurrenceScope.FORWARD : OccurrenceScope.SERIES;
                    com.calendar.lupe.FavoritesHelper.addFavorite(ctx, event.getId(), event.date, scope);
                    refreshFavMarker(event, marker);
                    rescheduleAll();
                })
                .setNegativeButton("Cancel", null));
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

        com.google.android.material.switchmaterial.SwitchMaterial switchMandatory =
                body.findViewById(R.id.switch_mandatory);
        View scopeGroup = body.findViewById(R.id.mandatory_scope_group);
        android.widget.RadioGroup radioScope = body.findViewById(R.id.radio_scope);
        boolean recurring = isRecurring(event);
        boolean isGoogle = event.circleCode != null && event.circleCode.equals("google_calendar");


        if (isGoogle) {
            body.findViewById(R.id.label_type).setVisibility(View.GONE);
            radioGroup.setVisibility(View.GONE);
            body.findViewById(R.id.til_description).setVisibility(View.GONE);
        }

        switchMandatory.setChecked(event.isMandatoryOn(event.date));

        boolean scopeApplies = recurring && !isGoogle;
        scopeGroup.setVisibility(scopeApplies && switchMandatory.isChecked() ? View.VISIBLE : View.GONE);
        switchMandatory.setOnCheckedChangeListener((b, isChecked) ->
                scopeGroup.setVisibility(scopeApplies && isChecked ? View.VISIBLE : View.GONE));

        FontScaleHelper.applyFontScale(body, requireContext());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(isGoogle ? "Mark mandatory" : "Edit event")
                .setView(body)
                .setPositiveButton("Save", (d, w) -> {
                    if (isGoogle) {
                        eventViewModel.setGoogleMandatory(event.getId(), switchMandatory.isChecked());
                        return;
                    }
                    int checked = radioGroup.getCheckedRadioButtonId();
                    String newType;
                    if (checked == R.id.radio_food) newType = "FOOD";
                    else if (checked == R.id.radio_activity_food) newType = "ACTIVITY_FOOD";
                    else newType = "ACTIVITY";
                    eventViewModel.updateEvent(event.getId(), newType, descInput.getText().toString().trim());

                    if (!switchMandatory.isChecked()) {
                        eventViewModel.clearMandatory(event.getId());
                    } else if (!recurring) {
                        eventViewModel.setMandatory(event.getId(), event.date, OccurrenceScope.SERIES);
                    } else {
                        int s = radioScope.getCheckedRadioButtonId();
                        OccurrenceScope scope = s == R.id.radio_scope_instance ? OccurrenceScope.INSTANCE
                                : s == R.id.radio_scope_forward ? OccurrenceScope.FORWARD
                                : OccurrenceScope.SERIES;
                        eventViewModel.setMandatory(event.getId(), event.date, scope);
                    }
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
