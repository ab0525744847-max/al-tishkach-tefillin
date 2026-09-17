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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity {

    private static final String PREFS = "tefillin_prefs";
    private static final String KEY_RINGTONE = "ringtone_uri";
    private static final String KEY_DONE_DATE = "tefillin_done_date";

    private static final int ALARM_REQUEST_CODE = 5001;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private Button snoozeButton;

    private boolean alarmStopped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showOverLockScreen();
        createAlarmScreen();
        startAlarmSound();
        startVibration();
    }

    private void showOverLockScreen() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
    }

    private void createAlarmScreen() {

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 70, 48, 70);
        root.setBackgroundColor(Color.rgb(4, 18, 50));

        TextView icon = new TextView(this);
        icon.setText("🔔");
        icon.setTextSize(55);
        icon.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("אל תשכח תפילין");
        title.setTextColor(Color.rgb(255, 215, 100));
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 20, 0, 15);

        TextView message = new TextView(this);
        message.setText("הגיע זמן התזכורת להנחת תפילין");
        message.setTextColor(Color.WHITE);
        message.setTextSize(20);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, 50);

        Button doneButton = new Button(this);
        doneButton.setText("✓ הנחתי תפילין");
        doneButton.setTextSize(20);
        doneButton.setAllCaps(false);

        LinearLayout.LayoutParams doneParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        doneParams.setMargins(0, 10, 0, 20);

        doneButton.setLayoutParams(doneParams);

        snoozeButton = new Button(this);
        snoozeButton.setText("⏱ תזכיר לי בעוד 10 דקות");
        snoozeButton.setTextSize(18);
        snoozeButton.setAllCaps(false);

        LinearLayout.LayoutParams snoozeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        snoozeParams.setMargins(0, 10, 0, 10);

        snoozeButton.setLayoutParams(snoozeParams);

        doneButton.setOnClickListener(v -> markTefillinDone());

        snoozeButton.setOnClickListener(v -> snoozeTenMinutes());

        root.addView(icon);
        root.addView(title);
        root.addView(message);
        root.addView(doneButton);
        root.addView(snoozeButton);

        setContentView(root);
    }

    private void startAlarmSound() {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(PREFS, MODE_PRIVATE);

            String savedRingtone =
                    prefs.getString(KEY_RINGTONE, "");

            Uri alarmUri = null;

            if (savedRingtone != null && !savedRingtone.isEmpty()) {
                try {
                    alarmUri = Uri.parse(savedRingtone);
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

            if (alarmUri == null) {
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

            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);

            mediaPlayer.setOnPreparedListener(player -> {
                try {
                    player.start();
                } catch (Exception ignored) {
                }
            });

            mediaPlayer.setOnErrorListener(
                    (player, what, extra) -> {
                        stopSound();
                        return true;
                    }
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            stopSound();
        }
    }

    private void startVibration() {

        try {

            vibrator =
                    (Vibrator) getSystemService(
                            Context.VIBRATOR_SERVICE
                    );

            if (vibrator == null) {
                return;
            }

            if (!vibrator.hasVibrator()) {
                return;
            }

            long[] pattern = {
                    0,
                    800,
                    400,
                    800,
                    400
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                vibrator.vibrate(
                        VibrationEffect.createWaveform(
                                pattern,
                                0
                        )
                );

            } else {

                vibrator.vibrate(pattern, 0);
            }

        } catch (Exception ignored) {
        }
    }

    private void markTefillinDone() {

        if (alarmStopped) {
            return;
        }

        alarmStopped = true;

        if (snoozeButton != null) {
            snoozeButton.setEnabled(false);
        }

        cancelScheduledAlarm();

        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        prefs.edit()
                .putBoolean("tefillin_done", true)
                .putString(KEY_DONE_DATE, getTodayKey())
                .putBoolean("alarm_active", false)
                .remove("alarm_time")
                .apply();

        stopAlarm();

        Toast.makeText(
                this,
                "סומן שהנחת תפילין ✓",
                Toast.LENGTH_SHORT
        ).show();

        finishAlarmActivity();
    }

    private void snoozeTenMinutes() {

        if (alarmStopped) {
            return;
        }

        if (isTefillinDoneToday()) {

            Toast.makeText(
                    this,
                    "כבר סימנת שהנחת תפילין היום",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        long triggerTime =
                System.currentTimeMillis()
                        + (10L * 60L * 1000L);

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(
                        Context.ALARM_SERVICE
                );

        if (alarmManager == null) {

            Toast.makeText(
                    this,
                    "לא ניתן להגדיר את התזכורת",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent reminderIntent =
                new Intent(
                        this,
                        ReminderReceiver.class
                );

        reminderIntent.setAction(
                "TEFILLIN_REMINDER"
        );

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        ALARM_REQUEST_CODE,
                        reminderIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !alarmManager.canScheduleExactAlarms()) {

                Toast.makeText(
                        this,
                        "צריך לאפשר לאפליקציה תזכורות מדויקות",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS,
                            MODE_PRIVATE
                    );

            prefs.edit()
                    .putLong(
                            "alarm_time",
                            triggerTime
                    )
                    .putBoolean(
                            "alarm_active",
                            true
                    )
                    .apply();

            alarmStopped = true;

            stopAlarm();

            Toast.makeText(
                    this,
                    "התזכורת הבאה בעוד 10 דקות",
                    Toast.LENGTH_SHORT
            ).show();

            finishAlarmActivity();

        } catch (SecurityException e) {

            Toast.makeText(
                    this,
                    "צריך לאפשר לאפליקציה תזכורות מדויקות",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "לא ניתן להגדיר את התזכורת",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void cancelScheduledAlarm() {

        try {

            AlarmManager alarmManager =
                    (AlarmManager) getSystemService(
                            Context.ALARM_SERVICE
                    );

            if (alarmManager == null) {
                return;
            }

            Intent reminderIntent =
                    new Intent(
                            this,
                            ReminderReceiver.class
                    );

            reminderIntent.setAction(
                    "TEFILLIN_REMINDER"
            );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this,
                            ALARM_REQUEST_CODE,
                            reminderIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            alarmManager.cancel(pendingIntent);

        } catch (Exception ignored) {
        }
    }

    private boolean isTefillinDoneToday() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        String savedDate =
                prefs.getString(
                        KEY_DONE_DATE,
                        ""
                );

        return getTodayKey().equals(savedDate);
    }

    private String getTodayKey() {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                );

        return format.format(new Date());
    }

    private void stopSound() {

        if (mediaPlayer == null) {
            return;
        }

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

    private void stopVibration() {

        if (vibrator == null) {
            return;
        }

        try {
            vibrator.cancel();
        } catch (Exception ignored) {
        }

        vibrator = null;
    }

    private void stopAlarm() {

        stopSound();
        stopVibration();
    }

    private void finishAlarmActivity() {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }

        } catch (Exception e) {
            finish();
        }
    }

    @Override
    public void onBackPressed() {

        /*
         * לחיצה על "חזור" נחשבת כאילו
         * המשתמש ביטל את הצלצול ולכן
         * מסמנים שהניח תפילין.
         */
        markTefillinDone();
    }

    @Override
    protected void onDestroy() {

        stopAlarm();

        super.onDestroy();
    }
}
