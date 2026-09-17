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

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "tefillin_alarm";

    @Override
    public void onReceive(Context context, Intent intent) {

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Uri alarmSound = Settings.System.DEFAULT_ALARM_ALERT_URI;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build();

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "תזכורת תפילין",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("תזכורת להנחת תפילין");
            channel.enableVibration(true);
            channel.setSound(alarmSound, audioAttributes);

            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("אל תשכח תפילין")
                        .setContentText("הגיע הזמן להניח תפילין 🙏")
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setSound
