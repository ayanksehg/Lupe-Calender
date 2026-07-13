package com.calendar.lupe.ui.start;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.calendar.lupe.FontScaleHelper;
import com.calendar.lupe.R;
import com.calendar.lupe.databinding.StartPageBinding;
import com.calendar.lupe.ui.home.EventViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

public class StartFragment extends Fragment {
    EventViewModel eventViewModel;
    private Mode currentMode = Mode.START;
    private StartPageBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);


        SharedPreferences prefs = requireContext().getSharedPreferences("circle_events_prefs", Context.MODE_PRIVATE);
        String savedCode = prefs.getString("saved_circle_code", null);
        String savedMode = prefs.getString("saved_mode", null);
        if (savedCode != null && savedMode != null) {
            binding = StartPageBinding.inflate(inflater, container, false);
            eventViewModel.setCurrentCircleCode(savedCode);
            eventViewModel.setUserName(prefs.getString("saved_name", ""));
            resumeSession(savedCode, savedMode);
            return binding.getRoot();
        }

        binding = StartPageBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        setUpButtons();
        return root;
    }


    private void resumeSession(String savedCode, String savedMode) {
        Mode savedModeEnum;
        try {
            savedModeEnum = Mode.valueOf(savedMode);
        } catch (IllegalArgumentException e) {
            savedModeEnum = Mode.JOIN;
        }

        if (savedModeEnum == Mode.JOIN) {

            eventViewModel.setSelectedMode(Mode.JOIN);
            navigateHome();
            return;
        }


        final Mode requestedMode = savedModeEnum;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {

            eventViewModel.setSelectedMode(Mode.JOIN);
            navigateHome();
            return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(result -> {
                    Object claim = result.getClaims().get("admin");
                    Mode effective = savedCode.equals(claim) ? requestedMode : Mode.JOIN;
                    eventViewModel.setSelectedMode(effective);
                    navigateHome();
                })
                .addOnFailureListener(e -> {
                    eventViewModel.setSelectedMode(Mode.JOIN);
                    navigateHome();
                });
    }

    private void navigateHome() {
        if (binding == null) {
            return;
        }
        binding.getRoot().post(() -> {
            if (isAdded() && binding != null) {
                Navigation.findNavController(binding.getRoot()).navigate(R.id.nav_home);
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FontScaleHelper.applyFontScale(view, requireContext());
    }

    private void setUpButtons(){
        binding.buttonStart.setOnClickListener(v -> setMode(Mode.CREATE));
        binding.buttonUnc.setOnClickListener(v -> setMode(Mode.JOIN));
        binding.buttonAdmin.setOnClickListener(v -> setMode(Mode.ADMIN));
        binding.buttonEnter.setOnClickListener(v -> confirmAction(v));
        binding.privacyLink.setOnClickListener(v -> startActivity(
                new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://lupecalendar.com/privacy.html"))));
    }
    private void setMode(Mode mode) {
        currentMode = mode;
        eventViewModel.setSelectedMode(mode);
        resetButtonAppearance();
        binding.groupLayout.setVisibility(View.VISIBLE);
        binding.nameLayout.setVisibility(View.VISIBLE);
        binding.adminLayout.setVisibility(View.GONE);
        binding.buttonEnter.setVisibility(View.VISIBLE);
        int selectedColor = ContextCompat.getColor(requireContext(), R.color.watch_blue_dark);
        switch (mode) {
            case CREATE:
                binding.buttonStart.setBackgroundTintList(ColorStateList.valueOf(selectedColor));
                binding.adminLayout.setVisibility(View.VISIBLE);
                break;
            case JOIN:
                binding.buttonUnc.setBackgroundTintList(ColorStateList.valueOf(selectedColor));
                break;

            case ADMIN:
                binding.buttonAdmin.setStrokeColor(ColorStateList.valueOf(selectedColor));
                binding.buttonAdmin.setTextColor(selectedColor);
                binding.adminLayout.setVisibility(View.VISIBLE);
                break;
        }
    }
    private void resetButtonAppearance() {
        int defaultColor = ContextCompat.getColor(requireContext(), R.color.watch_blue);
        int textColor = ContextCompat.getColor(requireContext(), R.color.watch_blue_dark);
        binding.buttonStart.setBackgroundTintList(ColorStateList.valueOf(defaultColor));
        binding.buttonUnc.setBackgroundTintList(ColorStateList.valueOf(defaultColor));
        binding.buttonAdmin.setStrokeColor(ColorStateList.valueOf(defaultColor));
        binding.buttonAdmin.setTextColor(textColor);
    }
    private void confirmAction(View view) {
        String groupCode = binding.Group.getText().toString().trim();
        String adminCode = binding.Admin.getText().toString().trim();
        String name = binding.Name.getText().toString().trim();

        if (groupCode.isEmpty()) {
            binding.groupLayout.setError("Please enter a code");
            return;
        }
        binding.groupLayout.setError(null);

        if (currentMode == Mode.JOIN) {

            binding.adminLayout.setError(null);
            enterCircle(view, groupCode, name);
            return;
        }


        if (adminCode.isEmpty()) {
            binding.adminLayout.setError("Admin code required");
            return;
        }
        binding.adminLayout.setError(null);

        String function = currentMode == Mode.CREATE ? "createCircle" : "joinAsAdmin";
        Map<String, Object> payload = new HashMap<>();
        payload.put("circleCode", groupCode);
        payload.put("adminCode", adminCode);

        binding.buttonEnter.setEnabled(false);

        //Authentication is required. Anonymous sign-in (started in main activity) may not have been completed
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(res -> callCircleFunction(view, function, payload, groupCode, name))
                    .addOnFailureListener(e -> {
                        if (binding == null) {
                            return;
                        }
                        binding.buttonEnter.setEnabled(true);
                        binding.adminLayout.setError("Could not sign in: " + e.getMessage());
                    });
        } else {
            callCircleFunction(view, function, payload, groupCode, name);
        }
    }

    private void callCircleFunction(View view, String function, Map<String, Object> payload,
                                    String groupCode, String name) {
        FirebaseFunctions.getInstance()
                .getHttpsCallable(function)
                .call(payload)
                .addOnSuccessListener(r -> {

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        user.getIdToken(true)
                                .addOnCompleteListener(t -> enterCircle(view, groupCode, name));
                    } else {
                        enterCircle(view, groupCode, name);
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding == null) {
                        return;
                    }
                    binding.buttonEnter.setEnabled(true);
                    binding.adminLayout.setError(e.getMessage());
                });
    }

    private void enterCircle(View view, String groupCode, String name) {
        if (!isAdded() || binding == null) {
            return;
        }
        binding.buttonEnter.setEnabled(true);

     
        eventViewModel.setCurrentCircleCode(groupCode);
        eventViewModel.setSelectedMode(currentMode);
        eventViewModel.setUserName(name);

   
        SharedPreferences savePrefs = requireContext().getSharedPreferences("circle_events_prefs", Context.MODE_PRIVATE);
        savePrefs.edit()
                .putString("saved_circle_code", groupCode)
                .putString("saved_mode", currentMode.name())
                .putString("saved_name", name)
                .apply();

        Navigation.findNavController(view)
                .navigate(R.id.nav_home);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
