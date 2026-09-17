package com.ariberman.altishkachtefillin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "tefillin_alarm_v3";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {

        // המסך שייפתח כאשר התזכורת מצלצלת
        Intent alarmIntent = new Intent(context, AlarmActivity.class);

        alarmIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent alarmPendingIntent = PendingIntent.getActivity(
                context,
                1001,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);

        // ערוץ מיוחד להתראת תפילין
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "תזכורת תפילין",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription("התראת תזכורת להנחת תפילין");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(
                    android.app.Notification.VISIBILITY_PUBLIC
            );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("אל תשכח תפילין")
                        .setContentText("הגיע הזמן להניח תפילין")
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentIntent(alarmPendingIntent)
                        .setFullScreenIntent(alarmPendingIntent, true);

        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }

        /*
         * אם Android מאפשר פתיחת Activity ישירות ברקע,
         * ננסה לפתוח את מסך הצלצול מיד.
         * ה-Full Screen Intent למעלה משמש גם כאשר
         * פתיחה ישירה ברקע מוגבלת.
         */
        try {
            context.startActivity(alarmIntent);
        } catch (Exception ignored) {
        }
    }
}
