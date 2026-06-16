package com.example.myapplication;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

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

        boolean recurring = recurrence != null && !"NONE".equals(recurrence) && !recurrence.isEmpty();

        if (recurring) {
            long rescheduled = nextFromFiredTime(System.currentTimeMillis(), recurrence, endDate);
            if (rescheduled > 0) {
                rescheduleExact(context, intent, requestCode, rescheduled);
                return; // keep the Firestore doc alive for the next occurrence
            }
        }

        if (eventId != null) {
            FirebaseFirestore.getInstance().collection("events").document(eventId).delete();
        }
    }

    /** One recurrence step after the fired time, or -1 if past the (inclusive) end date. */
    private long nextFromFiredTime(long firedTime, String recurrence, String endDate) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(firedTime);
        switch (recurrence) {
            case "WEEKLY":  c.add(Calendar.DAY_OF_MONTH, 7); break;
            case "MONTHLY": c.add(Calendar.MONTH, 1); break;
            case "YEARLY":  c.add(Calendar.YEAR, 1); break;
            case "DAILY":
            default:        c.add(Calendar.DAY_OF_MONTH, 1); break;
        }
        long next = c.getTimeInMillis();
        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
                Date end = sdf.parse(endDate.trim());
                if (end != null) {
                    Calendar ec = Calendar.getInstance();
                    ec.setTime(end);
                    ec.set(Calendar.HOUR_OF_DAY, 23);
                    ec.set(Calendar.MINUTE, 59);
                    ec.set(Calendar.SECOND, 59);
                    if (next > ec.getTimeInMillis()) return -1;
                }
            } catch (Exception ignored) {}
        }
        return next;
    }

    private void rescheduleExact(Context context, Intent firedIntent, int requestCode, long time) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtras(firedIntent);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
    }

}
