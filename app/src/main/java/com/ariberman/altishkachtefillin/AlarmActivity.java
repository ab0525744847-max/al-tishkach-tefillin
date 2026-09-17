package com.ariberman.altishkachtefillin;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showOverLockScreen();
        createAlarmScreen();
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

    private void createAlarmScreen() {

        LinearLayout screen = new LinearLayout(this);

        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(45, 70, 45, 70);

        screen.setBackgroundColor(
                Color.rgb(5, 28, 68)
        );

        TextView smallTitle = new TextView(this);
        smallTitle.setText("אל תשכח תפילין");
        smallTitle.setTextColor(
                Color.rgb(230, 190, 80)
        );
        smallTitle.setTextSize(24);
        smallTitle.setGravity(Gravity.CENTER);
        smallTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        TextView icon = new TextView(this);
        icon.setText("✡");
        icon.setTextSize(75);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.WHITE);
        icon.setPadding(0, 45, 0, 20);

        TextView time = new TextView(this);

        String currentTime =
                new SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                ).format(new Date());

        time.setText(currentTime);
        time.setTextColor(Color.WHITE);
        time.setTextSize(70);
        time.setGravity(Gravity.CENTER);
        time.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        TextView message = new TextView(this);
        message.setText(
                "הגיע הזמן להניח תפילין"
        );
        message.setTextColor(Color.WHITE);
        message.setTextSize(25);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 20, 0, 55);

        Button doneButton =
                new Button(this);

        doneButton.setText(
                "✓  הנחתי תפילין"
        );

        doneButton.setTextSize(20);
        doneButton.setAllCaps(false);

        Button snoozeButton =
                new Button(this);

        snoozeButton.setText(
                "תזכיר לי בעוד 10 דקות"
        );

        snoozeButton.setTextSize(18);
        snoozeButton.setAllCaps(false);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        150
                );

        buttonParams.setMargins(
                0,
                15,
                0,
                15
        );

        doneButton.setLayoutParams(buttonParams);
        snoozeButton.setLayoutParams(buttonParams);

        doneButton.setOnClickListener(v -> {

            markTefillinDoneToday();

            stopAlarm();

            finish();
        });

        snoozeButton.setOnClickListener(v -> {

            scheduleTenMinutes();

            stopAlarm();

            finish();
        });

        screen.addView(smallTitle);
        screen.addView(icon);
        screen.addView(time);
        screen.addView(message);
        screen.addView(doneButton);
        screen.addView(snoozeButton);

        setContentView(screen);
    }

    private void markTefillinDoneToday() {

        String today =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(new Date());

        getSharedPreferences(
                "reminder",
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        "tefillin_done_date",
                        today
                )
                .putBoolean(
                        "enabled",
                        false
                )
                .apply();
    }

    private void scheduleTenMinutes() {

        long alarmTime =
                System.currentTimeMillis()
                        + (10 * 60 * 1000);

        Intent intent =
                new Intent(
                        this,
                        ReminderReceiver.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        5001,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S &&
                alarmManager.canScheduleExactAlarms()
        ) {

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
            );

        } else if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.M
        ) {

            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
            );

        } else {

            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
            );
        }
    }

    private void stopAlarm() {

        Intent stopIntent =
                new Intent(
                        this,
                        ReminderReceiver.class
                );

        stopIntent.setAction("STOP_ALARM");

        sendBroadcast(stopIntent);
    }
}
