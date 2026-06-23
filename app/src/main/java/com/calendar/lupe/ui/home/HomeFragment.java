package com.calendar.lupe.ui.home;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.calendar.lupe.FontScaleHelper;
import com.calendar.lupe.R;
import com.calendar.lupe.databinding.FragmentHomeBinding;

import java.time.LocalTime;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private EventViewModel eventViewModel;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);

        String circle = eventViewModel.getCurrentCircleCode().getValue();
        String name = eventViewModel.getUserName();


        String greeting = "Good Morning";
        LocalTime currentTime = LocalTime.now();
        LocalTime fiveAM = LocalTime.of(5, 0);
        LocalTime twelveAM = LocalTime.of(0, 0);
        LocalTime ninePM = LocalTime.of(21, 30);
        LocalTime fivePM = LocalTime.of(17, 0);

        if (currentTime.isAfter(fiveAM) && currentTime.isBefore(twelveAM)) greeting = "Good Morning";
        else if (currentTime.isAfter(twelveAM) && currentTime.isBefore(ninePM)) greeting = "Good Afternoon";
        else if (currentTime.isBefore(ninePM) && currentTime.isAfter(fivePM)) greeting = "Good Evening";
        else greeting = "Good Night";

        binding.textWelcome.setText(greeting + (name == null || name.isEmpty() ? "" : " " + name)+ "! Welcome to " + (circle == null ? "" : circle));






        binding.buttonEvents.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_calendar_events));
        binding.buttonFood.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_calendar_food));
        binding.buttonAll.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_calendar_all));

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FontScaleHelper.applyFontScale(view, requireContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
