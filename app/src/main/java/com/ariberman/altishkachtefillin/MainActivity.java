package com.ariberman.altishkachtefillin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Calendar;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String WEBSITE =
            "https://mildly-puck-wcst1.shipped.cloud/";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        requestNotificationPermission();
        requestExactAlarmPermission();

        webView = findViewById(R.id.webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(
                new ReminderBridge(),
                "AndroidReminder"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                connectWebsiteToAndroid();
            }
        });

        webView.loadUrl(WEBSITE);
    }

    private void requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    100
            );
        }
    }

    private void requestExactAlarmPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            AlarmManager alarmManager =
                    (AlarmManager) getSystemService(ALARM_SERVICE);

            if (!alarmManager.canScheduleExactAlarms()) {

                try {

                    Intent intent =
                            new Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            );

                    startActivity(intent);

                } catch (Exception ignored) {
                }
            }
        }
    }

    public class ReminderBridge {

        @JavascriptInterface
        public void scheduleReminder(String time) {

            try {

                String[] parts = time.split(":");

                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                scheduleAlarm(hour, minute);

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "לא הצלחתי לקבוע את התזכורת",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }

        @JavascriptInterface
        public void tefillinDone() {

            stopAlarmNow();

        }
    }

    private void stopAlarmNow() {

        Intent stopIntent =
                new Intent(
                        this,
                        ReminderReceiver.class
                );

        stopIntent.setAction("STOP_ALARM");

        sendBroadcast(stopIntent);

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager != null) {
            manager.cancel(1001);
        }

        runOnUiThread(() ->
                Toast.makeText(
                        MainActivity.this,
                        "הצלצול הופסק ✓",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    private void scheduleAlarm(
            int hour,
            int minute
    ) {

        Calendar calendar =
                Calendar.getInstance();

        calendar.set(
                Calendar.HOUR_OF_DAY,
                hour
        );

        calendar.set(
                Calendar.MINUTE,
                minute
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );

        if (calendar.getTimeInMillis()
                <= System.currentTimeMillis()) {

            calendar.add(
                    Calendar.DAY_OF_YEAR,
                    1
            );
        }

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

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.S
                && alarmManager.canScheduleExactAlarms()) {

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );

        } else if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.M
        ) {

            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );

        } else {

            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }

        getSharedPreferences(
                "reminder",
                MODE_PRIVATE
        )
                .edit()
                .putInt("hour", hour)
                .putInt("minute", minute)
                .putBoolean("enabled", true)
                .apply();

        runOnUiThread(() ->
                Toast.makeText(
                        MainActivity.this,

                        String.format(
                                "🔔 התזכורת נקבעה ל־%02d:%02d",
                                hour,
                                minute
                        ),

                        Toast.LENGTH_LONG

                ).show()
        );
    }

    private void connectWebsiteToAndroid() {

        String javascript =

                "(function() {" +

                "if (window.__androidReminderConnected) return;" +
                "window.__androidReminderConnected = true;" +

                /*
                 * מציאת השעה שנבחרה
                 */
                "function findTime() {" +

                "var inputs = document.querySelectorAll(" +
                "'input[type=\"time\"]');" +

                "for (var i = 0; i < inputs.length; i++) {" +

                "if (inputs[i].value) {" +
                "return inputs[i].value;" +
                "}" +

                "}" +

                "return null;" +

                "}" +

                /*
                 * שליחת התזכורת לאנדרואיד
                 */
                "function sendReminder() {" +

                "var t = findTime();" +

                "if (t && window.AndroidReminder) {" +

                "AndroidReminder.scheduleReminder(t);" +

                "}" +

                "}" +

                /*
                 * מגביל את השעות
                 * זריחה -> 10 דקות לפני שקיעה
                 */
                "function limitReminderTime() {" +

                "var bodyText = document.body.innerText || '';" +

                "var sunriseMatch = " +
                "bodyText.match(/זריחה[^0-9]{0,30}([0-2]?[0-9]:[0-5][0-9])/);" +

                "var sunsetMatch = " +
                "bodyText.match(/שקיעה[^0-9]{0,30}([0-2]?[0-9]:[0-5][0-9])/);" +

                "var minTime = null;" +
                "var maxTime = null;" +

                "if (sunriseMatch) {" +

                "minTime = sunriseMatch[1];" +

                "if (minTime.length === 4) {" +
                "minTime = '0' + minTime;" +
                "}" +

                "}" +

                "if (sunsetMatch) {" +

                "var parts = sunsetMatch[1].split(':');" +

                "var totalMinutes = " +
                "parseInt(parts[0], 10) * 60 +" +
                "parseInt(parts[1], 10) - 10;" +

                "if (totalMinutes >= 0) {" +

                "var h = Math.floor(totalMinutes / 60);" +
                "var m = totalMinutes % 60;" +

                "maxTime =" +
                "('0' + h).slice(-2) +" +
                "':' +" +
                "('0' + m).slice(-2);" +

                "}" +

                "}" +

                "var inputs = document.querySelectorAll(" +
                "'input[type=\"time\"]');" +

                "for (var i = 0; i < inputs.length; i++) {" +

                "if (minTime) {" +
                "inputs[i].min = minTime;" +
                "}" +

                "if (maxTime) {" +
                "inputs[i].max = maxTime;" +
                "}" +

                "}" +

                "}" +

                /*
                 * לחיצה על כפתורים באתר
                 */
                "document.addEventListener(" +
                "'click'," +

                "function(e) {" +

                "var el = e.target;" +
                "var current = el;" +
                "var text = '';" +

                /*
                 * בודק גם את האלמנט
                 * וגם כמה הורים שלו
                 */
                "for (var i = 0; i < 4 && current; i++) {" +

                "text += ' ' +" +
                "((current.innerText || " +
                "current.textContent || '')" +
                ".trim());" +

                "current = current.parentElement;" +

                "}" +

                /*
                 * הנחתי תפילין
                 */
                "if (" +
                "/הנחתי\\s*תפילין|כבר\\s*הנחתי|הנחתי/" +
                ".test(text)" +
                ") {" +

                "if (window.AndroidReminder) {" +

                "AndroidReminder.tefillinDone();" +

                "}" +

                "return;" +

                "}" +

                /*
                 * שמירת תזכורת
                 */
                "var buttonText =" +
                "((el.innerText || " +
                "el.textContent || '')" +
                ".trim());" +

                "if (" +
                "/שמור|קבע|הפעל|תזכורת/" +
                ".test(buttonText)" +
                ") {" +

                "setTimeout(" +
                "sendReminder," +
                "400" +
                ");" +

                "}" +

                "}," +
                "true" +

                ");" +

                /*
                 * שינוי שעה
                 */
                "document.addEventListener(" +
                "'change'," +

                "function(e) {" +

                "if (" +
                "e.target && " +
                "e.target.type === 'time'" +
                ") {" +

                "setTimeout(" +
                "sendReminder," +
                "300" +
                ");" +

                "}" +

                "}," +
                "true" +

                ");" +

                /*
                 * הפעלת הגבלת השעות
                 */
                "limitReminderTime();" +

                "setTimeout(" +
                "limitReminderTime," +
                "1000" +
                ");" +

                "setTimeout(" +
                "limitReminderTime," +
                "3000" +
                ");" +

                "})();";

        webView.evaluateJavascript(
                javascript,
                null
        );
    }

    @Override
    public void onBackPressed() {

        if (
                webView != null &&
                webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            finish();

        }
    }
}
