package com.example.myapplication.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.FontScaleHelper;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentSettingsBinding;
import com.example.myapplication.ui.home.EventViewModel;
import com.example.myapplication.ui.home.ViewWindow;
import com.example.myapplication.ui.start.Mode;
import com.google.android.material.slider.Slider;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "circle_events_prefs";
    private FragmentSettingsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        float currentScale = FontScaleHelper.getFontScale(requireContext());

        // Initialize slider to saved value
        binding.sliderFontSize.setValue(currentScale);
        binding.textScalePercent.setText(Math.round(currentScale * 100) + "%");

        // Apply current scale to preview card
        updatePreview(currentScale);

        // Live preview as slider moves
        binding.sliderFontSize.addOnChangeListener((slider, value, fromUser) -> {
            binding.textScalePercent.setText(Math.round(value * 100) + "%");
            updatePreview(value);
        });

        // Persist only on release
        binding.sliderFontSize.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {}

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                FontScaleHelper.saveFontScale(requireContext(), slider.getValue());
            }
        });

        // Logout button
        binding.buttonLogout.setOnClickListener(v -> {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .remove("saved_circle_code")
                    .remove("saved_mode")
                    .apply();

            Navigation.findNavController(v).navigate(R.id.nav_start,
                    null,
                    new androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_start, true)
                            .build());
        });

        EventViewModel eventViewModel =
                new ViewModelProvider(requireActivity()).get(EventViewModel.class);
        Mode mode = eventViewModel.getSelectedMode().getValue();

        if (mode == Mode.ADMIN || mode == Mode.CREATE) {
            binding.viewWindowCard.setVisibility(View.VISIBLE);

            String[] windowLabels = getResources().getStringArray(R.array.view_window_labels);
            String[] windowValues = getResources().getStringArray(R.array.view_window_values);
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_list_item_1, windowLabels);
            binding.viewWindowDropdown.setAdapter(adapter);

            ViewWindow current = eventViewModel.getViewWindow().getValue();
            if (current == null) current = ViewWindow.DAY;
            for (int i = 0; i < windowValues.length; i++) {
                if (windowValues[i].equals(current.name())) {
                    binding.viewWindowDropdown.setText(windowLabels[i], false);
                    break;
                }
            }

            binding.viewWindowDropdown.setOnItemClickListener((parent, v, position, id) ->
                    eventViewModel.setViewWindow(ViewWindow.fromString(windowValues[position])));
        } else {
            binding.viewWindowCard.setVisibility(View.GONE);
        }
    }

    private void updatePreview(float scale) {
        FontScaleHelper.applyScaleToView(binding.previewCard, scale);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
