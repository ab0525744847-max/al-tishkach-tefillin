package com.ariberman.altishkachtefillin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;

public class ReminderReceiver extends BroadcastReceiver {

    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;
    private static final String CHANNEL_ID = "tefillin_alarm_silent";

    @Override
    public void onReceive(Context context, Intent intent) {

        if ("STOP_ALARM".equals(intent.getAction())) {
            stopAlarm();
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(1001);
            return;
        }

        startAlarm(context);
        showNotification(context);
    }

    private void startAlarm(Context context) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            Uri alarmUri = Settings.System.DEFAULT_ALARM_ALERT_URI;

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, alarmUri);

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mediaPlayer.setAudioAttributes(attributes);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            vibrator = (Vibrator)
                    context.getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator != null) {
                long[] pattern = {0, 800, 500, 800, 500};

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                            android.os.VibrationEffect.createWaveform(pattern, 0)
                    );
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNotification(Context context) {

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "שעון מעורר תפילין",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
        }

        Intent stopIntent = new Intent(context, ReminderReceiver.class);
        stopIntent.setAction("STOP_ALARM");

        PendingIntent stopPendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        2001,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("אל תשכח תפילין")
                .setContentText("הגיע הזמן להניח תפילין 🙏")
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(
                        android.R.drawable.ic_media_pause,
                        "הפסק צלצול",
                        stopPendingIntent
                );

        manager.notify(1001, builder.build());
    }

    private static void stopAlarm() {

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }
}
