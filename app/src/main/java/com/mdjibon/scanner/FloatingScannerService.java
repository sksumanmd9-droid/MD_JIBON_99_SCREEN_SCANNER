package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class FloatingScannerService extends Service {

    public static final String ACTION_SCAN =
            "com.mdjibon.scanner.ACTION_FLOATING_SCAN";

    public static final String ACTION_START_CONTINUOUS =
            "com.mdjibon.scanner.ACTION_START_CONTINUOUS";

    public static final String ACTION_AUTO_SCAN =
            "com.mdjibon.scanner.ACTION_AUTO_SCAN";

    public static final String ACTION_AUTO_SCAN_NOW =
            "com.mdjibon.scanner.ACTION_AUTO_SCAN_NOW";

    private static final float MIN_SIGNAL_SCORE = 90.0f;

    private static final long RESULT_HIDE_DELAY =
            12000L;

    private static volatile boolean running =
            false;

    private WindowManager windowManager;

    private LinearLayout bubble;

    private ImageView iconView;

    private TextView resultBadge;

    private WindowManager.LayoutParams bubbleParams;

    private Handler handler;

    private View scanOverlay;

    private boolean scanning =
            false;

    private long lastTapTime =
            0L;

    public static boolean isRunning() {
        return running;
    }

    private final BroadcastReceiver receiver =
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

                    if (
                            ScreenCaptureService.ACTION_SCAN_STATUS
                                    .equals(action)
                    ) {

                        String state =
                                intent.getStringExtra(
                                        "state"
                                );

                        if (
                                "working".equals(
                                        state
                                )
                        ) {

                            showScanOverlay();

                        } else if (
                                "done".equals(
                                        state
                                )
                        ) {

                            hideScanOverlay();
                        }

                        return;
                    }

                    if (
                            ScreenCaptureService.ACTION_RESULT
                                    .equals(action)
                    ) {

                        hideScanOverlay();

                        scanning =
                                false;

                        boolean strong =
                                intent.getBooleanExtra(
                                        "strong",
                                        false
                                );

                        boolean chartDetected =
                                intent.getBooleanExtra(
                                        "chartDetected",
                                        false
                                );

                        String signal =
                                intent.getStringExtra(
                                        "signal"
                                );

                        float score =
                                intent.getFloatExtra(
                                        "score",
                                        0.0f
                                );

                        if (
                                strong
                                        && chartDetected
                                        && score >= MIN_SIGNAL_SCORE
                                        && (
                                        "UP".equals(
                                                signal
                                        )
                                                || "DOWN".equals(
                                                signal
                                        )
                                )
                        ) {

                            showFinalResult(
                                    signal,
                                    score
                            );

                        } else {

                            /*
                             * No weak UP/DOWN.
                             */
                            hideFinalResult();

                            Toast.makeText(
                                    FloatingScannerService.this,
                                    "NO STRONG SIGNAL",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        return;
                    }

                    if (
                            ScreenCaptureService.ACTION_ERROR
                                    .equals(action)
                    ) {

                        hideScanOverlay();

                        scanning =
                                false;

                        String message =
                                intent.getStringExtra(
                                        "message"
                                );

                        if (
                                message == null
                                        || message.trim().isEmpty()
                        ) {
                            message =
                                    "Market screen is not ready.";
                        }

                        Toast.makeText(
                                FloatingScannerService.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            };

    @Override
    public void onCreate() {

        super.onCreate();

        handler =
                new Handler(
                        Looper.getMainLooper()
                );

        startForegroundCompat();

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                ScreenCaptureService.ACTION_RESULT
        );

        filter.addAction(
                ScreenCaptureService.ACTION_SCAN_STATUS
        );

        filter.addAction(
                ScreenCaptureService.ACTION_ERROR
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

        running =
                true;

        if (
                Settings.canDrawOverlays(
                        this
                )
        ) {
            createBubble();
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (
                bubble == null
                        && Settings.canDrawOverlays(
                        this
                )
        ) {
            createBubble();
        }

        if (
                intent != null
                        && (
                        ACTION_SCAN.equals(
                                intent.getAction()
                        )
                                || ACTION_START_CONTINUOUS.equals(
                                intent.getAction()
                        )
                                || ACTION_AUTO_SCAN_NOW.equals(
                                intent.getAction()
                        )
                )
        ) {

            startScan();
        }

        return START_STICKY;
    }

    // ============================================================
    // SCAN
    // ============================================================

    private void startScan() {

        if (scanning) {
            return;
        }

        /*
         * Screen capture must be active.
         */
        if (
                !ScreenCaptureService
                        .isCaptureActive()
        ) {

            Toast.makeText(
                    this,
                    "OPEN MARKET CHART FIRST",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        scanning =
                true;

        hideFinalResult();

        showScanOverlay();

        Intent scanIntent =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        scanIntent.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        if (bubbleParams != null) {

            scanIntent.putExtra(
                    "excludeX",
                    bubbleParams.x
            );

            scanIntent.putExtra(
                    "excludeY",
                    bubbleParams.y
            );

            scanIntent.putExtra(
                    "excludeW",
                    bubbleParams.width
            );

            scanIntent.putExtra(
                    "excludeH",
                    bubbleParams.height
            );
        }

        if (Build.VERSION.SDK_INT >= 26) {

            startForegroundService(
                    scanIntent
            );

        } else {

            startService(
                    scanIntent
            );
        }
    }

    // ============================================================
    // BUBBLE
    // ============================================================

    private void createBubble() {

        if (
                bubble != null
                        || !Settings.canDrawOverlays(
                        this
                )
        ) {
            return;
        }

        windowManager =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        bubble =
                new LinearLayout(this);

        bubble.setOrientation(
                LinearLayout.VERTICAL
        );

        bubble.setGravity(
                Gravity.CENTER
        );

        // --------------------------------------------------------
        // ICON
        // --------------------------------------------------------

        iconView =
                new ImageView(this);

        try {

            iconView.setImageResource(
                    R.drawable.md_jibon_logo
            );

        } catch (Exception ignored) {

            iconView.setImageDrawable(null);

            GradientDrawable fallback =
                    new GradientDrawable();

            fallback.setShape(
                    GradientDrawable.OVAL
            );

            fallback.setColor(
                    Color.rgb(
                            5,
                            20,
                            30
                    )
            );

            fallback.setStroke(
                    dp(2),
                    Color.rgb(
                            60,
                            240,
                            165
                    )
            );

            iconView.setBackground(
                    fallback
            );
        }

        iconView.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        bubble.addView(
                iconView,
                new LinearLayout.LayoutParams(
                        dp(64),
                        dp(64)
                )
        );

        // --------------------------------------------------------
        // RESULT BADGE
        // --------------------------------------------------------

        resultBadge =
                new TextView(this);

        resultBadge.setGravity(
                Gravity.CENTER
        );

        resultBadge.setTextSize(
                10
        );

        resultBadge.setTextColor(
                Color.WHITE
        );

        resultBadge.setVisibility(
                View.GONE
        );

        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(
                        dp(100),
                        dp(30)
                );

        badgeParams.topMargin =
                dp(3);

        bubble.addView(
                resultBadge,
                badgeParams
        );

        // --------------------------------------------------------
        // WINDOW
        // --------------------------------------------------------

        bubbleParams =
                new WindowManager.LayoutParams(
                        dp(105),
                        dp(98),
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams
                                .TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams
                                .TYPE_PHONE,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams
                                .FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        bubbleParams.gravity =
                Gravity.TOP
                        | Gravity.START;

        android.content.SharedPreferences pref =
                getSharedPreferences(
                        "scanner_settings",
                        MODE_PRIVATE
                );

        bubbleParams.x =
                pref.getInt(
                        "bubbleX",
                        dp(15)
                );

        bubbleParams.y =
                pref.getInt(
                        "bubbleY",
                        dp(280)
                );

        // --------------------------------------------------------
        // TOUCH / DRAG
        // --------------------------------------------------------

        bubble.setOnTouchListener(
                new View.OnTouchListener() {

                    float startRawX;
                    float startRawY;

                    int startX;
                    int startY;

                    long downTime;

                    boolean moved;

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event
                    ) {

                        switch (
                                event.getActionMasked()
                        ) {

                            case MotionEvent.ACTION_DOWN:

                                startRawX =
                                        event.getRawX();

                                startRawY =
                                        event.getRawY();

                                startX =
                                        bubbleParams.x;

                                startY =
                                        bubbleParams.y;

                                downTime =
                                        System.currentTimeMillis();

                                moved =
                                        false;

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                float dx =
                                        event.getRawX()
                                                - startRawX;

                                float dy =
                                        event.getRawY()
                                                - startRawY;

                                if (
                                        Math.abs(dx)
                                                > dp(5)
                                                || Math.abs(dy)
                                                > dp(5)
                                ) {

                                    moved =
                                            true;
                                }

                                bubbleParams.x =
                                        Math.max(
                                                0,
                                                startX
                                                        + (int) dx
                                        );

                                bubbleParams.y =
                                        Math.max(
                                                0,
                                                startY
                                                        + (int) dy
                                        );

                                saveBubblePosition();

                                try {

                                    windowManager
                                            .updateViewLayout(
                                                    bubble,
                                                    bubbleParams
                                            );

                                } catch (Exception ignored) {}

                                return true;

                            case MotionEvent.ACTION_UP:

                                long duration =
                                        System.currentTimeMillis()
                                                - downTime;

                                if (
                                        !moved
                                                && duration < 450
                                ) {

                                    /*
                                     * Floating Icon tap
                                     * ALWAYS starts scan.
                                     */
                                    long now =
                                            System.currentTimeMillis();

                                    if (
                                            now
                                                    - lastTapTime
                                                    > 500
                                    ) {

                                        lastTapTime =
                                                now;

                                        startScan();
                                    }
                                }

                                return true;
                        }

                        return true;
                    }
                }
        );

        try {

            windowManager.addView(
                    bubble,
                    bubbleParams
            );

        } catch (Exception e) {

            bubble = null;
        }
    }

    private void saveBubblePosition() {

        if (bubbleParams == null) {
            return;
        }

        getSharedPreferences(
                "scanner_settings",
                MODE_PRIVATE
        )
                .edit()
                .putInt(
                        "bubbleX",
                        bubbleParams.x
                )
                .putInt(
                        "bubbleY",
                        bubbleParams.y
                )
                .apply();
    }

    // ============================================================
    // FINAL RESULT
    // ============================================================

    private void showFinalResult(
            String signal,
            float score
    ) {

        if (resultBadge == null) {
            return;
        }

        resultBadge.setText(
                signal
                        + " "
                        + String.format(
                        Locale.US,
                        "%.0f%%",
                        score
                )
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setCornerRadius(
                dp(8)
        );

        if ("UP".equals(signal)) {

            background.setColor(
                    Color.rgb(
                            0,
                            125,
                            70
                    )
            );

            background.setStroke(
                    dp(1),
                    Color.rgb(
                            80,
                            255,
                            175
                    )
            );

        } else {

            background.setColor(
                    Color.rgb(
                            150,
                            25,
                            40
                    )
            );

            background.setStroke(
                    dp(1),
                    Color.rgb(
                            255,
                            90,
                            100
                    )
            );
        }

        resultBadge.setBackground(
                background
        );

        resultBadge.setVisibility(
                View.VISIBLE
        );

        /*
         * Result remains beside icon for a while.
         * User can tap icon again for a new scan.
         */
        handler.postDelayed(
                this::hideFinalResult,
                RESULT_HIDE_DELAY
        );
    }

    private void hideFinalResult() {

        if (resultBadge != null) {

            resultBadge.setVisibility(
                    View.GONE
            );
        }
    }

    // ============================================================
    // BLUE SCAN OVERLAY
    // ============================================================

    private void showScanOverlay() {

        if (
                scanOverlay != null
                        || windowManager == null
                        || !Settings.canDrawOverlays(
                        this
                )
        ) {
            return;
        }

        scanOverlay =
                new ScanOverlay(this);

        WindowManager.LayoutParams overlayParams =
                new WindowManager.LayoutParams(
                        -1,
                        -1,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams
                                .TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams
                                .TYPE_PHONE,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams
                                .FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams
                                .FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        overlayParams.gravity =
                Gravity.TOP
                        | Gravity.START;

        try {

            windowManager.addView(
                    scanOverlay,
                    overlayParams
            );

        } catch (Exception e) {

            scanOverlay = null;
        }
    }

    private void hideScanOverlay() {

        if (scanOverlay != null) {

            try {

                windowManager.removeView(
                        scanOverlay
                );

            } catch (Exception ignored) {}

            scanOverlay = null;
        }
    }

    // ============================================================
    // FOREGROUND
    // ============================================================

    private void startForegroundCompat() {

        String channelId =
                "md_jibon_floating";

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if (Build.VERSION.SDK_INT >= 26) {

            manager.createNotificationChannel(
                    new NotificationChannel(
                            channelId,
                            "MD JIBON Floating Scanner",
                            NotificationManager.IMPORTANCE_LOW
                    )
            );
        }

        Notification.Builder builder =
                Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(
                        this,
                        channelId
                )
                        : new Notification.Builder(this);

        startForeground(
                9903,
                builder
                        .setContentTitle(
                                "MD JIBON Scanner"
                        )
                        .setContentText(
                                "Floating market scanner active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_search
                        )
                        .build()
        );
    }

    // ============================================================
    // DP
    // ============================================================

    private int dp(
            int value
    ) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @Override
    public void onDestroy() {

        running =
                false;

        scanning =
                false;

        hideScanOverlay();

        hideFinalResult();

        try {
            unregisterReceiver(
                    receiver
            );
        } catch (Exception ignored) {}

        if (
                bubble != null
                        && windowManager != null
        ) {

            try {

                windowManager.removeView(
                        bubble
                );

            } catch (Exception ignored) {}
        }

        bubble = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {
        return null;
    }

    // ============================================================
    // OVERLAY VIEW
    // ============================================================

    private static final class ScanOverlay
            extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private final Handler animationHandler =
                new Handler(
                        Looper.getMainLooper()
                );

        private float scanY =
                -50.0f;

        private final Runnable animation =
                new Runnable() {

                    @Override
                    public void run() {

                        scanY +=
                                Math.max(
                                        9.0f,
                                        getResources()
                                                .getDisplayMetrics()
                                                .heightPixels
                                                / 45.0f
                                );

                        if (
                                scanY
                                        > getHeight()
                                                + 60
                        ) {

                            scanY =
                                    -60;
                        }

                        invalidate();

                        animationHandler
                                .postDelayed(
                                        this,
                                        28
                                );
                    }
                };

        ScanOverlay(
                Context context
        ) {

            super(context);

            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );

            animationHandler.post(
                    animation
            );
        }

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(canvas);

            paint.setStyle(
                    Paint.Style.FILL
            );

            /*
             * Full blue translucent layer.
             */
            paint.setColor(
                    Color.argb(
                            48,
                            0,
                            100,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    paint
            );

            /*
             * Moving blue scan trail.
             */
            paint.setColor(
                    Color.argb(
                            55,
                            40,
                            140,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    Math.max(
                            0,
                            scanY - 120
                    ),
                    getWidth(),
                    scanY,
                    paint
            );

            /*
             * Main scan line.
             */
            paint.setColor(
                    Color.argb(
                            215,
                            45,
                            150,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    scanY - 5,
                    getWidth(),
                    scanY + 5,
                    paint
            );

            paint.setColor(
                    Color.argb(
                            130,
                            110,
                            210,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    scanY - 1,
                    getWidth(),
                    scanY + 1,
                    paint
            );
        }

        @Override
        protected void onDetachedFromWindow() {

            animationHandler
                    .removeCallbacks(
                            animation
                    );

            super.onDetachedFromWindow();
        }
    }
}
