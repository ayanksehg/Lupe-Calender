package com.example.myapplication.ui.slideshow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.FontScaleHelper;
import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentSlideshowBinding;
import com.example.myapplication.ui.home.Event;
import com.example.myapplication.ui.home.EventViewModel;
import com.example.myapplication.ui.start.Mode;
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
            android.widget.ArrayAdapter<String> recurAdapter = new android.widget.ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_list_item_1, recurLabels);
            binding.recurrenceDropdown.setAdapter(recurAdapter);
            binding.recurrenceDropdown.setText(recurLabels[0], false); // "Does not repeat"

            binding.recurrenceDropdown.setOnItemClickListener((parent, view, position, id) -> {
                boolean repeats = position != 0; // index 0 == NONE
                binding.repeatUntilLayout.setVisibility(repeats ? View.VISIBLE : View.GONE);
            });

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

                Event e = new Event(
                        titleText,
                        "description",
                        dateText,
                        timeText,
                        "location",
                        eventViewModel.getCurrentCircleCode().getValue()
                );
                e.recurrence = recurrenceValue;
                e.recurrenceEndDate = (repeatUntil != null && repeatUntil.isEmpty()) ? null : repeatUntil;

                eventViewModel.addEvent(e);

                ((MainActivity) requireActivity())
                        .scheduleEventNotification(
                                eventTime,
                                titleText,
                                e.getId().hashCode(),
                                e.getId(),
                                e.recurrence,
                                e.recurrenceEndDate
                        );

                // Clear fields after creation
                title.setText("");
                date.setText("");
                time.setText("");
                binding.recurrenceDropdown.setText(recurLabels[0], false);
                binding.editTextRepeatUntil.setText("");
                binding.repeatUntilLayout.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Event created!", Toast.LENGTH_SHORT).show();
            });
        }


        final EditText calendarInput = binding.editTextText;
        final Button submit = binding.button;

        submit.setOnClickListener(v -> {
            String calendarId = calendarInput.getText().toString().trim();

            if(calendarId.isEmpty()){
                calendarInput.setError("Enter Calendar ID");
                return;
            }

            String circleCode=eventViewModel.getCurrentCircleCode().getValue();
            Map<String, Object> data = new HashMap<>();
            data.put("calendarId", calendarId);
            FirebaseFirestore.getInstance().collection("circles").document(circleCode).set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        calendarInput.setText("");
                        Toast.makeText(getContext(), "Calendar linked!", Toast.LENGTH_SHORT).show();
                        // Immediately fetch Google Calendar events so they appear in HomeFragment
                        eventViewModel.fetchGoogleCalendarEvents(calendarId);
                    })
                    .addOnFailureListener(e -> {
                        calendarInput.setError("Invalid Calendar ID");
                    });
        });

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FontScaleHelper.applyFontScale(view, requireContext());
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
