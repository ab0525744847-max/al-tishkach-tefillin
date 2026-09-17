package com.ariberman.altishkachtefillin;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity {

    private static final String PREFS = "tefillin_prefs";
    private static final String KEY_RINGTONE = "ringtone_uri";

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private boolean alarmStopped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showOverLockScreen();
        buildScreen();
        startAlarm();
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(55, 70, 55, 70);
        root.setBackgroundColor(Color.rgb(3, 18, 48));

        TextView smallTitle = new TextView(this);
        smallTitle.setText("TEFILLIN REMINDER");
        smallTitle.setTextColor(Color.rgb(239, 199, 91));
        smallTitle.setTextSize(14);
        smallTitle.setGravity(Gravity.CENTER);
        smallTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView title = new TextView(this);
        title.setText("אל תשכח תפילין");
        title.setTextColor(Color.rgb(255, 221, 123));
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 35, 0, 15);

        TextView subtitle = new TextView(this);
        subtitle.setText("הגיע הזמן להניח תפילין");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(19);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 60);

        Button doneButton = new Button(this);
        doneButton.setText("✓ הנחתי תפילין");
        doneButton.setTextSize(19);
        doneButton.setAllCaps(false);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        150
                );

        buttonParams.setMargins(0, 15, 0, 15);
        doneButton.setLayoutParams(buttonParams);

        doneButton.setOnClickListener(v -> {
            markTefillinDone();
            stopAlarm();
            finishAndRemoveTask();
        });

        Button snoozeButton = new Button(this);
        snoozeButton.setText("⏱ תזכיר לי בעוד 10 דקות");
        snoozeButton.setTextSize(18);
        snoozeButton.setAllCaps(false);
        snoozeButton.setLayoutParams(buttonParams);

        snoozeButton.setOnClickListener(v -> {
            if (isTefillinDoneToday()) {
                stopAlarm();
                finishAndRemoveTask();
                return;
            }

            scheduleSnooze();
            stopAlarm();
            finishAndRemoveTask();
        });

        Button stopButton = new Button(this);
        stopButton.setText("הפסק צלצול");
        stopButton.setTextSize(17);
        stopButton.setAllCaps(false);
        stopButton.setLayoutParams(buttonParams);

        stopButton.setOnClickListener(v -> {
            /*
             * לפי ההתנהגות שקבענו:
             * ביטול הצלצול נחשב כאישור שהמשתמש הניח תפילין.
             */
            markTefillinDone();
            stopAlarm();
            finishAndRemoveTask();
        });

        root.addView(smallTitle);
        root.addView(title);
        root.addView(subtitle);
        root.addView(doneButton);
        root.addView(snoozeButton);
        root.addView(stopButton);

        setContentView(root);
    }

    private void startAlarm() {
        if (mediaPlayer != null) {
            return;
        }

        try {
            SharedPreferences prefs =
                    getSharedPreferences(PREFS, MODE_PRIVATE);

            String savedUri =
                    prefs.getString(KEY_RINGTONE, null);

            Uri alarmUri = null;

            if (savedUri != null && !savedUri.trim().isEmpty()) {
                try {
                    alarmUri = Uri.parse(savedUri);
                } catch (Exception ignored) {
                }
            }

            if (alarmUri == null) {
                alarmUri =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_ALARM
                        );
            }

            if (alarmUri == null) {
                alarmUri =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        );
            }

            if (alarmUri != null) {
                mediaPlayer = new MediaPlayer();

                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(
                                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                                )
                                .build()
                );

                mediaPlayer.setDataSource(this, alarmUri);
                mediaPlayer.setLooping(true);

                mediaPlayer.setOnPreparedListener(mp -> {
                    if (!alarmStopped) {
                        mp.start();
                    }
                });

                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    releaseMediaPlayer();
                    playDefaultAlarm();
                    return true;
                });

                mediaPlayer.prepareAsync();
            }

        } catch (Exception e) {
            releaseMediaPlayer();
            playDefaultAlarm();
        }

        startVibration();
    }

    private void playDefaultAlarm() {
        if (alarmStopped || mediaPlayer != null) {
            return;
        }

        try {
            Uri defaultUri =
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_ALARM
                    );

            if (defaultUri == null) {
                return;
            }

            mediaPlayer = new MediaPlayer();

            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
            );

            mediaPlayer.setDataSource(this, defaultUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

        } catch (Exception ignored) {
            releaseMediaPlayer();
        }
    }

    private void startVibration() {
        try {
            vibrator =
                    (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }

            long[] pattern = {
                    0,
                    700,
                    350,
                    700,
                    350
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(pattern, 0)
                );
            } else {
                vibrator.vibrate(pattern, 0);
            }

        } catch (Exception ignored) {
        }
    }

    private void scheduleSnooze() {
        long triggerTime =
                System.currentTimeMillis() + (10 * 60 * 1000L);

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) {
            return;
        }

        Intent intent =
                new Intent(this, ReminderReceiver.class);

        intent.setAction("TEFILLIN_REMINDER");

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        5001,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()) {

                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );

            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putLong("alarm_time", triggerTime)
                    .putBoolean("alarm_active", true)
                    .apply();

        } catch (Exception ignored) {
        }
    }

    private void markTefillinDone() {
        String today =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(new Date());

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean("tefillin_done", true)
                .putString("tefillin_done_date", today)
                .putBoolean("alarm_active", false)
                .remove("alarm_time")
                .apply();

        cancelPendingAlarm();
    }

    private boolean isTefillinDoneToday() {
        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        String savedDate =
                prefs.getString("tefillin_done_date", "");

        String today =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(new Date());

        return prefs.getBoolean("tefillin_done", false)
                && today.equals(savedDate);
    }

    private void cancelPendingAlarm() {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) {
            return;
        }

        Intent intent =
                new Intent(this, ReminderReceiver.class);

        intent.setAction("TEFILLIN_REMINDER");

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        5001,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        alarmManager.cancel(pendingIntent);
    }

    private void stopAlarm() {
        alarmStopped = true;

        releaseMediaPlayer();

        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }

            vibrator = null;
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.reset();
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }

            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        /*
         * בזמן שהשעון מצלצל, כפתור "חזרה"
         * לא סוגר בטעות את מסך התזכורת.
         */
    }
}
