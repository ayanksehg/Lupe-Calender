package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        int leadMinutes = intent.getIntExtra("lead_minutes", 0);
        String contentText = title + (leadMinutes > 0
                ? " happens in " + leadMinutes + (leadMinutes == 1 ? " minute" : " minutes")
                : " is starting now");
        Notification notification =
                new NotificationCompat.Builder(context, "event_reminders")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Event Reminder")
                        .setContentText(contentText)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build();
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), notification);
        }

        String eventId = intent.getStringExtra("event_id");
        String recurrence = intent.getStringExtra("recurrence");
        String endDate = intent.getStringExtra("recurrence_end_date");
        int requestCode = intent.getIntExtra("request_code", eventId != null ? eventId.hashCode() : 0);

        java.util.ArrayList<String> excluded = intent.getStringArrayListExtra("excluded_dates");
        boolean mandatorySeries = intent.getBooleanExtra("mandatory_series", false);
        String mandatoryFrom = intent.getStringExtra("mandatory_from");
        java.util.ArrayList<String> mandatoryDates = intent.getStringArrayListExtra("mandatory_dates");
        boolean notifyOnly = intent.getBooleanExtra("notify_only_favorites", false);

        boolean recurring = recurrence != null && !"NONE".equals(recurrence) && !recurrence.isEmpty();

        if (recurring) {
            long rescheduled = nextFromFiredTime(context, eventId, System.currentTimeMillis(),
                    recurrence, endDate, leadMinutes, excluded,
                    notifyOnly, mandatorySeries, mandatoryFrom, mandatoryDates);
            if (rescheduled > 0) {
                rescheduleExact(context, intent, requestCode, rescheduled);
                return; // keep the Firestore doc alive for the next occurrence
            }
        }

        if (eventId != null) {
            FirebaseFirestore.getInstance().collection("events").document(eventId).delete();
        }
    }


    private long nextFromFiredTime(Context context, String eventId, long firedTime, String recurrence,
                                   String endDate, int leadMinutes, java.util.List<String> excluded,
                                   boolean notifyOnly, boolean mandatorySeries, String mandatoryFrom,
                                   java.util.List<String> mandatoryDates) {
        long endLimit = endLimitMillis(endDate);
        long leadMs = leadMinutes * 60_000L;
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(firedTime);
        int guard = 0;
        while (true) {
            switch (recurrence) {
                case "EVERY_OTHER_DAY": c.add(Calendar.DAY_OF_MONTH, 2); break;
                case "WEEKLY":  c.add(Calendar.DAY_OF_MONTH, 7); break;
                case "MONTHLY": c.add(Calendar.MONTH, 1); break;
                case "YEARLY":  c.add(Calendar.YEAR, 1); break;
                case "DAILY":
                default:        c.add(Calendar.DAY_OF_MONTH, 1); break;
            }
            long occurrence = c.getTimeInMillis() + leadMs;
            if (occurrence > endLimit) return -1;
            if (++guard > 100000) return -1;
            String occDate = sdf.format(new Date(occurrence));
            if (excluded != null && excluded.contains(occDate)) continue;
            if (!qualifies(context, eventId, occDate, notifyOnly, mandatorySeries,
                    mandatoryFrom, mandatoryDates)) continue;
            return c.getTimeInMillis();
        }
    }

    /** With notify-only on, an occurrence fires only if mandatory or favorited (per device). */
    private boolean qualifies(Context context, String eventId, String occDate, boolean notifyOnly,
                              boolean mandatorySeries, String mandatoryFrom,
                              java.util.List<String> mandatoryDates) {
        if (!notifyOnly) return true;
        if (isMandatory(occDate, mandatorySeries, mandatoryFrom, mandatoryDates)) return true;
        return FavoritesHelper.isFavorite(context, eventId, occDate);
    }

    private boolean isMandatory(String occDate, boolean mandatorySeries, String mandatoryFrom,
                                java.util.List<String> mandatoryDates) {
        if (mandatorySeries) return true;
        if (mandatoryDates != null && occDate != null && mandatoryDates.contains(occDate)) return true;
        if (mandatoryFrom != null && occDate != null) {
            long from = millisOf(mandatoryFrom);
            long when = millisOf(occDate);
            if (from >= 0 && when >= 0 && when >= from) return true;
        }
        return false;
    }

    private long millisOf(String date) {
        try {
            Date d = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(date.trim());
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }


    private long endLimitMillis(String endDate) {
        if (endDate == null || endDate.trim().isEmpty()) return Long.MAX_VALUE;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
            Date end = sdf.parse(endDate.trim());
            if (end == null) return Long.MAX_VALUE;
            Calendar ec = Calendar.getInstance();
            ec.setTime(end);
            ec.set(Calendar.HOUR_OF_DAY, 23);
            ec.set(Calendar.MINUTE, 59);
            ec.set(Calendar.SECOND, 59);
            ec.set(Calendar.MILLISECOND, 999);
            return ec.getTimeInMillis();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private void rescheduleExact(Context context, Intent firedIntent, int requestCode, long time) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtras(firedIntent);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        } else {
            // No exact-alarm permission: fall back to inexact so the reminder still fires (possibly late).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
        }
    }

}
