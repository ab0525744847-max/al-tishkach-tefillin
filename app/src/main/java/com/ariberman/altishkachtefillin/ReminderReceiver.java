package com.ariberman.altishkachtefillin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "tefillin_alarm_channel_v2";

    @Override
    public void onReceive(Context context, Intent intent) {

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Uri alarmSound = Settings.System.DEFAULT_ALARM_ALERT_URI;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "תזכורת תפילין",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("צלצול תזכורת להנחת תפילין");
            channel.enableVibration(true);

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            channel.setSound(alarmSound, attributes);

            manager.createNotificationChannel(channel);
        }

        android.app.Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
            builder.setSound(alarmSound);
            builder.setPriority(android.app.Notification.PRIORITY_HIGH);
        }

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("אל תשכח תפילין")
                .setContentText("הגיע הזמן להניח תפילין 🙏")
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_ALARM)
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC);

        manager.notify(1001, builder.build());
    }
}
