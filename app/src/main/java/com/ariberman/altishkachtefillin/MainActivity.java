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

    private static final int ALARM_REQUEST_CODE = 5001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;

    private WebView webView;
    private Ringtone previewRingtone;

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
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                super.onPageFinished(view, url);
                notifyWebsiteOfNativeState();
            }
        });

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        askNotificationPermission();

        webView.loadUrl(WEBSITE_URL);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        );
    }

    private String todayKey() {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(new Date());
    }

    private boolean isDoneToday() {
        String savedDate =
                prefs().getString(
                        KEY_DONE_DATE,
                        ""
                );

        return todayKey().equals(savedDate);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        NOTIFICATION_PERMISSION_REQUEST
                );
            }
        }
    }

    private boolean canScheduleExactAlarm() {
        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.S) {
            return true;
        }

        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(
                                Context.ALARM_SERVICE
                        );

        return alarmManager != null &&
                alarmManager.canScheduleExactAlarms();
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.S) {
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager == null) {
            return;
        }

        if (alarmManager.canScheduleExactAlarms()) {
            return;
        }

        try {
            Intent intent =
                    new Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    );

            intent.setData(
                    Uri.parse(
                            "package:" +
                                    getPackageName()
                    )
            );

            startActivity(intent);

        } catch (Exception ignored) {
        }
    }

    private PendingIntent getReminderPendingIntent() {
        Intent intent =
                new Intent(
                        this,
                        ReminderReceiver.class
                );

        intent.setAction(
                "TEFILLIN_REMINDER"
        );

        return PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleAlarm(
            long triggerAtMillis
    ) {

        if (triggerAtMillis <=
                System.currentTimeMillis()) {

            Toast.makeText(
                    this,
                    "זמן התזכורת כבר עבר",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager == null) {
            Toast.makeText(
                    this,
                    "לא ניתן להפעיל את התזכורת",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!canScheduleExactAlarm()) {
            requestExactAlarmPermissionIfNeeded();

            Toast.makeText(
                    this,
                    "צריך לאפשר תזכורות מדויקות",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        PendingIntent pendingIntent =
                getReminderPendingIntent();

        try {
            alarmManager.cancel(
                    pendingIntent
            );

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );

            prefs()
                    .edit()
                    .putLong(
                            KEY_ALARM_TIME,
                            triggerAtMillis
                    )
                    .putBoolean(
                            KEY_ALARM_ACTIVE,
                            true
                    )
                    .apply();

            notifyWebsiteOfNativeState();

        } catch (SecurityException e) {
            requestExactAlarmPermissionIfNeeded();

            Toast.makeText(
                    this,
                    "צריך לאפשר תזכורות מדויקות",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void cancelAlarm() {
        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager != null) {
            alarmManager.cancel(
                    getReminderPendingIntent()
            );
        }

        prefs()
                .edit()
                .putBoolean(
                        KEY_ALARM_ACTIVE,
                        false
                )
                .remove(
                        KEY_ALARM_TIME
                )
                .apply();

        notifyWebsiteOfNativeState();
    }

    private void markDone() {
        cancelAlarm();

        prefs()
                .edit()
                .putString(
                        KEY_DONE_DATE,
                        todayKey()
                )
                .putBoolean(
                        "tefillin_done",
                        true
                )
                .putBoolean(
                        KEY_ALARM_ACTIVE,
                        false
                )
                .remove(
                        KEY_ALARM_TIME
                )
                .apply();

        notifyWebsiteTefillinDone();
        notifyWebsiteOfNativeState();
    }

    private void snoozeTenMinutes() {
        if (isDoneToday()) {
            Toast.makeText(
                    this,
                    "כבר סימנת שהנחת תפילין היום",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        long triggerAtMillis =
                System.currentTimeMillis() +
                        (10L * 60L * 1000L);

        scheduleAlarm(
                triggerAtMillis
        );
    }

    private void showRingtoneChooser() {
        RingtoneManager manager =
                new RingtoneManager(this);

        manager.setType(
                RingtoneManager.TYPE_ALARM
        );

        Cursor cursor = null;

        ArrayList<String> names =
                new ArrayList<>();

        ArrayList<String> uris =
                new ArrayList<>();

        try {
            cursor = manager.getCursor();

            int count = 0;

            while (cursor.moveToNext() &&
                    count < 10) {

                int position =
                        cursor.getPosition();

                Uri ringtoneUri =
                        manager.getRingtoneUri(
                                position
                        );

                if (ringtoneUri == null) {
                    continue;
                }

                String title =
                        "צלצול " +
                                (count + 1);

                try {
                    Ringtone ringtone =
                            RingtoneManager.getRingtone(
                                    this,
                                    ringtoneUri
                            );

                    if (ringtone != null) {
                        title =
                                ringtone.getTitle(
                                        this
                                );
                    }

                } catch (Exception ignored) {
                }

                names.add(
                        (count + 1) +
                                ". " +
                                title
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

            return;

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
                names.toArray(
                        new String[0]
                );

        new AlertDialog.Builder(this)
                .setTitle(
                        "בחר צלצול לתזכורת"
                )
                .setItems(
                        ringtoneNames,
                        (dialog, which) -> {

                            if (which < 0 ||
                                    which >= uris.size()) {
                                return;
                            }

                            String selectedUri =
                                    uris.get(which);

                            prefs()
                                    .edit()
                                    .putString(
                                            KEY_RINGTONE,
                                            selectedUri
                                    )
                                    .apply();

                            playPreview(
                                    selectedUri
                            );

                            Toast.makeText(
                                    this,
                                    "הצלצול נשמר",
                                    Toast.LENGTH_SHORT
                            ).show();

                            notifyWebsiteOfNativeState();
                        }
                )
                .setNegativeButton(
                        "ביטול",
                        null
                )
                .show();
    }

    private void playPreview(
            String uriString
    ) {
        stopPreview();

        try {
            Uri uri =
                    Uri.parse(
                            uriString
                    );

            previewRingtone =
                    RingtoneManager.getRingtone(
                            this,
                            uri
                    );

            if (previewRingtone == null) {
                return;
            }

            previewRingtone.play();

            webView.postDelayed(
                    this::stopPreview,
                    3000
            );

        } catch (Exception ignored) {
            stopPreview();
        }
    }

    private void stopPreview() {
        if (previewRingtone == null) {
            return;
        }

        try {
            if (previewRingtone.isPlaying()) {
                previewRingtone.stop();
            }
        } catch (Exception ignored) {
        }

        previewRingtone = null;
    }

    private void notifyWebsiteTefillinDone() {
        if (webView == null) {
            return;
        }

        webView.post(() ->
                webView.evaluateJavascript(
                        "window.dispatchEvent(" +
                                "new CustomEvent('tefillinDone')" +
                                ");",
                        null
                )
        );
    }

    private void notifyWebsiteOfNativeState() {
        if (webView == null) {
            return;
        }

        boolean done =
                isDoneToday();

        boolean active =
                prefs().getBoolean(
                        KEY_ALARM_ACTIVE,
                        false
                );

        long alarmTime =
                prefs().getLong(
                        KEY_ALARM_TIME,
                        0L
                );

        String javascript =
                "window.dispatchEvent(" +
                        "new CustomEvent(" +
                        "'androidNativeState'," +
                        "{detail:{" +
                        "done:" + done + "," +
                        "alarmActive:" + active + "," +
                        "alarmTime:" + alarmTime +
                        "}}" +
                        ")" +
                        ");";

        webView.post(() ->
                webView.evaluateJavascript(
                        javascript,
                        null
                )
        );
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void scheduleReminder(
                long timestamp
        ) {
            runOnUiThread(() ->
                    scheduleAlarm(
                            timestamp
                    )
            );
        }

        @JavascriptInterface
        public void setReminder(
                long timestamp
        ) {
            scheduleReminder(
                    timestamp
            );
        }

        @JavascriptInterface
        public void cancelReminder() {
            runOnUiThread(
                    MainActivity.this::cancelAlarm
            );
        }

        @JavascriptInterface
        public void chooseRingtone() {
            runOnUiThread(
                    MainActivity.this::showRingtoneChooser
            );
        }

        @JavascriptInterface
        public String getSelectedRingtone() {
            return prefs().getString(
                    KEY_RINGTONE,
                    ""
            );
        }

        @JavascriptInterface
        public void markTefillinDone() {
            runOnUiThread(
                    MainActivity.this::markDone
            );
        }

        @JavascriptInterface
        public boolean isTefillinDone() {
            return isDoneToday();
        }

        @JavascriptInterface
        public boolean isReminderActive() {
            return prefs().getBoolean(
                    KEY_ALARM_ACTIVE,
                    false
            );
        }

        @JavascriptInterface
        public long getReminderTime() {
            return prefs().getLong(
                    KEY_ALARM_TIME,
                    0L
            );
        }

        @JavascriptInterface
        public void snoozeTenMinutes() {
            runOnUiThread(
                    MainActivity.this::snoozeTenMinutes
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.post(() -> {
                webView.evaluateJavascript(
                        "window.dispatchEvent(" +
                                "new CustomEvent(" +
                                "'androidAppResumed'" +
                                ")" +
                                ");",
                        null
                );

                notifyWebsiteOfNativeState();
            });
        }
    }

    @Override
    protected void onDestroy() {
        stopPreview();

        if (webView != null) {
            webView.removeJavascriptInterface(
                    "Android"
            );

            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {
            super.onBackPressed();
        }
    }
}
