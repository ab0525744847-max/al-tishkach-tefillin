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
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
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

    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private static final int ALARM_REQUEST_CODE = 5001;

    private WebView webView;
    private FrameLayout rootLayout;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createScreen();
        configureWebView();
        askNotificationPermission();

        webView.loadUrl(WEBSITE_URL);
    }

    private void createScreen() {

        rootLayout = new FrameLayout(this);

        webView = new WebView(this);

        FrameLayout.LayoutParams webParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        rootLayout.addView(webView, webParams);

        Button ringtoneButton = new Button(this);

        ringtoneButton.setText("🔔 צלצול");
        ringtoneButton.setTextSize(14);
        ringtoneButton.setAllCaps(false);
        ringtoneButton.setTextColor(Color.rgb(15, 25, 50));
        ringtoneButton.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        FrameLayout.LayoutParams buttonParams =
                new FrameLayout.LayoutParams(
                        260,
                        125
                );

        buttonParams.gravity =
                Gravity.BOTTOM | Gravity.END;

        buttonParams.setMargins(
                25,
                25,
                30,
                45
        );

        ringtoneButton.setLayoutParams(buttonParams);

        ringtoneButton.setOnClickListener(
                v -> showRingtoneChooser()
        );

        rootLayout.addView(ringtoneButton);

        setContentView(rootLayout);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(
                new WebViewClient()
        );

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );
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

    private void requestExactAlarmPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S) {

            AlarmManager alarmManager =
                    (AlarmManager)
                            getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (alarmManager != null &&
                    !alarmManager.canScheduleExactAlarms()) {

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

        if (isTefillinDoneToday()) {

            Toast.makeText(
                    this,
                    "כבר סימנת שהנחת תפילין היום",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (triggerAtMillis <=
                System.currentTimeMillis()) {

            Toast.makeText(
                    this,
                    "שעת התזכורת כבר עברה",
                    Toast.LENGTH_SHORT
            ).show();

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

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()) {

                requestExactAlarmPermissionIfNeeded();

                Toast.makeText(
                        this,
                        "צריך לאפשר תזכורות מדויקות",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    getReminderPendingIntent()
            );

            getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
                    .edit()
                    .putLong(
                            "alarm_time",
                            triggerAtMillis
                    )
                    .putBoolean(
                            "alarm_active",
                            true
                    )
                    .apply();

            Toast.makeText(
                    this,
                    "התזכורת נשמרה ✓",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (SecurityException e) {

            requestExactAlarmPermissionIfNeeded();
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

        getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        )
                .edit()
                .putBoolean(
                        "alarm_active",
                        false
                )
                .remove(
                        "alarm_time"
                )
                .apply();
    }

    private void showRingtoneChooser() {

        try {

            RingtoneManager manager =
                    new RingtoneManager(this);

            manager.setType(
                    RingtoneManager.TYPE_ALARM
            );

            Cursor cursor =
                    manager.getCursor();

            ArrayList<String> names =
                    new ArrayList<>();

            ArrayList<String> uris =
                    new ArrayList<>();

            int count = 0;

            while (cursor.moveToNext() &&
                    count < 10) {

                Uri uri =
                        manager.getRingtoneUri(
                                cursor.getPosition()
                        );

                if (uri == null) {
                    continue;
                }

                String title =
                        "צלצול " +
                                (count + 1);

                try {

                    Ringtone ringtone =
                            RingtoneManager.getRingtone(
                                    this,
                                    uri
                            );

                    if (ringtone != null) {

                        String phoneTitle =
                                ringtone.getTitle(this);

                        if (phoneTitle != null &&
                                !phoneTitle.trim().isEmpty()) {

                            title =
                                    phoneTitle;
                        }
                    }

                } catch (Exception ignored) {
                }

                names.add(
                        "🔔 " + title
                );

                uris.add(
                        uri.toString()
                );

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

            String[] options =
                    names.toArray(
                            new String[0]
                    );

            new AlertDialog.Builder(this)
                    .setTitle(
                            "בחר צלצול לתזכורת"
                    )
                    .setItems(
                            options,
                            (dialog, which) -> {

                                String selected =
                                        uris.get(which);

                                getSharedPreferences(
                                        PREFS,
                                        MODE_PRIVATE
                                )
                                        .edit()
                                        .putString(
                                                KEY_RINGTONE,
                                                selected
                                        )
                                        .apply();

                                previewRingtone(
                                        selected
                                );

                                Toast.makeText(
                                        this,
                                        "הצלצול נשמר ✓",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                    )
                    .setNegativeButton(
                            "ביטול",
                            null
                    )
                    .show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "לא ניתן לפתוח את רשימת הצלצולים",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void previewRingtone(
            String uriString
    ) {

        try {

            Uri uri =
                    Uri.parse(
                            uriString
                    );

            Ringtone ringtone =
                    RingtoneManager.getRingtone(
                            this,
                            uri
                    );

            if (ringtone == null) {
                return;
            }

            ringtone.play();

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

        } catch (Exception ignored) {
        }
    }

    private void markTefillinDone() {

        String today =
                getTodayKey();

        cancelAlarm();

        getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        )
                .edit()
                .putBoolean(
                        "tefillin_done",
                        true
                )
                .putString(
                        "tefillin_done_date",
                        today
                )
                .putBoolean(
                        "alarm_active",
                        false
                )
                .remove(
                        "alarm_time"
                )
                .apply();

        runOnUiThread(
                () -> {

                    if (webView != null) {

                        webView.evaluateJavascript(
                                "window.dispatchEvent(new CustomEvent('tefillinDone'));",
                                null
                        );
                    }
                }
        );
    }

    private boolean isTefillinDoneToday() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        String savedDate =
                prefs.getString(
                        "tefillin_done_date",
                        ""
                );

        return getTodayKey()
                .equals(savedDate);
    }

    private String getTodayKey() {

        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(
                new Date()
        );
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void scheduleReminder(
                long timestamp
        ) {

            runOnUiThread(
                    () -> scheduleAlarm(
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

            return getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            ).getString(
                    KEY_RINGTONE,
                    ""
            );
        }

        @JavascriptInterface
        public void markTefillinDone() {

            MainActivity.this
                    .markTefillinDone();
        }

        @JavascriptInterface
        public boolean isTefillinDone() {

            return isTefillinDoneToday();
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

        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
