package com.ariberman.altishkachtefillin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String PREFS = "tefillin_prefs";
    private static final String KEY_DONE_DATE = "done_date";
    private static final String KEY_REMINDER_TIME = "reminder_time";
    private static final String KEY_OVERLAY_ASKED = "overlay_asked";

    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final int OVERLAY_PERMISSION_CODE = 101;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        requestNotificationPermission();
        requestOverlayPermissionOnce();

        webView.loadUrl("https://mildly-puck-wcst1.shipped.cloud/");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE
                );
            }
        }
    }

    private void requestOverlayPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        if (Settings.canDrawOverlays(this)) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        boolean alreadyAsked = prefs.getBoolean(KEY_OVERLAY_ASKED, false);

        if (!alreadyAsked) {
            prefs.edit()
                    .putBoolean(KEY_OVERLAY_ASKED, true)
                    .apply();

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );

            try {
                startActivityForResult(intent, OVERLAY_PERMISSION_CODE);
            } catch (Exception ignored) {
            }
        }
    }

    private String todayKey() {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(new Date());
    }

    private boolean isDoneToday() {
        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        String doneDate =
                prefs.getString(KEY_DONE_DATE, "");

        return todayKey().equals(doneDate);
    }

    private void markDoneToday() {
        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_DONE_DATE, todayKey())
                .apply();

        cancelAllReminderAlarms();
        stopAlarmSound();

        updateWebsiteState();
    }

    private void stopAlarmSound() {
        Intent stopIntent =
                new Intent("com.ariberman.altishkachtefillin.STOP_ALARM");

        stopIntent.setPackage(getPackageName());
        sendBroadcast(stopIntent);
    }

    private void cancelAllReminderAlarms() {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent =
                new Intent(this, ReminderReceiver.class);

        for (int requestCode = 1000; requestCode <= 1010; requestCode++) {

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_NO_CREATE |
                                    PendingIntent.FLAG_IMMUTABLE
                    );

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
    }

    private void scheduleReminder(int hour, int minute) {

        if (isDoneToday()) {
            updateWebsiteState();
            return;
        }

        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();

        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.getTimeInMillis() <= now.getTimeInMillis()) {
            return;
        }

        Intent intent =
                new Intent(this, ReminderReceiver.class);

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        1000,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()) {

                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        target.getTimeInMillis(),
                        pendingIntent
                );

            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        target.getTimeInMillis(),
                        pendingIntent
                );
            }
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    target.getTimeInMillis(),
                    pendingIntent
            );
        }

        String time =
                String.format(
                        Locale.US,
                        "%02d:%02d",
                        hour,
                        minute
                );

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_REMINDER_TIME, time)
                .apply();
    }

    private void scheduleTenMinutes() {

        if (isDoneToday()) {
            updateWebsiteState();
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 10);

        Intent intent =
                new Intent(this, ReminderReceiver.class);

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        1001,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        long triggerAt =
                calendar.getTimeInMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()) {

                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );

            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
            }

        } else {

            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
            );
        }

        stopAlarmSound();
    }

    private void updateWebsiteState() {

        if (webView == null) {
            return;
        }

        boolean done = isDoneToday();

        String javascript =
                "javascript:(function(){" +
                "window.tefillinDoneToday=" + done + ";" +
                "window.dispatchEvent(new CustomEvent('tefillinStateChanged'," +
                "{detail:{done:" + done + "}}));" +
                "})();";

        webView.post(() ->
                webView.evaluateJavascript(
                        javascript,
                        null
                )
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            updateWebsiteState();
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void scheduleReminder(String time) {

            if (time == null || !time.matches("\\d{2}:\\d{2}")) {
                return;
            }

            try {
                String[] parts = time.split(":");

                int hour =
                        Integer.parseInt(parts[0]);

                int minute =
                        Integer.parseInt(parts[1]);

                scheduleReminder(hour, minute);

            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void scheduleReminder(int hour, int minute) {
            MainActivity.this.scheduleReminder(hour, minute);
        }

        @JavascriptInterface
        public void tefillinDone() {
            markDoneToday();
        }

        @JavascriptInterface
        public void markTefillinDone() {
            markDoneToday();
        }

        @JavascriptInterface
        public void stopAlarm() {
            stopAlarmSound();
        }

        @JavascriptInterface
        public void remindInTenMinutes() {
            scheduleTenMinutes();
        }

        @JavascriptInterface
        public boolean isTefillinDoneToday() {
            return isDoneToday();
        }

        @JavascriptInterface
        public void openOverlayPermission() {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(MainActivity.this)) {

                Intent intent =
                        new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse(
                                        "package:" +
                                                getPackageName()
                                )
                        );

                startActivity(intent);
            }
        }
    }
}
