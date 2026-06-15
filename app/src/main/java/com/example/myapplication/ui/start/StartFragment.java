package com.example.myapplication.ui.start;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.FontScaleHelper;
import com.example.myapplication.R;
import com.example.myapplication.databinding.StartPageBinding;
import com.example.myapplication.ui.home.EventViewModel;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class StartFragment extends Fragment {
    EventViewModel eventViewModel;
    private Mode currentMode = Mode.START;
    private StartPageBinding binding;
    Map<String, String> login;
    private com.google.firebase.firestore.ListenerRegistration loginListener;

    FirebaseFirestore firestore;
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        firestore = FirebaseFirestore.getInstance();
        login = new HashMap<>();
        listenForLoginData();
        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);

        // Auto-login: check for saved session
        SharedPreferences prefs = requireContext().getSharedPreferences("circle_events_prefs", Context.MODE_PRIVATE);
        String savedCode = prefs.getString("saved_circle_code", null);
        String savedMode = prefs.getString("saved_mode", null);
        if (savedCode != null && savedMode != null) {
            eventViewModel.setCurrentCircleCode(savedCode);
            eventViewModel.setSelectedMode(Mode.valueOf(savedMode));
            eventViewModel.setUserName(prefs.getString("saved_name", ""));
            binding = StartPageBinding.inflate(inflater, container, false);
            binding.getRoot().post(() -> {
                if (isAdded()) {
                    Navigation.findNavController(binding.getRoot()).navigate(R.id.nav_home);
                }
            });
            return binding.getRoot();
        }

        binding = StartPageBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        setUpButtons();
        return root;
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

        if (name.isEmpty()) {
            binding.nameLayout.setError("Please enter your name");
            return;
        } else {
            binding.nameLayout.setError(null);
        }
        if (groupCode.isEmpty()) {
            binding.groupLayout.setError("Please enter a code");
            return;
        } else {
            binding.groupLayout.setError(null);
        }
        if (currentMode == Mode.JOIN || currentMode == Mode.ADMIN) {
            if (!login.containsKey(groupCode)) {
                binding.groupLayout.setError("Group does not exist");
                return;
            }
        }
        if (currentMode != Mode.JOIN && adminCode.isEmpty()) {
            binding.adminLayout.setError("Admin code required");
            return;
        } else {
            binding.adminLayout.setError(null);
        }
        if (currentMode == Mode.CREATE) {
            if (login.containsKey(groupCode)) {
                binding.groupLayout.setError("Group already exists");
                return;
            }
            Map<String, Object> newGroup = new HashMap<>();
            newGroup.put("adminCode", adminCode);

            firestore.collection("default").document(groupCode)
                    .set(newGroup)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Group Created"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error creating group", e));

            login.put(groupCode, adminCode);
        }

        if (currentMode == Mode.ADMIN) {
            String correctAdminCode = login.get(groupCode);
            if (!adminCode.equals(correctAdminCode)) {
                binding.adminLayout.setError("Incorrect admin code");
                return;
            }
        }

        // Set the current circle code in the ViewModel before navigating
        eventViewModel.setCurrentCircleCode(groupCode);
        eventViewModel.setUserName(name);

        // Save session for auto-login
        SharedPreferences savePrefs = requireContext().getSharedPreferences("circle_events_prefs", Context.MODE_PRIVATE);
        savePrefs.edit()
                .putString("saved_circle_code", groupCode)
                .putString("saved_mode", currentMode.name())
                .putString("saved_name", name)
                .apply();

        Navigation.findNavController(view)
                .navigate(R.id.nav_home);
    }
    private void listenForLoginData(){
        if (loginListener != null) {
            loginListener.remove();
        }
        loginListener = firestore.collection("default")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening for login data: ", error);
                        return;
                    }
                    if (snapshots != null) {
                        login.clear();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot document : snapshots) {
                            String group = document.getId();
                            String admin = document.getString("adminCode");
                            if (group != null && admin != null) {
                                login.put(group, admin);
                            }
                        }
                        Log.d(TAG, "Login map updated with " + login.size() + " groups.");
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loginListener != null) {
            loginListener.remove();
            loginListener = null;
        }
        binding = null;
    }
}
