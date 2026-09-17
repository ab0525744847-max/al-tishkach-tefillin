package com.ariberman.altishkachtefillin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "tefillin_alarm_v3";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {

        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        PowerManager.WakeLock wakeLock = null;

        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AlTishkachTefillin:ReminderWakeLock"
            );

            wakeLock.acquire(10000);
        }

        try {
            createNotificationChannel(context);

            Intent alarmIntent = new Intent(context, AlarmActivity.class);
            alarmIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            PendingIntent fullScreenPendingIntent =
                    PendingIntent.getActivity(
                            context,
                            3001,
                            alarmIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT |
                                    PendingIntent.FLAG_IMMUTABLE
                    );

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
                            .setContentIntent(fullScreenPendingIntent)
                            .setFullScreenIntent(fullScreenPendingIntent, true);

            NotificationManager manager =
                    (NotificationManager)
                            context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager != null) {
                manager.notify(NOTIFICATION_ID, builder.build());
            }

            context.startActivity(alarmIntent);

        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void createNotificationChannel(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "תזכורת תפילין",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription("התראת שעון מעורר להנחת תפילין");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(
                    android.app.Notification.VISIBILITY_PUBLIC
            );

            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
