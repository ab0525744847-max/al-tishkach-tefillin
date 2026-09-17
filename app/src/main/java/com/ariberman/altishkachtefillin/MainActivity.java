package com.ariberman.altishkachtefillin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends Activity {

    private static final String WEBSITE_URL =
            "https://mildly-puck-wcst1.shipped.cloud/";

    private static final String PREFS = "tefillin_prefs";
    private static final String KEY_RINGTONE = "ringtone_uri";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;

    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        askNotificationPermission();

        webView.loadUrl(WEBSITE_URL);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST
                );
            }
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager =
                    (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            if (alarmManager != null &&
                    !alarmManager.canScheduleExactAlarms()) {

                try {
                    Intent intent = new Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    );

                    intent.setData(
                            Uri.parse("package:" + getPackageName())
                    );

                    startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void scheduleAlarm(long triggerAtMillis) {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

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

        if (alarmManager == null) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()) {

                requestExactAlarmPermissionIfNeeded();

                Toast.makeText(
                        this,
                        "צריך לאפשר לאפליקציה תזכורות מדויקות",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putLong("alarm_time", triggerAtMillis)
                    .putBoolean("alarm_active", true)
                    .apply();

        } catch (SecurityException e) {
            requestExactAlarmPermissionIfNeeded();
        }
    }

    private void cancelAlarm() {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

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

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean("alarm_active", false)
                .remove("alarm_time")
                .apply();
    }

    private void showRingtoneChooser() {
        RingtoneManager manager =
                new RingtoneManager(this);

        manager.setType(RingtoneManager.TYPE_ALARM);

        Cursor cursor = manager.getCursor();

        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> uris = new ArrayList<>();

        int count = 0;

        while (cursor.moveToNext() && count < 10) {
            int position = cursor.getPosition();

            Uri uri = manager.getRingtoneUri(position);

            if (uri == null) {
                continue;
            }

            Ringtone ringtone =
                    RingtoneManager.getRingtone(this, uri);

            String title;

            try {
                title = ringtone.getTitle(this);
            } catch (Exception e) {
                title = "צלצול " + (count + 1);
            }

            names.add((count + 1) + ". " + title);
            uris.add(uri.toString());

            count++;
        }

        cursor.close();

        if (names.isEmpty()) {
            Toast.makeText(
                    this,
                    "לא נמצאו צלצולי שעון מעורר",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String[] ringtoneNames =
                names.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("בחר צלצול לתזכורת")
                .setItems(
                        ringtoneNames,
                        (dialog, which) -> {

                            String selectedUri =
                                    uris.get(which);

                            getSharedPreferences(
                                    PREFS,
                                    MODE_PRIVATE
                            )
                                    .edit()
                                    .putString(
                                            KEY_RINGTONE,
                                            selectedUri
                                    )
                                    .apply();

                            playPreview(selectedUri);

                            Toast.makeText(
                                    this,
                                    "הצלצול נשמר",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void playPreview(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);

            Ringtone ringtone =
                    RingtoneManager.getRingtone(this, uri);

            if (ringtone != null) {
                ringtone.play();

                webView.postDelayed(() -> {
                    try {
                        if (ringtone.isPlaying()) {
                            ringtone.stop();
                        }
                    } catch (Exception ignored) {
                    }
                }, 3000);
            }

        } catch (Exception ignored) {
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void scheduleReminder(long timestamp) {
            runOnUiThread(() ->
                    scheduleAlarm(timestamp)
            );
        }

        @JavascriptInterface
        public void setReminder(long timestamp) {
            scheduleReminder(timestamp);
        }

        @JavascriptInterface
        public void cancelReminder() {
            runOnUiThread(() ->
                    cancelAlarm()
            );
        }

        @JavascriptInterface
        public void chooseRingtone() {
            runOnUiThread(() ->
                    showRingtoneChooser()
            );
        }

        @JavascriptInterface
        public String getSelectedRingtone() {
            return getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            ).getString(KEY_RINGTONE, "");
        }

        @JavascriptInterface
        public void markTefillinDone() {
            getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
                    .edit()
                    .putBoolean("tefillin_done", true)
                    .putBoolean("alarm_active", false)
                    .apply();

            runOnUiThread(() -> {
                cancelAlarm();

                webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('tefillinDone'));",
                        null
                );
            });
        }

        @JavascriptInterface
        public boolean isTefillinDone() {
            return getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            ).getBoolean("tefillin_done", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('androidAppResumed'));",
                    null
            );
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
}
