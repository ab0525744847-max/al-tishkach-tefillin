package com.ariberman.altishkachtefillin;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
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

import java.util.Calendar;

public class AlarmActivity extends Activity {

    private Ringtone ringtone;
    private Vibrator vibrator;
    private boolean actionDone = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // הצגת מסך הצלצול גם כשהטלפון נעול
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
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        createScreen();
        startAlarm();
    }

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(50, 50, 50, 50);
        root.setBackgroundColor(0xFF06122D);

        TextView title = new TextView(this);
        title.setText("⏰ אל תשכח תפילין");
        title.setTextSize(32);
        title.setTextColor(0xFFFFD65A);
        title.setGravity(Gravity.CENTER);

        TextView message = new TextView(this);
        message.setText("\nהגיע הזמן להניח תפילין\n");
        message.setTextSize(22);
        message.setTextColor(0xFFFFFFFF);
        message.setGravity(Gravity.CENTER);

        Button doneButton = new Button(this);
        doneButton.setText("הנחתי תפילין ✓");
        doneButton.setTextSize(20);

        Button snoozeButton = new Button(this);
        snoozeButton.setText("עוד 10 דקות");
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

        // המשתמש הניח תפילין
        doneButton.setOnClickListener(v -> {

            if (actionDone) return;
            actionDone = true;

            stopAlarm();

            getSharedPreferences("tefillin", MODE_PRIVATE)
                    .edit()
                    .putBoolean("done_today", true)
                    .putLong("done_time", System.currentTimeMillis())
                    .apply();

            // מודיע למסך הראשי שהמשתמש הניח
            Intent updateIntent =
                    new Intent("com.ariberman.altishkachtefillin.TEFILLIN_DONE");
            updateIntent.setPackage(getPackageName());
            sendBroadcast(updateIntent);

            finishAndRemoveTask();
        });

        // דחייה של 10 דקות
        snoozeButton.setOnClickListener(v -> {

            if (actionDone) return;

            boolean alreadyDone =
                    getSharedPreferences("tefillin", MODE_PRIVATE)
                            .getBoolean("done_today", false);

            // אם כבר סימן שהניח - אי אפשר לדחות
            if (alreadyDone) {
                snoozeButton.setEnabled(false);
                snoozeButton.setText("כבר סימנת שהנחת ✓");
                return;
            }

            actionDone = true;

            stopAlarm();
            scheduleTenMinutes();

            getSharedPreferences("tefillin", MODE_PRIVATE)
                    .edit()
                    .putLong(
                            "snooze_until",
                            System.currentTimeMillis() + (10 * 60 * 1000L)
                    )
                    .apply();

            // מודיע למסך הראשי שנקבעה דחייה
            Intent updateIntent =
                    new Intent("com.ariberman.altishkachtefillin.TEFILLIN_SNOOZE");
            updateIntent.setPackage(getPackageName());
            sendBroadcast(updateIntent);

            // סוגר את חלון הצלצול מיד
            finishAndRemoveTask();
        });
    }

    private void startAlarm() {

        try {
            Uri alarmUri =
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_ALARM
                    );

            if (alarmUri == null) {
                alarmUri =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        );
            }

            ringtone =
                    RingtoneManager.getRingtone(
                            getApplicationContext(),
                            alarmUri
                    );

            if (ringtone != null) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(
                                            AudioAttributes.CONTENT_TYPE_SONIFICATION
                                    )
                                    .build()
                    );
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }

                ringtone.play();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        vibrator =
                (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null && vibrator.hasVibrator()) {

            long[] pattern = {0, 700, 400, 700, 400};

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(pattern, 0)
                );
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopAlarm() {

        try {
            if (ringtone != null && ringtone.isPlaying()) {
                ringtone.stop();
            }
        } catch (Exception ignored) {
        }

        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void scheduleTenMinutes() {

        long triggerTime =
                System.currentTimeMillis() + (10 * 60 * 1000L);

        Intent intent =
                new Intent(this, ReminderReceiver.class);

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        5010,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            try {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            } catch (SecurityException e) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }

        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        }
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // לא מאפשרים לכפתור חזרה להשאיר צלצול פעיל ברקע
        stopAlarm();
        super.onBackPressed();
    }
}
