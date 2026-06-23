package com.calendar.lupe.ui.slideshow;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.calendar.lupe.FontScaleHelper;
import com.calendar.lupe.MainActivity;
import com.calendar.lupe.R;
import com.calendar.lupe.databinding.FragmentSlideshowBinding;
import com.calendar.lupe.ui.home.Event;
import com.calendar.lupe.ui.home.EventViewModel;
import com.calendar.lupe.ui.start.Mode;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SlideshowFragment extends Fragment {

    private FragmentSlideshowBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        EventViewModel eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);
        binding = FragmentSlideshowBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final Button create = binding.createCard;
        final EditText title = binding.titleText;
        final EditText date = binding.editTextDate;
        final EditText time = binding.editTextTime;

        if(eventViewModel.getSelectedMode().getValue() == Mode.JOIN){
            binding.createSection.setVisibility(View.GONE);
            binding.textSlideshow.setText("Calendar Settings");
        }
        else{
            String[] recurLabels = getResources().getStringArray(R.array.recurrence_labels);
            String[] recurValues = getResources().getStringArray(R.array.recurrence_values);
            binding.recurrenceDropdown.setAdapter(nonFilteringAdapter(recurLabels));
            binding.recurrenceDropdown.setText(recurLabels[0], false); // "Does not repeat"

            binding.recurrenceDropdown.setOnItemClickListener((parent, view, position, id) -> {
                boolean repeats = position != 0; // index 0 == NONE
                binding.repeatUntilLayout.setVisibility(repeats ? View.VISIBLE : View.GONE);
            });

            String[] typeLabels = getResources().getStringArray(R.array.event_type_labels);
            String[] typeValues = getResources().getStringArray(R.array.event_type_values);
            binding.typeDropdown.setAdapter(nonFilteringAdapter(typeLabels));
            binding.typeDropdown.setText(typeLabels[0], false); // "Activity"

            create.setOnClickListener(v -> {
                String titleText = title.getText().toString();
                String dateText = date.getText().toString();
                String timeText = time.getText().toString();
                long eventTime = parseDateTimeToMillis(dateText, timeText);

                if (eventTime == -1) {
                    date.setError("Invalid date/time");
                    return;
                }

                // Resolve recurrence label -> value
                String selectedLabel = binding.recurrenceDropdown.getText().toString();
                String recurrenceValue = "NONE";
                for (int i = 0; i < recurLabels.length; i++) {
                    if (recurLabels[i].equals(selectedLabel)) {
                        recurrenceValue = recurValues[i];
                        break;
                    }
                }
                String repeatUntil = binding.editTextRepeatUntil.getText().toString().trim();
                if ("NONE".equals(recurrenceValue)) repeatUntil = null;

                String selectedTypeLabel = binding.typeDropdown.getText().toString();
                String typeValue = "ACTIVITY";
                for (int i = 0; i < typeLabels.length; i++) {
                    if (typeLabels[i].equals(selectedTypeLabel)) {
                        typeValue = typeValues[i];
                        break;
                    }
                }
                String descriptionText = binding.editTextDescription.getText().toString().trim();

                Event e = new Event(
                        titleText,
                        descriptionText,
                        dateText,
                        timeText,
                        "location",
                        eventViewModel.getCurrentCircleCode().getValue()
                );
                e.recurrence = recurrenceValue;
                e.recurrenceEndDate = (repeatUntil != null && repeatUntil.isEmpty()) ? null : repeatUntil;
                e.type = typeValue;
                e.importance = binding.checkboxMandatory.isChecked();

                eventViewModel.addEvent(e);

                int leadMinutes = eventViewModel.getNotificationLeadMinutesValue();
                ((MainActivity) requireActivity())
                        .scheduleEventNotification(
                                eventTime - leadMinutes * 60_000L,
                                titleText,
                                e.getId().hashCode(),
                                e.getId(),
                                e.recurrence,
                                e.recurrenceEndDate,
                                leadMinutes
                        );

                // Clear fields after creation
//                title.setText("");
//                date.setText("");
//                time.setText("");
                binding.recurrenceDropdown.setText(recurLabels[0], false);
                binding.editTextRepeatUntil.setText("");
                binding.repeatUntilLayout.setVisibility(View.GONE);
                binding.typeDropdown.setText(typeLabels[0], false);
                binding.editTextDescription.setText("");
                binding.checkboxMandatory.setChecked(false);
                Toast.makeText(getContext(), "Event created!", Toast.LENGTH_SHORT).show();
            });
        }


        final EditText calendarInput = binding.editTextText;
        final TextInputLayout calendarInputLayout = binding.tilEventsCalendar;
        final Button submit = binding.button;

        // Clear the error as soon as the user starts typing again.
        calendarInput.setOnFocusChangeListener((v, hasFocus) -> calendarInputLayout.setError(null));

        submit.setOnClickListener(v -> {
            calendarInputLayout.setError(null);
            String calendarId = calendarInput.getText().toString().trim();

            if (calendarId.isEmpty()) {
                calendarInputLayout.setError("Enter a Calendar ID");
                return;
            }

            String circleCode = eventViewModel.getCurrentCircleCode().getValue();
            if (circleCode == null || circleCode.isEmpty()) {
                Toast.makeText(getContext(),
                        "No circle is active — re-join your circle and try again.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("calendarId", calendarId);
            FirebaseFirestore.getInstance().collection("circles").document(circleCode)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        calendarInput.setText("");
                        Toast.makeText(getContext(), "Calendar linked!", Toast.LENGTH_SHORT).show();

                        eventViewModel.fetchGoogleCalendarEvents(calendarId);
                        refreshLinkedCalendarStatus(circleCode);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("LinkCalendar", "Failed to save calendarId for " + circleCode, e);
                        calendarInputLayout.setError("Couldn't save — check your connection");
                        Toast.makeText(getContext(),
                                "Couldn't link calendar: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        });

        final EditText foodCalendarInput = binding.editTextFoodCalendar;
        final TextInputLayout foodCalendarInputLayout = binding.tilFoodCalendar;
        final Button submitFood = binding.buttonFoodCalendar;

        foodCalendarInput.setOnFocusChangeListener((v, hasFocus) -> foodCalendarInputLayout.setError(null));

        submitFood.setOnClickListener(v -> {
            foodCalendarInputLayout.setError(null);
            String foodCalendarId = foodCalendarInput.getText().toString().trim();
            if (foodCalendarId.isEmpty()) {
                foodCalendarInputLayout.setError("Enter a Calendar ID");
                return;
            }
            String circleCode = eventViewModel.getCurrentCircleCode().getValue();
            if (circleCode == null || circleCode.isEmpty()) {
                Toast.makeText(getContext(),
                        "No circle is active — re-join your circle and try again.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("mealCalendarId", foodCalendarId);
            FirebaseFirestore.getInstance().collection("circles").document(circleCode)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        foodCalendarInput.setText("");
                        Toast.makeText(getContext(), "Food calendar linked!", Toast.LENGTH_SHORT).show();
                        eventViewModel.fetchGoogleCalendarEvents(foodCalendarId, "FOOD");
                        refreshLinkedCalendarStatus(circleCode);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("LinkCalendar", "Failed to save mealCalendarId for " + circleCode, e);
                        foodCalendarInputLayout.setError("Couldn't save — check your connection");
                        Toast.makeText(getContext(),
                                "Couldn't link food calendar: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        });

        // Show whatever is currently linked for this circle, below the form.
        refreshLinkedCalendarStatus(eventViewModel.getCurrentCircleCode().getValue());

        return root;
    }

    /**
     * Reads the circle's linked Google Calendar IDs from Firestore and renders them in the
     * status TextView below the link form. Safe to call after the view is gone (no-ops).
     */
    private void refreshLinkedCalendarStatus(String circleCode) {
        if (binding == null) return;
        final TextView status = binding.linkedCalendarStatus;

        if (circleCode == null || circleCode.isEmpty()) {
            status.setText("No calendar linked yet.");
            return;
        }

        FirebaseFirestore.getInstance().collection("circles").document(circleCode).get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    String calendarId = doc.exists() ? doc.getString("calendarId") : null;
                    String mealCalendarId = doc.exists() ? doc.getString("mealCalendarId") : null;

                    StringBuilder sb = new StringBuilder();
                    if (calendarId != null && !calendarId.isEmpty()) {
                        sb.append("Events: ").append(calendarId);
                    }
                    if (mealCalendarId != null && !mealCalendarId.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append("Food: ").append(mealCalendarId);
                    }

                    if (sb.length() == 0) {
                        status.setText("No calendar linked yet.");
                    } else {
                        status.setText("Currently linked\n" + sb);
                    }
                    FontScaleHelper.applyFontScale(status, requireContext());
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        binding.linkedCalendarStatus.setText("Couldn't load linked calendar.");
                    }
                });
    }

    @Override
    public void onViewCreated(@NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FontScaleHelper.applyFontScale(view, requireContext());
    }

    /**
     * ArrayAdapter for an exposed dropdown (MaterialAutoCompleteTextView) whose filter is a no-op:
     * it always publishes the full item list. Without this, the dropdown filters by the field's
     * current text, so once a value is selected (or restored after navigating back to this screen)
     * reopening the menu hides every non-matching option.
     */
    private android.widget.ArrayAdapter<String> nonFilteringAdapter(String[] items) {
        return new android.widget.ArrayAdapter<String>(
                requireContext(), android.R.layout.simple_list_item_1, items) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults fr = new FilterResults();
                        fr.values = java.util.Arrays.asList(items);
                        fr.count = items.length;
                        return fr;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
