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

    /*
     * MainActivity uses this action to start the continuous
     * finite scanning session.
     */
    public static final String ACTION_START_CONTINUOUS =
            "com.mdjibon.scanner.ACTION_START_CONTINUOUS";

    private static final int MIN_SCANS_BEFORE_SIGNAL = 3;
    private static final int MAX_SCANS = 5;

    /*
     * Final signal requires strong analyzer evidence.
     */
    private static final float MIN_SIGNAL_SCORE = 90.0f;

    /*
     * Same direction must be confirmed twice.
     */
    private static final int SAME_DIRECTION_CONFIRMATIONS = 2;

    private static final long SIGNAL_COOLDOWN_MS = 2500L;
    private static final long SCAN_INTERVAL_MS = 2600L;
    private static final long OVERLAY_MS = 1450L;

    private static volatile boolean running = false;

    private WindowManager wm;

    private LinearLayout bubble;

    private TextView badge;

    private WindowManager.LayoutParams params;

    private Handler handler;

    private View scanOverlay;

    private Runnable loop;

    private boolean continuous = false;

    private boolean scanBusy = false;

    private int scanCount = 0;

    private String lastStrongSignal = "";

    private int sameStrongCount = 0;

    private long lastAnnouncedAt = 0L;

    // ============================================================
    // RUNNING STATE
    // ============================================================

    public static boolean isRunning() {
        return running;
    }

    // ============================================================
    // BROADCAST RECEIVER
    // ============================================================

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

                    // ------------------------------------------------
                    // SCAN STATUS
                    // ------------------------------------------------

                    if (
                            ScreenCaptureService.ACTION_SCAN_STATUS
                                    .equals(action)
                    ) {

                        String state =
                                intent.getStringExtra(
                                        "state"
                                );

                        if ("working".equals(state)) {

                            showScanOverlay();

                        } else if ("done".equals(state)) {

                            hideScanOverlay();

                            scanBusy = false;
                        }

                        return;
                    }

                    // ------------------------------------------------
                    // ANALYZER RESULT
                    // ------------------------------------------------

                    if (
                            ScreenCaptureService.ACTION_RESULT
                                    .equals(action)
                    ) {

                        hideScanOverlay();

                        scanBusy = false;

                        String signal =
                                intent.getStringExtra(
                                        "signal"
                                );

                        float score =
                                intent.getFloatExtra(
                                        "score",
                                        0f
                                );

                        boolean analyzerStrong =
                                intent.getBooleanExtra(
                                        "strong",
                                        false
                                );

                        if (signal == null) {
                            return;
                        }

                        signal =
                                signal.trim()
                                        .toUpperCase(
                                                Locale.US
                                        );

                        if (
                                !signal.equals("UP")
                                        && !signal.equals("DOWN")
                        ) {
                            return;
                        }

                        /*
                         * A strong result must pass the
                         * configured minimum score.
                         */
                        if (
                                analyzerStrong
                                        && score >= MIN_SIGNAL_SCORE
                        ) {

                            if (
                                    signal.equals(
                                            lastStrongSignal
                                    )
                            ) {

                                sameStrongCount++;

                            } else {

                                lastStrongSignal =
                                        signal;

                                sameStrongCount = 1;
                            }

                            /*
                             * Final signal is not allowed
                             * before three completed scans.
                             *
                             * Also require the same direction
                             * to be confirmed twice.
                             */
                            if (
                                    scanCount
                                            >= MIN_SCANS_BEFORE_SIGNAL
                                            && sameStrongCount
                                            >= SAME_DIRECTION_CONFIRMATIONS
                                            && System.currentTimeMillis()
                                            - lastAnnouncedAt
                                            >= SIGNAL_COOLDOWN_MS
                            ) {

                                lastAnnouncedAt =
                                        System.currentTimeMillis();

                                showBadge(
                                        signal,
                                        score
                                );

                                continuous = false;

                                stopLoopOnly();

                                Toast.makeText(
                                        FloatingScannerService.this,
                                        signal
                                                + "  "
                                                + String.format(
                                                Locale.US,
                                                "%.0f%%",
                                                score
                                        )
                                                + " • SCAN STOPPED",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }

                        /*
                         * Five scans is the hard maximum.
                         *
                         * If no strong result was confirmed,
                         * remove the badge and stop.
                         */
                        if (
                                scanCount >= MAX_SCANS
                                        && continuous
                        ) {

                            continuous = false;

                            stopLoopOnly();

                            hideBadge();
                        }

                        return;
                    }

                    // ------------------------------------------------
                    // SCAN ERROR
                    // ------------------------------------------------

                    if (
                            ScreenCaptureService.ACTION_ERROR
                                    .equals(action)
                    ) {

                        scanBusy = false;

                        hideScanOverlay();
                    }
                }
            };

    // ============================================================
    // SERVICE CREATE
    // ============================================================

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

        if (
                Settings.canDrawOverlays(this)
        ) {

            createBubble();
        }

        running = true;
    }

    // ============================================================
    // START COMMAND
    // ============================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (
                bubble == null
                        && Settings.canDrawOverlays(this)
        ) {

            createBubble();
        }

        if (
                intent != null
                        && ACTION_START_CONTINUOUS
                        .equals(
                                intent.getAction()
                        )
        ) {

            startContinuous();
        }

        return START_STICKY;
    }

    // ============================================================
    // START CONTINUOUS
    // ============================================================

    private void startContinuous() {

        if (
                !ScreenCaptureService
                        .isCaptureActive()
        ) {

            Toast.makeText(
                    this,
                    "Start screen capture first.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (continuous) {
            return;
        }

        continuous = true;

        scanBusy = false;

        scanCount = 0;

        lastStrongSignal = "";

        sameStrongCount = 0;

        lastAnnouncedAt = 0L;

        /*
         * Do not show a premature UP/DOWN result.
         */
        hideBadge();

        if (loop != null) {

            handler.removeCallbacks(
                    loop
            );
        }

        loop =
                new Runnable() {

                    @Override
                    public void run() {

                        if (
                                !continuous
                                        || !running
                        ) {

                            return;
                        }

                        if (
                                scanCount
                                        >= MAX_SCANS
                        ) {

                            continuous = false;

                            stopLoopOnly();

                            hideBadge();

                            return;
                        }

                        requestOneScan();

                        if (
                                continuous
                                        && scanCount
                                        < MAX_SCANS
                        ) {

                            handler.postDelayed(
                                    this,
                                    SCAN_INTERVAL_MS
                            );
                        }
                    }
                };

        /*
         * First scan starts immediately.
         */
        requestOneScan();

        /*
         * Following scans are spaced apart.
         */
        handler.postDelayed(
                loop,
                SCAN_INTERVAL_MS
        );
    }

    // ============================================================
    // REQUEST ONE SCAN
    // ============================================================

    private void requestOneScan() {

        if (
                !continuous
                        || scanBusy
                        || !ScreenCaptureService
                        .isCaptureActive()
        ) {

            return;
        }

        if (scanCount >= MAX_SCANS) {

            continuous = false;

            stopLoopOnly();

            hideBadge();

            return;
        }

        scanBusy = true;

        scanCount++;

        /*
         * Never show a fake/premature signal.
         */
        hideBadge();

        Intent intent =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        intent.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        /*
         * Exclude the floating bubble from the
         * screenshot analysis.
         */
        if (params != null) {

            intent.putExtra(
                    "excludeX",
                    params.x
            );

            intent.putExtra(
                    "excludeY",
                    params.y
            );

            intent.putExtra(
                    "excludeW",
                    params.width
            );

            intent.putExtra(
                    "excludeH",
                    params.height
            );
        }

        /*
         * Capture the clean frame first.
         * The blue overlay is shown to the user
         * while that frame is being analyzed.
         */
        try {

            if (Build.VERSION.SDK_INT >= 26) {

                startForegroundService(
                        intent
                );

            } else {

                startService(
                        intent
                );
            }

        } catch (Exception e) {

            scanBusy = false;

            hideScanOverlay();

            Toast.makeText(
                    this,
                    "Unable to start screen scan.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        showScanOverlay();

        handler.postDelayed(
                this::hideScanOverlay,
                OVERLAY_MS
        );
    }

    // ============================================================
    // STOP LOOP
    // ============================================================

    private void stopLoopOnly() {

        continuous = false;

        scanBusy = false;

        if (loop != null) {

            handler.removeCallbacks(
                    loop
            );

            loop = null;
        }

        hideScanOverlay();
    }

    // ============================================================
    // STOP EVERYTHING
    // ============================================================

    private void stopEverything() {

        stopLoopOnly();

        hideBadge();
    }

    // ============================================================
    // CREATE FLOATING BUBBLE
    // ============================================================

    private void createBubble() {

        if (
                bubble != null
                        || !Settings.canDrawOverlays(this)
        ) {

            return;
        }

        wm =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        bubble =
                new LinearLayout(this);

        bubble.setGravity(
                Gravity.CENTER
        );

        bubble.setOrientation(
                LinearLayout.VERTICAL
        );

        // --------------------------------------------------------
        // LOGO
        // --------------------------------------------------------

        ImageView icon =
                new ImageView(this);

        icon.setImageResource(
                R.drawable.md_jibon_logo
        );

        icon.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        icon.setBackgroundColor(
                Color.TRANSPARENT
        );

        bubble.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(68),
                        dp(68)
                )
        );

        // --------------------------------------------------------
        // BADGE
        // --------------------------------------------------------

        badge =
                new TextView(this);

        badge.setText(
                "SCAN"
        );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setTextSize(
                10
        );

        badge.setTextColor(
                Color.WHITE
        );

        GradientDrawable badgeBg =
                new GradientDrawable();

        badgeBg.setColor(
                Color.rgb(
                        8,
                        20,
                        31
                )
        );

        badgeBg.setStroke(
                dp(1),
                Color.rgb(
                        55,
                        170,
                        235
                )
        );

        badgeBg.setCornerRadius(
                dp(8)
        );

        badge.setBackground(
                badgeBg
        );

        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(
                        dp(72),
                        dp(28)
                );

        badgeParams.topMargin =
                dp(2);

        bubble.addView(
                badge,
                badgeParams
        );

        /*
         * Hidden until a real strong result.
         */
        badge.setVisibility(
                View.GONE
        );

        // --------------------------------------------------------
        // WINDOW PARAMS
        // --------------------------------------------------------

        params =
                new WindowManager.LayoutParams(
                        dp(76),
                        dp(92),

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

        params.gravity =
                Gravity.TOP
                        | Gravity.START;

        // --------------------------------------------------------
        // SAVED POSITION
        // --------------------------------------------------------

        android.content.SharedPreferences pref =
                getSharedPreferences(
                        "scanner_settings",
                        MODE_PRIVATE
                );

        params.x =
                pref.getInt(
                        "bubbleX",
                        dp(15)
                );

        params.y =
                pref.getInt(
                        "bubbleY",
                        dp(280)
                );

        // --------------------------------------------------------
        // ICON CLICK
        // --------------------------------------------------------

        icon.setOnClickListener(
                v -> {

                    if (continuous) {

                        stopEverything();

                    } else {

                        startContinuous();
                    }
                }
        );

        // --------------------------------------------------------
        // DRAG
        // --------------------------------------------------------

        bubble.setOnTouchListener(
                new View.OnTouchListener() {

                    float startX;
                    float startY;

                    int startParamX;
                    int startParamY;

                    long downTime;

                    boolean moved;

                    @Override
                    public boolean onTouch(
                            View view,
                            MotionEvent event
                    ) {

                        switch (
                                event.getActionMasked()
                        ) {

                            case MotionEvent.ACTION_DOWN:

                                startX =
                                        event.getRawX();

                                startY =
                                        event.getRawY();

                                startParamX =
                                        params.x;

                                startParamY =
                                        params.y;

                                downTime =
                                        System.currentTimeMillis();

                                moved = false;

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                float dx =
                                        event.getRawX()
                                                - startX;

                                float dy =
                                        event.getRawY()
                                                - startY;

                                if (
                                        Math.abs(dx)
                                                > dp(5)
                                                || Math.abs(dy)
                                                > dp(5)
                                ) {

                                    moved = true;
                                }

                                params.x =
                                        Math.max(
                                                0,
                                                startParamX
                                                        + (int) dx
                                        );

                                params.y =
                                        Math.max(
                                                0,
                                                startParamY
                                                        + (int) dy
                                        );

                                getSharedPreferences(
                                        "scanner_settings",
                                        MODE_PRIVATE
                                )
                                        .edit()
                                        .putInt(
                                                "bubbleX",
                                                params.x
                                        )
                                        .putInt(
                                                "bubbleY",
                                                params.y
                                        )
                                        .apply();

                                try {

                                    wm.updateViewLayout(
                                            bubble,
                                            params
                                    );

                                } catch (
                                        Exception ignored
                                ) {
                                }

                                return true;

                            case MotionEvent.ACTION_UP:

                                long duration =
                                        System.currentTimeMillis()
                                                - downTime;

                                if (
                                        !moved
                                                && duration
                                                < 450
                                ) {

                                    if (continuous) {

                                        stopEverything();

                                    } else {

                                        startContinuous();
                                    }
                                }

                                return true;
                        }

                        return true;
                    }
                }
        );

        // --------------------------------------------------------
        // ADD BUBBLE
        // --------------------------------------------------------

        try {

            wm.addView(
                    bubble,
                    params
            );

        } catch (Exception e) {

            bubble = null;
        }
    }

    // ============================================================
    // SHOW BLUE SCAN OVERLAY
    // ============================================================

    private void showScanOverlay() {

        if (
                scanOverlay != null
                        || wm == null
                        || !Settings.canDrawOverlays(this)
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

            wm.addView(
                    scanOverlay,
                    overlayParams
            );

        } catch (Exception e) {

            scanOverlay = null;
        }
    }

    // ============================================================
    // HIDE BLUE SCAN OVERLAY
    // ============================================================

    private void hideScanOverlay() {

        if (scanOverlay != null) {

            try {

                wm.removeView(
                        scanOverlay
                );

            } catch (Exception ignored) {
            }

            scanOverlay = null;
        }
    }

    // ============================================================
    // SHOW FINAL SIGNAL BADGE
    // ============================================================

    private void showBadge(
            String signal,
            float score
    ) {

        if (badge == null) {
            return;
        }

        if (
                !signal.equals("UP")
                        && !signal.equals("DOWN")
        ) {

            return;
        }

        /*
         * Do not display less than the strong threshold.
         */
        if (score < MIN_SIGNAL_SCORE) {
            return;
        }

        /*
         * Keep displayed evidence score within
         * the intended visual range.
         */
        score =
                Math.max(
                        90.0f,
                        Math.min(
                                97.0f,
                                score
                        )
                );

        badge.setVisibility(
                View.VISIBLE
        );

        badge.setText(
                "STRONG "
                        + signal
                        + " "
                        + String.format(
                        Locale.US,
                        "%.0f%%",
                        score
                )
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setCornerRadius(
                dp(8)
        );

        if (signal.equals("UP")) {

            bg.setColor(
                    Color.rgb(
                            0,
                            120,
                            70
                    )
            );

            bg.setStroke(
                    dp(1),
                    Color.rgb(
                            60,
                            240,
                            165
                    )
            );

        } else {

            bg.setColor(
                    Color.rgb(
                            145,
                            25,
                            38
                    )
            );

            bg.setStroke(
                    dp(1),
                    Color.rgb(
                            255,
                            75,
                            90
                    )
            );
        }

        badge.setBackground(
                bg
        );
    }

    // ============================================================
    // HIDE BADGE
    // ============================================================

    private void hideBadge() {

        if (badge != null) {

            badge.setVisibility(
                    View.GONE
            );
        }
    }

    // ============================================================
    // FOREGROUND SERVICE
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
                        : new Notification.Builder(
                        this
                );

        startForeground(
                9903,
                builder
                        .setContentTitle(
                                "MD JIBON Scanner"
                        )
                        .setContentText(
                                "Current-screen continuous scanner active"
                        )
                        .setSmallIcon(
                                android.R.drawable
                                        .ic_menu_search
                        )
                        .build()
        );
    }

    // ============================================================
    // DP CONVERSION
    // ============================================================

    /*
     * IMPORTANT:
     * float is intentionally accepted here.
     *
     * This fixes:
     *
     * incompatible types:
     * possible lossy conversion from float to int
     *
     * caused by calls such as dp(1.5f).
     */
    private int dp(float value) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // ============================================================
    // SERVICE DESTROY
    // ============================================================

    @Override
    public void onDestroy() {

        running = false;

        stopEverything();

        if (handler != null) {

            handler.removeCallbacksAndMessages(
                    null
            );
        }

        try {

            unregisterReceiver(
                    receiver
            );

        } catch (Exception ignored) {
        }

        if (
                bubble != null
                        && wm != null
        ) {

            try {

                wm.removeView(
                        bubble
                );

            } catch (Exception ignored) {
            }
        }

        bubble = null;

        badge = null;

        hideScanOverlay();

        super.onDestroy();
    }

    // ============================================================
    // BIND
    // ============================================================

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }

    // ============================================================
    // BLUE SCAN OVERLAY VIEW
    // ============================================================

    private static class ScanOverlay
            extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private final Handler animationHandler =
                new Handler(
                        Looper.getMainLooper()
                );

        private float y =
                -40f;

        private final Runnable animation =
                new Runnable() {

                    @Override
                    public void run() {

                        y += Math.max(
                                10f,
                                getResources()
                                        .getDisplayMetrics()
                                        .heightPixels
                                        / 42f
                        );

                        if (
                                y
                                        > getHeight()
                                        + 40
                        ) {

                            y = -40f;
                        }

                        invalidate();

                        animationHandler.postDelayed(
                                this,
                                28L
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

            super.onDraw(
                    canvas
            );

            paint.setStyle(
                    Paint.Style.FILL
            );

            // --------------------------------------------------------
            // FULL DISPLAY BLUE TRANSLUCENT COVER
            // --------------------------------------------------------

            paint.setColor(
                    Color.argb(
                            48,
                            0,
                            110,
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

            // --------------------------------------------------------
            // SOFT BLUE SCANNING TRAIL
            // --------------------------------------------------------

            paint.setColor(
                    Color.argb(
                            58,
                            40,
                            145,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    Math.max(
                            0,
                            y - 105
                    ),
                    getWidth(),
                    y,
                    paint
            );

            // --------------------------------------------------------
            // BRIGHT MOVING SCAN BAR
            // --------------------------------------------------------

            paint.setColor(
                    Color.argb(
                            205,
                            55,
                            165,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    y - 5,
                    getWidth(),
                    y + 5,
                    paint
            );

            // --------------------------------------------------------
            // THIN CENTER LINE
            // --------------------------------------------------------

            paint.setColor(
                    Color.argb(
                            120,
                            100,
                            200,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    y - 1,
                    getWidth(),
                    y + 1,
                    paint
            );
        }

        @Override
        protected void onDetachedFromWindow() {

            animationHandler.removeCallbacks(
                    animation
            );

            super.onDetachedFromWindow();
        }
    }
}
