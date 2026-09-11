package com.mdjibon.scanner;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class FloatingScannerService extends Service {

    private static volatile boolean running = false;

    private WindowManager windowManager;

    private View logoView;
    private View scanView;
    private View resultView;

    private BroadcastReceiver receiver;

    private int savedX = 20;
    private int savedY = 250;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;

        windowManager =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        registerScannerReceiver();

        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(
                    2001,
                    new android.app.Notification.Builder(
                            this,
                            createChannel()
                    )
                            .setContentTitle(
                                    "MD JIBON Scanner"
                            )
                            .setContentText(
                                    "Floating scanner is active"
                            )
                            .setSmallIcon(
                                    android.R.drawable.ic_menu_search
                            )
                            .setOngoing(true)
                            .build()
            );
        }

        if (!Settings.canDrawOverlays(this)) {

            Toast.makeText(
                    this,
                    "Overlay permission দিন",
                    Toast.LENGTH_LONG
            ).show();

            stopSelf();
            return;
        }

        createLogo();
    }

    private String createChannel() {

        String channelId =
                "md_jibon_floating";

        if (Build.VERSION.SDK_INT >= 26) {

            android.app.NotificationChannel channel =
                    new android.app.NotificationChannel(
                            channelId,
                            "MD JIBON Floating Scanner",
                            android.app.NotificationManager
                                    .IMPORTANCE_LOW
                    );

            android.app.NotificationManager manager =
                    (android.app.NotificationManager)
                            getSystemService(
                                    NOTIFICATION_SERVICE
                            );

            if (manager != null) {
                manager.createNotificationChannel(
                        channel
                );
            }
        }

        return channelId;
    }

    private void registerScannerReceiver() {

        receiver =
                new BroadcastReceiver() {

                    @Override
                    public void onReceive(
                            Context context,
                            Intent intent
                    ) {

                        if (intent == null) {
                            return;
                        }

                        String action =
                                intent.getAction();

                        /*
                         * SAME ACTION AS ScreenCaptureService.
                         */
                        if (ScreenCaptureService
                                .ACTION_PROGRESS
                                .equals(action)) {

                            int progress =
                                    intent.getIntExtra(
                                            "progress",
                                            0
                                    );

                            updateProgress(
                                    progress
                            );

                        } else if (ScreenCaptureService
                                .ACTION_RESULT
                                .equals(action)) {

                            String signal =
                                    intent.getStringExtra(
                                            "signal"
                                    );

                            int confidence =
                                    intent.getIntExtra(
                                            "confidence",
                                            0
                                    );

                            int rules =
                                    intent.getIntExtra(
                                            "ruleCount",
                                            0
                                    );

                            int candles =
                                    intent.getIntExtra(
                                            "detectedCandles",
                                            0
                                    );

                            String timeframe =
                                    intent.getStringExtra(
                                            "timeframe"
                                    );

                            showResult(
                                    signal,
                                    confidence,
                                    rules,
                                    candles,
                                    timeframe
                            );
                        }
                    }
                };

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                ScreenCaptureService.ACTION_PROGRESS
        );

        filter.addAction(
                ScreenCaptureService.ACTION_RESULT
        );

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    receiver,
                    filter
            );
        }
    }

    private int dp(float value) {

        return (int)
                (value *
                        getResources()
                                .getDisplayMetrics()
                                .density +
                        0.5f);
    }

    private WindowManager.LayoutParams overlayParams() {

        int type;

        if (Build.VERSION.SDK_INT >= 26) {
            type =
                    WindowManager.LayoutParams
                            .TYPE_APPLICATION_OVERLAY;
        } else {
            type =
                    WindowManager.LayoutParams
                            .TYPE_PHONE;
        }

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        type,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams
                                .FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP |
                        Gravity.START;

        params.x = savedX;
        params.y = savedY;

        return params;
    }

    private void createLogo() {

        if (windowManager == null) {
            return;
        }

        if (logoView != null) {
            return;
        }

        /*
         * SMALL FLOATING ICON:
         * Previous size was too large.
         * 44dp keeps the same logo but makes it compact.
         */
        ImageView image =
                new ImageView(this);

        image.setImageResource(
                getResources()
                        .getIdentifier(
                                "md_jibon_logo",
                                "drawable",
                                getPackageName()
                        )
        );

        image.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        int size = dp(44);

        image.setLayoutParams(
                new android.view.ViewGroup.LayoutParams(
                        size,
                        size
                )
        );

        logoView = image;

        WindowManager.LayoutParams params =
                overlayParams();

        image.setOnTouchListener(
                new View.OnTouchListener() {

                    private int downX;
                    private int downY;
                    private int startX;
                    private int startY;
                    private long downTime;

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event
                    ) {

                        switch (event.getAction()) {

                            case MotionEvent.ACTION_DOWN:

                                downX =
                                        (int) event.getRawX();

                                downY =
                                        (int) event.getRawY();

                                startX = params.x;
                                startY = params.y;

                                downTime =
                                        System.currentTimeMillis();

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                int dx =
                                        (int) event.getRawX() -
                                                downX;

                                int dy =
                                        (int) event.getRawY() -
                                                downY;

                                params.x =
                                        startX + dx;

                                params.y =
                                        startY + dy;

                                try {
                                    windowManager.updateViewLayout(
                                            image,
                                            params
                                    );
                                } catch (Exception ignored) {
                                }

                                return true;

                            case MotionEvent.ACTION_UP:

                                savedX = params.x;
                                savedY = params.y;

                                long duration =
                                        System.currentTimeMillis() -
                                                downTime;

                                int totalMove =
                                        Math.abs(
                                                (int) event.getRawX() -
                                                        downX
                                        ) +
                                                Math.abs(
                                                        (int) event.getRawY() -
                                                                downY
                                                );

                                /*
                                 * Tap = short press + little movement.
                                 */
                                if (duration < 350 &&
                                        totalMove < dp(12)) {

                                    startScan();
                                }

                                return true;
                        }

                        return true;
                    }
                }
        );

        try {

            windowManager.addView(
                    image,
                    params
            );

        } catch (Exception e) {

            logoView = null;
        }
    }

    private void startScan() {

        if (!ScreenCaptureService
                .isCaptureActive()) {

            Toast.makeText(
                    this,
                    "আগে SCREEN CAPTURE ON করুন",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        removeView(
                logoView
        );

        createScanView();

        Intent scan =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        scan.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                startForegroundService(
                        scan
                );

            } else {

                startService(scan);
            }

        } catch (Exception e) {

            removeView(scanView);
            createLogo();

            Toast.makeText(
                    this,
                    "Scanner start হয়নি",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void createScanView() {

        TextView scan =
                new TextView(this);

        scan.setText(
                "SCANNING\n0 / 100"
        );

        scan.setTextColor(
                Color.WHITE
        );

        scan.setTextSize(
                18
        );

        scan.setGravity(
                Gravity.CENTER
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setColor(
                Color.rgb(
                        10,
                        18,
                        30
                )
        );

        bg.setCornerRadius(
                dp(18)
        );

        bg.setStroke(
                dp(2),
                Color.rgb(
                        40,
                        220,
                        255
                )
        );

        scan.setBackground(bg);

        int width = dp(155);
        int height = dp(90);

        scanView = scan;

        WindowManager.LayoutParams params =
                overlayParams();

        params.width = width;
        params.height = height;

        params.x =
                Math.max(
                        0,
                        savedX - dp(50)
                );

        params.y =
                Math.max(
                        0,
                        savedY - dp(20)
                );

        try {

            windowManager.addView(
                    scan,
                    params
            );

        } catch (Exception ignored) {
        }
    }

    private void updateProgress(
            int progress
    ) {

        if (!(scanView instanceof TextView)) {
            return;
        }

        TextView text =
                (TextView) scanView;

        text.setText(
                "SCANNING\n" +
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        progress
                                )
                        ) +
                        " / 100"
        );
    }

    private void showResult(
            String signal,
            int confidence,
            int rules,
            int candles,
            String timeframe
    ) {

        removeView(scanView);

        if (signal == null) {
            signal = "NO TRADE";
        }

        if (!"UP".equals(signal) &&
                !"DOWN".equals(signal) &&
                !"NO TRADE".equals(signal)) {

            signal = "NO TRADE";
        }

        String safeTimeframe =
                timeframe == null
                        ? "1 MIN"
                        : timeframe;

        TextView result =
                new TextView(this);

        String text;

        if ("UP".equals(signal)) {

            text =
                    "▲ UP\n" +
                            "Score: " +
                            confidence +
                            "%\n" +
                            "Rules: " +
                            rules +
                            "/100\n" +
                            "Candles: " +
                            candles;

        } else if ("DOWN".equals(signal)) {

            text =
                    "▼ DOWN\n" +
                            "Score: " +
                            confidence +
                            "%\n" +
                            "Rules: " +
                            rules +
                            "/100\n" +
                            "Candles: " +
                            candles;

        } else {

            text =
                    "WAIT\n" +
                            "NO TRADE\n" +
                            "Rules: " +
                            rules +
                            "/100\n" +
                            "Candles: " +
                            candles;
        }

        result.setText(text);

        result.setTextSize(17);

        result.setGravity(
                Gravity.CENTER
        );

        if ("UP".equals(signal)) {

            result.setTextColor(
                    Color.rgb(
                            30,
                            235,
                            135
                    )
            );

        } else if ("DOWN".equals(signal)) {

            result.setTextColor(
                    Color.rgb(
                            255,
                            70,
                            85
                    )
            );

        } else {

            result.setTextColor(
                    Color.WHITE
            );
        }

        GradientDrawable bg =
                new GradientDrawable();

        bg.setColor(
                Color.rgb(
                        8,
                        12,
                        20
                )
        );

        bg.setCornerRadius(
                dp(18)
        );

        bg.setStroke(
                dp(2),
                Color.rgb(
                        80,
                        80,
                        90
                )
        );

        result.setBackground(bg);

        result.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
        );

        resultView = result;

        WindowManager.LayoutParams params =
                overlayParams();

        params.width = dp(180);
        params.height = dp(130);

        params.x =
                Math.max(
                        0,
                        savedX - dp(60)
                );

        params.y =
                Math.max(
                        0,
                        savedY - dp(35)
                );

        try {

            windowManager.addView(
                    result,
                    params
            );

        } catch (Exception ignored) {
        }

        /*
         * Result remains visible for 4 seconds,
         * then floating logo comes back.
         */
        mainHandlerPostDelayed(
                () -> {

                    removeView(resultView);
                    resultView = null;

                    createLogo();

                },
                4000
        );
    }

    private void mainHandlerPostDelayed(
            Runnable runnable,
            long delay
    ) {

        new android.os.Handler(
                android.os.Looper.getMainLooper()
        ).postDelayed(
                runnable,
                delay
        );
    }

    private void removeView(
            View view
    ) {

        if (view == null ||
                windowManager == null) {
            return;
        }

        try {

            windowManager.removeView(view);

        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {

        running = false;

        if (receiver != null) {

            try {
                unregisterReceiver(
                        receiver
                );
            } catch (Exception ignored) {
            }

            receiver = null;
        }

        removeView(logoView);
        removeView(scanView);
        removeView(resultView);

        logoView = null;
        scanView = null;
        resultView = null;

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
