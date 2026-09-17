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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String WEBSITE_URL =
            "https://mildly-puck-wcst1.shipped.cloud/";

    private static final String PREFS = "tefillin_prefs";

    private static final String KEY_RINGTONE = "ringtone_uri";
    private static final String KEY_ALARM_TIME = "alarm_time";
    private static final String KEY_ALARM_ACTIVE = "alarm_active";
    private static final String KEY_DONE_DATE = "tefillin_done_date";

    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private static final int ALARM_REQUEST_CODE = 5001;

    private WebView webView;

    @SuppressLint({
            "SetJavaScriptEnabled",
            "JavascriptInterface"
    })
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                notifyWebsiteAppReady();
            }
        });

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        askNotificationPermission();

        webView.loadUrl(WEBSITE_URL);
    }

    // ---------------------------------------------------------
    // Notification permission
    // ---------------------------------------------------------

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        NOTIFICATION_PERMISSION_REQUEST
                );
            }
        }
    }

    // ---------------------------------------------------------
    // Exact alarm permission
    // ---------------------------------------------------------

    private boolean canScheduleExactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        return alarmManager != null
                && alarmManager.canScheduleExactAlarms();
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) {
            return;
        }

        if (alarmManager.canScheduleExactAlarms()) {
            return;
        }

        try {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            );

            intent.setData(
                    Uri.parse("package:" + getPackageName())
            );

            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "יש לאפשר לאפליקציה תזכורות מדויקות",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ---------------------------------------------------------
    // Display over other apps permission
    // ---------------------------------------------------------

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        return Settings.canDrawOverlays(this);
    }

    private void openOverlayPermissionScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        if (Settings.canDrawOverlays(this)) {
            return;
        }

        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );

            startActivity(intent);

        } catch (Exception firstError) {
            try {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                );

                startActivity(intent);

            } catch (Exception secondError) {
                Toast.makeText(
                        this,
                        "לא ניתן לפתוח את הגדרת ההצגה מעל אפליקציות",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // ---------------------------------------------------------
    // Alarm PendingIntent
    // ---------------------------------------------------------

    private PendingIntent getReminderPendingIntent() {
        Intent intent =
                new Intent(this, ReminderReceiver.class);

        intent.setAction("TEFILLIN_REMINDER");

        return PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );
    }

    // ---------------------------------------------------------
    // Schedule alarm
    // ---------------------------------------------------------

    private void scheduleAlarm(long triggerAtMillis) {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Toast.makeText(
                    this,
                    "זמן התזכורת כבר עבר",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) {
            Toast.makeText(
                    this,
                    "לא ניתן להפעיל את מערכת התזכורות",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!canScheduleExactAlarm()) {
            requestExactAlarmPermissionIfNeeded();

            Toast.makeText(
                    this,
                    "יש לאפשר תזכורות מדויקות ואז לשמור שוב את התזכורת",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    getReminderPendingIntent()
            );

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_ALARM_TIME, triggerAtMillis)
                    .putBoolean(KEY_ALARM_ACTIVE, true)
                    .apply();

            Toast.makeText(
                    this,
                    "התזכורת נשמרה",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (SecurityException e) {
            requestExactAlarmPermissionIfNeeded();

            Toast.makeText(
                    this,
                    "יש לאפשר תזכורות מדויקות",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "לא הצלחנו לשמור את התזכורת",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ---------------------------------------------------------
    // Cancel alarm
    // ---------------------------------------------------------

    private void cancelAlarm() {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (alarmManager != null) {
            alarmManager.cancel(
                    getReminderPendingIntent()
            );
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALARM_ACTIVE, false)
                .remove(KEY_ALARM_TIME)
                .apply();
    }

    // ---------------------------------------------------------
    // Ringtone chooser
    // ---------------------------------------------------------

    private void showRingtoneChooser() {
        RingtoneManager manager =
                new RingtoneManager(this);

        manager.setType(
                RingtoneManager.TYPE_ALARM
        );

        Cursor cursor = null;

        final ArrayList<String> names =
                new ArrayList<>();

        final ArrayList<String> uris =
                new ArrayList<>();

        try {
            cursor = manager.getCursor();

            int count = 0;

            while (cursor != null
                    && cursor.moveToNext()
                    && count < 10) {

                int position =
                        cursor.getPosition();

                Uri ringtoneUri =
                        manager.getRingtoneUri(position);

                if (ringtoneUri == null) {
                    continue;
                }

                String title =
                        "צלצול " + (count + 1);

                try {
                    Ringtone ringtone =
                            RingtoneManager.getRingtone(
                                    this,
                                    ringtoneUri
                            );

                    if (ringtone != null) {
                        String ringtoneTitle =
                                ringtone.getTitle(this);

                        if (ringtoneTitle != null
                                && !ringtoneTitle.trim().isEmpty()) {

                            title = ringtoneTitle;
                        }
                    }

                } catch (Exception ignored) {
                    // Keep fallback title.
                }

                names.add(
                        (count + 1) + ". " + title
                );

                uris.add(
                        ringtoneUri.toString()
                );

                count++;
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "לא ניתן לקרוא את רשימת הצלצולים",
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

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
                            if (which < 0
                                    || which >= uris.size()) {
                                return;
                            }

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
                .setNegativeButton(
                        "ביטול",
                        null
                )
                .show();
    }

    // ---------------------------------------------------------
    // Ringtone preview
    // ---------------------------------------------------------

    private void playPreview(String uriString) {
        if (uriString == null
                || uriString.trim().isEmpty()) {
            return;
        }

        try {
            Uri uri =
                    Uri.parse(uriString);

            final Ringtone ringtone =
                    RingtoneManager.getRingtone(
                            this,
                            uri
                    );

            if (ringtone == null) {
                return;
            }

            ringtone.play();

            if (webView != null) {
                webView.postDelayed(
                        () -> {
                            try {
                                if (ringtone.isPlaying()) {
                                    ringtone.stop();
                                }
                            } catch (Exception ignored) {
                            }
                        },
                        3000
                );
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "לא ניתן להשמיע את הצלצול",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ---------------------------------------------------------
    // Daily tefillin state
    // ---------------------------------------------------------

    private String todayKey() {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(new Date());
    }

    private boolean isTefillinDoneToday() {
        String doneDate =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                )
                        .getString(
                                KEY_DONE_DATE,
                                ""
                        );

        return todayKey().equals(doneDate);
    }

    private void markTefillinDoneInternal() {
        getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        KEY_DONE_DATE,
                        todayKey()
                )
                .putBoolean(
                        "tefillin_done",
                        true
                )
                .apply();

        cancelAlarm();

        notifyWebsiteTefillinDone();
    }

    // ---------------------------------------------------------
    // Website events
    // ---------------------------------------------------------

    private void notifyWebsiteAppReady() {
        if (webView == null) {
            return;
        }

        webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('androidAppReady'));",
                null
        );
    }

    private void notifyWebsiteTefillinDone() {
        if (webView == null) {
            return;
        }

        webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('tefillinDone'));",
                null
        );
    }

    private void notifyWebsiteResumed() {
        if (webView == null) {
            return;
        }

        webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('androidAppResumed'));",
                null
        );
    }

    // ---------------------------------------------------------
    // JavaScript <-> Android bridge
    // ---------------------------------------------------------

    public class AndroidBridge {

        @JavascriptInterface
        public void scheduleReminder(long timestamp) {
            runOnUiThread(
                    () -> scheduleAlarm(timestamp)
            );
        }

        @JavascriptInterface
        public void setReminder(long timestamp) {
            scheduleReminder(timestamp);
        }

        @JavascriptInterface
        public void cancelReminder() {
            runOnUiThread(
                    () -> cancelAlarm()
            );
        }

        @JavascriptInterface
        public void chooseRingtone() {
            runOnUiThread(
                    () -> showRingtoneChooser()
            );
        }

        @JavascriptInterface
        public String getSelectedRingtone() {
            return getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
                    .getString(
                            KEY_RINGTONE,
                            ""
                    );
        }

        @JavascriptInterface
        public void requestOverlayPermission() {
            runOnUiThread(
                    () -> openOverlayPermissionScreen()
            );
        }

        @JavascriptInterface
        public boolean hasOverlayPermission() {
            return canDrawOverlays();
        }

        @JavascriptInterface
        public void requestExactAlarmPermission() {
            runOnUiThread(
                    () -> requestExactAlarmPermissionIfNeeded()
            );
        }

        @JavascriptInterface
        public boolean hasExactAlarmPermission() {
            return canScheduleExactAlarm();
        }

        @JavascriptInterface
        public void markTefillinDone() {
            runOnUiThread(
                    () -> markTefillinDoneInternal()
            );
        }

        @JavascriptInterface
        public boolean isTefillinDone() {
            return isTefillinDoneToday();
        }

        @JavascriptInterface
        public boolean isTefillinDoneToday() {
            return MainActivity.this
                    .isTefillinDoneToday();
        }

        @JavascriptInterface
        public long getAlarmTime() {
            return getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
                    .getLong(
                            KEY_ALARM_TIME,
                            0L
                    );
        }

        @JavascriptInterface
        public boolean isAlarmActive() {
            return getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
                    .getBoolean(
                            KEY_ALARM_ACTIVE,
                            false
                    );
        }
    }

    // ---------------------------------------------------------
    // Activity lifecycle
    // ---
