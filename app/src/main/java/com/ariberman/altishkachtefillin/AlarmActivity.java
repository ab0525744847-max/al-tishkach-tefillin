package com.ariberman.altishkachtefillin;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity {

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    private SharedPreferences prefs;

    private static final int NOTIFICATION_ID = 1001;
    private static final int ALARM_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showOverLockScreen();

        prefs = getSharedPreferences(
                "tefillin_reminder",
                Context.MODE_PRIVATE
        );

        buildScreen();
        startAlarm();
    }

    private void showOverLockScreen() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        Window window = getWindow();

        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );
    }

    private void buildScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(50, 70, 50, 70);
        root.setBackgroundColor(Color.rgb(3, 16, 43));

        TextView title = new TextView(this);
        title.setText("אל תשכח תפילין");
        title.setTextColor(Color.rgb(255, 218, 105));
        title.setTextSize(34);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView message = new TextView(this);
        message.setText("\nהגיע הזמן להניח תפילין\n");
        message.setTextColor(Color.WHITE);
        message.setTextSize(22);
        message.setGravity(Gravity.CENTER);

        Button doneButton = new Button(this);
        doneButton.setText("✓ הנחתי תפילין");
        doneButton.setTextSize(20);

        Button snoozeButton = new Button(this);
        snoozeButton.setText("⏰ עוד 10 דקות");
        snoozeButton.setTextSize(20);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.setMargins(0, 20, 0, 20);

        root.addView(title);
        root.addView(message);
        root.addView(doneButton, buttonParams);
        root.addView(snoozeButton, buttonParams);

        setContentView(root);

        doneButton.setOnClickListener(v -> markAsDone());

        snoozeButton.setOnClickListener(v -> snoozeTenMinutes());
    }

    private void startAlarm() {

        try {
            Uri alarmUri = getSelectedAlarmUri();

            mediaPlayer = new MediaPlayer();

            mediaPlayer.setDataSource(this, alarmUri);

            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
            );

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

        startVibration();
    }

    /*
     * כאן נשמר הצלצול שהמשתמש בחר.
     * בשלב הבא נחבר למסך הבחירה של 10 הצלצולים.
     */
    private Uri getSelectedAlarmUri() {

        String savedUri =
                prefs.getString("selected_ringtone_uri", null);

        if (savedUri != null && !savedUri.isEmpty()) {
            return Uri.parse(savedUri);
        }

        Uri uri =
                RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM
                );

        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
            );
        }

        return uri;
    }

    private void startVibration() {

        vibrator =
                (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (vibrator == null || !vibrator.hasVibrator()) {
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
    }

    private void markAsDone() {

        stopAlarm();

        String today =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(new Date());

        prefs.edit()
                .putString("completed_date", today)
                .putBoolean("completed_today", true)
                .apply();

        cancelCurrentNotification();

        Toast.makeText(
                this,
                "סומן שהנחת תפילין ✓",
                Toast.LENGTH_SHORT
        ).show();

        finishAndRemoveTask();
    }

    private void snoozeTenMinutes() {

        String today =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(new Date());

        String completedDate =
                prefs.getString("completed_date", "");

        if (today.equals(completedDate)) {

            Toast.makeText(
                    this,
                    "כבר סימנת שהנחת תפילין היום",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        stopAlarm();

        long nextAlarm =
                System.currentTimeMillis() +
                        (10 * 60 * 1000L);

        Intent intent =
                new Intent(
                        this,
                        ReminderReceiver.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        ALARM_REQUEST_CODE,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(ALARM_SERVICE);

        if (alarmManager != null) {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()) {

                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarm,
                        pendingIntent
                );

            } else {

                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarm,
                        pendingIntent
                );
            }
        }

        prefs.edit()
                .putLong("snooze_until", nextAlarm)
                .apply();

        cancelCurrentNotification();

        Toast.makeText(
                this,
                "נזכיר לך שוב בעוד 10 דקות",
                Toast.LENGTH_SHORT
        ).show();

        finishAndRemoveTask();
    }

    private void cancelCurrentNotification() {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private void stopAlarm() {

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
        }
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // לא מאפשרים לסגור בטעות את מסך הצלצול
        // בלי לבחור "הנחתי" או "עוד 10 דקות".
    }
}
