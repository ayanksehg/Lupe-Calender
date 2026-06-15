package com.example.myapplication.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.FontScaleHelper;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private EventViewModel eventViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);

        String circle = eventViewModel.getCurrentCircleCode().getValue();
        String name = eventViewModel.getUserName();
        binding.textWelcome.setText("Welcome to " + (circle == null ? "" : circle)
                + (name == null || name.isEmpty() ? "" : " " + name) + "!");

        binding.buttonEvents.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_calendar_events));
        binding.buttonFood.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_calendar_food));

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
