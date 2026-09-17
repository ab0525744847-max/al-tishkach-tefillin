package com.ariberman.altishkachtefillin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
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

    // =========================================================
    // הגדרות כלליות
    // =========================================================

    private static final String WEBSITE_URL =
            "https://mildly-puck-wcst1.shipped.cloud/";

    private static final String PREFS =
            "tefillin_prefs";

    private static final String KEY_RINGTONE =
            "ringtone_uri";

    private static final String KEY_ALARM_TIME =
            "alarm_time";

    private static final String KEY_ALARM_ACTIVE =
            "alarm_active";

    private static final String KEY_DONE =
            "tefillin_done";

    private static final String KEY_DONE_DATE =
            "tefillin_done_date";

    private static final int ALARM_REQUEST_CODE =
            5001;

    private static final int NOTIFICATION_PERMISSION_REQUEST =
            1001;

    private WebView webView;
    private FrameLayout rootLayout;

    private Ringtone previewRingtone;

    private boolean permissionSetupStarted = false;
    private boolean returningFromSpecialPermission = false;

    // =========================================================
    // פתיחת האפליקציה
    // =========================================================

    @SuppressLint({
            "SetJavaScriptEnabled",
            "JavascriptInterface"
    })
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createMainScreen();
        configureWebView();

        webView.loadUrl(WEBSITE_URL);

        startPermissionSetup();
    }

    // =========================================================
    // יצירת המסך
    // =========================================================

    private void createMainScreen() {

        rootLayout =
                new FrameLayout(this);

        webView =
                new WebView(this);

        FrameLayout.LayoutParams webParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        rootLayout.addView(
                webView,
                webParams
        );

        /*
         * כפתור גלוי לבחירת הצלצול.
         * כך בחירת הצלצולים קיימת גם אם
         * האתר עצמו עדיין לא כולל כפתור.
         */
        Button ringtoneButton =
                new Button(this);

        ringtoneButton.setText(
                "🔔 צלצול"
        );

        ringtoneButton.setTextSize(14);
        ringtoneButton.setAllCaps(false);

        ringtoneButton.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        ringtoneButton.setTextColor(
                Color.rgb(
                        10,
                        25,
                        55
                )
        );

        FrameLayout.LayoutParams ringtoneParams =
                new FrameLayout.LayoutParams(
                        270,
                        125
                );

        ringtoneParams.gravity =
                Gravity.BOTTOM |
                        Gravity.END;

        ringtoneParams.setMargins(
                25,
                25,
                30,
                45
        );

        ringtoneButton.setLayoutParams(
                ringtoneParams
        );

        ringtoneButton.setOnClickListener(
                view -> showRingtoneChooser()
        );

        rootLayout.addView(
                ringtoneButton
        );

        setContentView(
                rootLayout
        );
    }

    // =========================================================
    // WebView
    // =========================================================

    @SuppressLint({
            "SetJavaScriptEnabled",
            "JavascriptInterface"
    })
    private void configureWebView() {

        webView.getSettings()
                .setJavaScriptEnabled(true);

        webView.getSettings()
                .setDomStorageEnabled(true);

        webView.getSettings()
                .setDatabaseEnabled(true);

        webView.getSettings()
                .setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {
                        super.onPageFinished(
                                view,
                                url
                        );

                        sendNativeStateToWebsite();

                        runJavascript(
                                "window.dispatchEvent(" +
                                        "new CustomEvent('androidAppReady')" +
                                        ");"
                        );
                    }
                }
        );

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );
    }

    // =========================================================
    // SharedPreferences
    // =========================================================

    private SharedPreferences prefs() {

        return getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        );
    }

    // =========================================================
    // מערכת האישורים
    // =========================================================

    private void startPermissionSetup() {

        if (permissionSetupStarted) {
            return;
        }

        permissionSetupStarted = true;

        /*
         * הרשאת Notifications היא הרשאת Runtime רגילה,
         * ולכן מתחילים ממנה.
         */
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

                return;
            }
        }

        showNextSpecialPermission();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode ==
                NOTIFICATION_PERMISSION_REQUEST) {

            showNextSpecialPermission();
        }
    }

    /*
     * בודק בכל פעם מהו האישור המיוחד הבא שחסר.
     */
    private void showNextSpecialPermission() {

        // -----------------------------------------------------
        // 1. תזכורות מדויקות
        // -----------------------------------------------------

        if (!canScheduleExactAlarms()) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "תזכורות מדויקות"
                    )
                    .setMessage(
                            "כדי שהתזכורת תצלצל בדיוק בשעה שבחרת, צריך לאפשר לאפליקציה שעונים ותזכורות מדויקים."
                    )
                    .setPositiveButton(
                            "אישור",
                            (dialog, which) ->
                                    openExactAlarmSettings()
                    )
                    .setNegativeButton(
                            "אחר כך",
                            (dialog, which) ->
                                    showOverlayPermissionIfNeeded()
                    )
                    .show();

            return;
        }

        showOverlayPermissionIfNeeded();
    }

    // =========================================================
    // Exact Alarm
    // =========================================================

    private boolean canScheduleExactAlarms() {

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

    private void openExactAlarmSettings() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.S) {

            showOverlayPermissionIfNeeded();
            return;
        }

        try {

            returningFromSpecialPermission = true;

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

        } catch (Exception e) {

            returningFromSpecialPermission = false;

            Toast.makeText(
                    this,
                    "לא ניתן לפתוח את הגדרת התזכורות המדויקות",
                    Toast.LENGTH_LONG
            ).show();

            showOverlayPermissionIfNeeded();
        }
    }

    // =========================================================
    // הצגה מעל אפליקציות אחרות
    // =========================================================

    private boolean canDrawOverOtherApps() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.M) {

            return true;
        }

        return Settings.canDrawOverlays(
                this
        );
    }

    private void showOverlayPermissionIfNeeded() {

        if (canDrawOverOtherApps()) {

            showFullScreenPermissionIfNeeded();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "הצגה מעל אפליקציות אחרות"
                )
                .setMessage(
                        "כדי שמסך התזכורת יוכל להופיע גם כשאתה נמצא באפליקציה אחרת, צריך לאפשר הצגה מעל אפליקציות אחרות."
                )
                .setPositiveButton(
                        "אישור",
                        (dialog, which) ->
                                openOverlaySettings()
                )
                .setNegativeButton(
                        "אחר כך",
                        (dialog, which) ->
                                showFullScreenPermissionIfNeeded()
                )
                .show();
    }

    private void openOverlaySettings() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.M) {

            showFullScreenPermissionIfNeeded();
            return;
        }

        try {

            returningFromSpecialPermission = true;

            Intent intent =
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    );

            intent.setData(
                    Uri.parse(
                            "package:" +
                                    getPackageName()
                    )
            );

            startActivity(intent);

        } catch (Exception firstError) {

            try {

                returningFromSpecialPermission = true;

                Intent intent =
                        new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                        );

                startActivity(intent);

            } catch (Exception secondError) {

                returningFromSpecialPermission = false;

                Toast.makeText(
                        this,
                        "לא ניתן לפתוח את הרשאת ההצגה מעל אפליקציות",
                        Toast.LENGTH_LONG
                ).show();

                showFullScreenPermissionIfNeeded();
            }
        }
    }

    // =========================================================
    // Full Screen Intent
    // =========================================================

    private boolean canUseFullScreenIntent() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

            return true;
        }

        try {

            NotificationManager notificationManager =
                    (NotificationManager)
                            getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            return notificationManager != null &&
                    notificationManager.canUseFullScreenIntent();

        } catch (Exception e) {

            return true;
        }
    }

    private void showFullScreenPermissionIfNeeded() {

        if (canUseFullScreenIntent()) {

            permissionSetupFinished();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "התראות במסך מלא"
                )
                .setMessage(
                        "כדי שהתזכורת תוכל לפתוח את מסך השעון המעורר כשהטלפון נעול או כשאפליקציה אחרת פתוחה, צריך לאפשר התראות במסך מלא."
                )
                .setPositiveButton(
                        "אישור",
                        (dialog, which) ->
                                openFullScreenIntentSettings()
                )
                .setNegativeButton(
                        "אחר כך",
                        (dialog, which) ->
                                permissionSetupFinished()
                )
                .show();
    }

    private void openFullScreenIntentSettings() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

            permissionSetupFinished();
            return;
        }

        try {

            returningFromSpecialPermission = true;

            Intent intent =
                    new Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                    );

            intent.setData(
                    Uri.parse(
                            "package:" +
                                    getPackageName()
                    )
            );

            startActivity(intent);

        } catch (Exception e) {

            returningFromSpecialPermission = false;

            Toast.makeText(
                    this,
                    "לא ניתן לפתוח את הגדרת המסך המלא",
                    Toast.LENGTH_LONG
            ).show();

            permissionSetupFinished();
        }
    }

    private void permissionSetupFinished() {

        Toast.makeText(
                this,
                "הגדרת ההרשאות הסתיימה",
                Toast.LENGTH_SHORT
        ).show();

        sendNativeStateToWebsite();
    }

    // =========================================================
    // PendingIntent
    // =========================================================

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

    // =========================================================
    // קביעת תזכורת
    // =========================================================

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
                    "זמן התזכורת כבר עבר",
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

            Toast.makeText(
                    this,
                    "לא ניתן להפעיל את התזכורת",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!canScheduleExactAlarms()) {

            openExactAlarmSettings();

            Toast.makeText(
                    this,
                    "אשר תזכורות מדויקות ואז שמור שוב את התזכורת",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        try {

            PendingIntent pendingIntent =
                    getReminderPendingIntent();

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

            Toast.makeText(
                    this,
                    "התזכורת נשמרה ✓",
                    Toast.LENGTH_SHORT
            ).show();

            sendNativeStateToWebsite();

        } catch (SecurityException e) {

            openExactAlarmSettings();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "לא ניתן לשמור את התזכורת",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =========================================================
    // ביטול תזכורת
    // =========================================================

    private void cancelAlarm() {

        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(
        
