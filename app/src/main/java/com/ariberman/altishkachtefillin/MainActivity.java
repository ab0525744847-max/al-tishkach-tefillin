package com.ariberman.altishkachtefillin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
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
                            new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
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
    }

    private void scheduleAlarm(int hour, int minute) {

        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent =
                new Intent(this, ReminderReceiver.class);

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        5001,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                alarmManager.canScheduleExactAlarms()) {

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

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

        getSharedPreferences("reminder", MODE_PRIVATE)
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
                " if (window.__androidReminderConnected) return;" +
                " window.__androidReminderConnected = true;" +

                " function findTime() {" +
                "   var inputs = document.querySelectorAll('input[type=\"time\"]');" +
                "   for (var i = 0; i < inputs.length; i++) {" +
                "     if (inputs[i].value) return inputs[i].value;" +
                "   }" +
                "   return null;" +
                " }" +

                " function sendReminder() {" +
                "   var t = findTime();" +
                "   if (t && window.AndroidReminder) {" +
                "     AndroidReminder.scheduleReminder(t);" +
                "   }" +
                " }" +

                " document.addEventListener('click', function(e) {" +
                "   var el = e.target;" +
                "   var text = (el.innerText || el.textContent || '').trim();" +
                "   if (/שמור|קבע|הפעל|תזכורת/.test(text)) {" +
                "     setTimeout(sendReminder, 400);" +
                "   }" +
                " }, true);" +

                " document.addEventListener('change', function(e) {" +
                "   if (e.target && e.target.type === 'time') {" +
                "     setTimeout(sendReminder, 300);" +
                "   }" +
                " }, true);" +

                "})();";

        webView.evaluateJavascript(javascript, null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }
}
