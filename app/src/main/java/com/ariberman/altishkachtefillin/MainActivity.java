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

    private static final String KEY_OVERLAY_ASKED =
            "overlay_permission_asked";

    private static final int NOTIFICATION_PERMISSION_REQUEST =
            100;

    private static final int ALARM_REQUEST_CODE =
            5001;

    private WebView webView;
    private FrameLayout rootLayout;

    private Ringtone previewRingtone;

    @SuppressLint({
            "SetJavaScriptEnabled",
            "JavascriptInterface"
    })
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createScreen();
        configureWebView();

        askNotificationPermission();

        webView.loadUrl(WEBSITE_URL);

        /*
         * נותנים למסך הראשי להיפתח קודם,
         * ואז בודקים את הרשאת ההצגה מעל אפליקציות.
         */
        webView.postDelayed(
                this::checkOverlayPermission,
                1000
        );
    }

    private void createScreen() {

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
         * כפתור בחירת הצלצול.
         * הוא נשאר מעל האתר כדי שאפשר יהיה
         * לבחור צלצול גם בלי לשנות את קוד האתר.
         */
        Button ringtoneButton =
                new Button(this);

        ringtoneButton.setText(
                "🔔 צלצול"
        );

        ringtoneButton.setTextSize(14);

        ringtoneButton.setAllCaps(false);

        ringtoneButton.setTextColor(
                Color.rgb(
                        15,
                        25,
                        50
                )
        );

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
                Gravity.BOTTOM |
                        Gravity.END;

        buttonParams.setMargins(
                25,
                25,
                30,
                45
        );

        ringtoneButton.setLayoutParams(
                buttonParams
        );

        ringtoneButton.setOnClickListener(
                v -> showRingtoneChooser()
        );

        rootLayout.addView(
                ringtoneButton
        );

        setContentView(
                rootLayout
        );
    }

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

                        notifyWebsiteAppReady();
                    }
                }
        );

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );
    }

    /*
     * הרשאת התראות Android 13+
     */
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

    /*
     * הרשאת "הצגה מעל אפליקציות אחרות".
     *
     * Android לא מאפשר לאפליקציה לאשר אותה בעצמה.
     * המשתמש חייב להפעיל אותה במסך ההגדרות.
     */
    private void checkOverlayPermission() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.M) {
            return;
        }

        if (Settings.canDrawOverlays(this)) {
            return;
        }

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        boolean alreadyAsked =
                prefs.getBoolean(
                        KEY_OVERLAY_ASKED,
                        false
                );

        /*
         * בפעם הראשונה מציגים הסבר.
         * לאחר מכן המשתמש עדיין יכול לפתוח
         * את ההרשאה דרך Android Bridge.
         */
        if (!alreadyAsked) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "הרשאה לתזכורת"
                    )
                    .setMessage(
                            "כדי שמסך התזכורת יוכל להופיע מעל אפליקציות אחרות, צריך לאפשר לאפליקציה \"הצגה מעל אפליקציות אחרות\"."
                    )
                    .setPositiveButton(
                            "פתח הגדרות",
                            (dialog, which) -> {

                                prefs.edit()
                                        .putBoolean(
                                                KEY_OVERLAY_ASKED,
                                                true
                                        )
                                        .apply();

                                openOverlaySettings();
                            }
                    )
                    .setNegativeButton(
                            "לא עכשיו",
                            (dialog, which) -> {

                                prefs.edit()
                                        .putBoolean(
                                                KEY_OVERLAY_ASKED,
                                                true
                                        )
                                        .apply();
                            }
                    )
                    .setCancelable(false)
                    .show();
        }
    }

    private void openOverlaySettings() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.M) {
            return;
        }

        try {

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

            /*
             * גיבוי למכשירים שבהם היצרן
             * שינה את מסך ההרשאות.
             */
            try {

                Intent intent =
                        new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                        );

                startActivity(intent);

            } catch (Exception secondError) {

                Toast.makeText(
                        this,
                        "פתח בהגדרות את ההרשאה: הצגה מעל אפליקציות אחרות",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    /*
     * הרשאת תזכורות מדויקות Android 12+
     */
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

    /*
     * קביעת תזכורת.
     */
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

            Toast.makeText(
                    this,
                    "לא ניתן להפעיל את התזכורת",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S &&
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
                    getReminderPendingIntent()
            );

            getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
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

            notifyWebsiteReminderChanged(
                    triggerAtMillis,
                    true
            );

        } catch (SecurityException e) {

            requestExactAlarmPermissionIfNeeded();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "אירעה בעיה בשמירת התזכורת",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /*
     * ביטול תזכורת.
     */
    private void cancelAlarm() {

        AlarmManager alarmManager =
                (AlarmManager)
                        getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager != null) {

            try {

                alarmManager.cancel(
                        getReminderPendingIntent()
                );

            } catch (Exception ignored) {
            }
        }

        getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        )
                .edit()
                .putBoolean(
                        KEY_ALARM_ACTIVE,
                        false
                )
                .remove(
                        KEY_ALARM_TIME
                )
                .apply();

        notifyWebsiteReminderChanged(
                0,
                false
        );
    }

    /*
     * בחירת עד 10 צלצולי Alarm מהמכשיר.
     */
    private void showRingtoneChooser() {

        stopPreviewRingtone();

        Cursor cursor = null;

        try {

            RingtoneManager manager =
                    new RingtoneManager(this);

            manager.setType(
                    RingtoneManager.TYPE_ALARM
            );

            cursor =
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
                                ringtone.getTitle(
                                        this
                                );

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

                                if (which < 0 ||
                                        which >= uris.size()) {
                                    return;
                                }

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

                                notifyWebsiteRingtoneChanged();
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

        } finally {

            if (cursor != null) {

                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /*
     * השמעת דוגמה של הצלצול למשך 3 שניות.
     */
    private void previewRingtone(
            String uriString
    ) {

        stopPreviewRingtone();

        try {

            Uri uri =
                    Uri.parse(
                            uriString
                    );

            previewRingtone =
                    RingtoneManager.getRingtone(
                            this,
                  
