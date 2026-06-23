package com.calendar.lupe;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.calendar.lupe.ui.home.EventViewModel;
import com.calendar.lupe.ui.start.Mode;
import com.google.android.material.navigation.NavigationView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.calendar.lupe.databinding.ActivityMainBinding;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private EventViewModel eventViewModel;
    FirebaseFirestore firestore;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                    .addOnFailureListener(e -> android.util.Log.e("Auth", "Anon sign-in failed", e));
        }
        firestore = FirebaseFirestore.getInstance();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        requestNotificationPermission();
        requestExactAlarmPermission();
        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_calendar_events, R.id.nav_calendar_food,
                R.id.nav_calendar_all, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(MainActivity.this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {
                    Mode mode = eventViewModel.getSelectedMode().getValue();
                    int id = destination.getId();
                    boolean isCalendar = id == R.id.nav_calendar_events
                            || id == R.id.nav_calendar_food
                            || id == R.id.nav_calendar_all;
                    boolean isMenu = id == R.id.nav_home;

                    if (mode == Mode.JOIN && (isMenu || isCalendar)) {
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                        navigationView.setVisibility(View.GONE);
                        if (getSupportActionBar() != null) {
                            // Up only on the calendar screens (to return to the menu); none on the menu itself.
                            getSupportActionBar().setDisplayHomeAsUpEnabled(isCalendar);
                            getSupportActionBar().setHomeButtonEnabled(isCalendar);
                        }
                    } else {
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                        navigationView.setVisibility(View.VISIBLE);
                    }
                }
        );

        createNotificationChannel();

    }
    private static final String CHANNEL_ID = "event_reminders";
    private void createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Event Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for scheduled events");
            channel.enableVibration(true);
            channel.enableLights(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    private void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
            if(ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS)!= PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1);
            }
        }
    }
    private void requestExactAlarmPermission(){
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }
    public void scheduleEventNotification(long time, String title, int requestCode, String eventId) {
        scheduleEventNotification(time, title, requestCode, eventId, "NONE", null, 0);
    }

    public void scheduleEventNotification(long time, String title, int requestCode, String eventId,
                                          String recurrence, String recurrenceEndDate) {
        scheduleEventNotification(time, title, requestCode, eventId, recurrence, recurrenceEndDate, 0);
    }

    public void scheduleEventNotification(long time, String title, int requestCode, String eventId,
                                          String recurrence, String recurrenceEndDate, int leadMinutes) {
        scheduleEventNotification(time, title, requestCode, eventId, recurrence, recurrenceEndDate,
                leadMinutes, null);
    }

    public void scheduleEventNotification(long time, String title, int requestCode, String eventId,
                                          String recurrence, String recurrenceEndDate, int leadMinutes,
                                          java.util.ArrayList<String> excludedDates) {
        scheduleEventNotification(time, title, requestCode, eventId, recurrence, recurrenceEndDate,
                leadMinutes, excludedDates, false, null, null, false);
    }

    public void scheduleEventNotification(long time, String title, int requestCode, String eventId,
                                          String recurrence, String recurrenceEndDate, int leadMinutes,
                                          java.util.ArrayList<String> excludedDates,
                                          boolean mandatorySeries, String mandatoryFrom,
                                          java.util.ArrayList<String> mandatoryDates,
                                          boolean notifyOnlyFavorites) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("event_id", eventId);
        intent.putExtra("recurrence", recurrence);
        intent.putExtra("recurrence_end_date", recurrenceEndDate);
        intent.putExtra("request_code", requestCode);
        intent.putExtra("lead_minutes", leadMinutes);
        intent.putStringArrayListExtra("excluded_dates", excludedDates);
        intent.putExtra("mandatory_series", mandatorySeries);
        intent.putExtra("mandatory_from", mandatoryFrom);
        intent.putStringArrayListExtra("mandatory_dates", mandatoryDates);
        intent.putExtra("notify_only_favorites", notifyOnlyFavorites);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        } else {
            // No exact-alarm permission: fall back to inexact so the reminder still fires (possibly late).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        }
    }


    public void cancelEventNotification(int requestCode) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.nav_settings);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        Mode mode = eventViewModel.getSelectedMode().getValue();
        if (mode == Mode.JOIN) {

            return navController.popBackStack() || super.onSupportNavigateUp();
        }
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
