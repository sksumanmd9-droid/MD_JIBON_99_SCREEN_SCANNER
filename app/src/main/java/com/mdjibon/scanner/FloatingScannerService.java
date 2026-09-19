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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class FloatingScannerService extends Service {

    // ============================================================
    // ACTION
    // ============================================================

    public static final String ACTION_START_CONTINUOUS =
            "com.mdjibon.scanner.ACTION_START_CONTINUOUS";

    // ============================================================
    // SCAN SETTINGS
    // ============================================================

    private static final int MIN_SCANS_BEFORE_SIGNAL = 3;
    private static final int MAX_SCANS = 5;

    private static final float MIN_SIGNAL_SCORE = 90.0f;

    private static final int SAME_DIRECTION_CONFIRMATIONS = 2;

    private static final long SIGNAL_COOLDOWN_MS = 2500L;
    private static final long SCAN_INTERVAL_MS = 2600L;

    // ============================================================
    // RUNNING
    // ============================================================

    private static volatile boolean running = false;

    // ============================================================
    // FLOATING UI
    // ============================================================

    private WindowManager wm;

    /*
     * Root floating container.
     *
     * IMPORTANT:
     * Drag is handled by this root view.
     * The ImageView does NOT have its own click listener.
     * This prevents the icon from stealing the drag events.
     */
    private FrameLayout bubble;

    private ImageView icon;

    private TextView badge;

    private WindowManager.LayoutParams params;

    // ============================================================
    // HANDLER / SCAN STATE
    // ============================================================

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
    // DRAG STATE
    // ============================================================

    private float touchStartRawX;

    private float touchStartRawY;

    private int touchStartParamX;

    private int touchStartParamY;

    private long touchDownTime;

    private boolean touchMoved = false;

    // ============================================================
    // DIMENSIONS
    // ============================================================

    private int iconSize() {
        return dp(54);
    }

    private int normalBubbleWidth() {
        return dp(58);
    }

    private int bubbleHeight() {
        return dp(58);
    }

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
                    // RESULT
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

                        boolean marketChart =
                                intent.getBooleanExtra(
                                        "marketChart",
                                        false
                                );

                        int candles =
                                intent.getIntExtra(
                                        "candles",
                                        0
                                );

                        // ------------------------------------------------
                        // MARKET-ONLY GATE
                        // ------------------------------------------------
                        // Never show UP/DOWN when the visible screen does
                        // not contain a confirmed market-chart structure.
                        if (!marketChart || candles < 12) {

                            continuous = false;

                            stopLoopOnly();

                            scanBusy = false;

                            hideScanOverlay();

                            lastStrongSignal = "";
                            sameStrongCount = 0;

                            hideBadge();

                            Toast.makeText(
                                    FloatingScannerService.this,
                                    "MARKET CHART NOT DETECTED",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

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

                            if (scanCount >= MAX_SCANS) {
                                continuous = false;
                                stopLoopOnly();
                                hideBadge();
                            } else if (continuous) {
                                handler.postDelayed(
                                        FloatingScannerService.this::requestOneScan,
                                        SCAN_INTERVAL_MS
                                );
                            }
                            return;
                        }

                        // ------------------------------------------------
                        // STRONG RESULT
                        // ------------------------------------------------

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
                             * Final result requires:
                             *
                             * 3 completed scans minimum
                             * + 2 same-direction strong confirmations
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
                                                + " â€¢ SCAN STOPPED",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }
                        }

                        // ------------------------------------------------
                        // MAX 5 SCANS
                        // ------------------------------------------------

                        if (
                                scanCount >= MAX_SCANS
                                        && continuous
                        ) {

                            continuous = false;
                            stopLoopOnly();
                            hideBadge();

                            Toast.makeText(
                                    FloatingScannerService.this,
                                    "NO STRONG SIGNAL â€¢ 5 SCANS COMPLETED",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        return;
                    }

                    // ------------------------------------------------
                    // ERROR
                    // ------------------------------------------------

                    if (
                            ScreenCaptureService.ACTION_ERROR
                                    .equals(action)
                    ) {

                        scanBusy = false;
                        continuous = false;
                        stopLoopOnly();
                        hideScanOverlay();
                        hideBadge();
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

        if (!ScreenCaptureService.isCaptureActive()) {
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

        hideBadge();
        requestOneScan();
    }

    // ============================================================
    // ONE SCAN
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

        // Never show an old result during a new scan.
        hideBadge();
        showScanningState();

        Intent intent =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        intent.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        // --------------------------------------------------------
        // EXCLUDE FLOATING ICON FROM SCREEN ANALYSIS
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // START SCREEN SCAN
        // --------------------------------------------------------

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

        // The scan overlay is intentionally NOT shown here.
        // ScreenCaptureService first copies the latest clean frame and then
        // broadcasts ACTION_SCAN_STATUS("working"). This prevents the blue
        // overlay itself from being captured and analyzed as part of the chart.
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
    // CREATE FLOATING ICON
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

        // --------------------------------------------------------
        // ROOT CONTAINER
        // --------------------------------------------------------

        bubble =
                new FrameLayout(this);

        bubble.setClipChildren(false);

        bubble.setClipToPadding(false);

        // --------------------------------------------------------
        // ICON
        // --------------------------------------------------------

        icon =
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

        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(
                        iconSize(),
                        iconSize()
                );

        iconParams.leftMargin = 0;
        iconParams.topMargin = 0;

        bubble.addView(
                icon,
                iconParams
        );

        // --------------------------------------------------------
        // RESULT / SCAN TEXT INSIDE THE SAME SMALL ICON
        // --------------------------------------------------------

        badge =
                new TextView(this);

        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setLineSpacing(0f, 0.88f);
        badge.setIncludeFontPadding(false);
        badge.setSingleLine(false);
        badge.setPadding(0, 0, 0, 0);

        GradientDrawable defaultBadge =
                new GradientDrawable();
        defaultBadge.setShape(GradientDrawable.OVAL);
        defaultBadge.setColor(Color.argb(225, 7, 17, 28));
        defaultBadge.setStroke(dp(1), Color.rgb(55, 170, 235));
        defaultBadge.setCornerRadius(dp(30));
        badge.setBackground(defaultBadge);

        FrameLayout.LayoutParams badgeParams =
                new FrameLayout.LayoutParams(
                        iconSize(),
                        iconSize()
                );

        badgeParams.leftMargin = 0;
        badgeParams.topMargin = 0;

        bubble.addView(badge, badgeParams);
        badge.setVisibility(View.GONE);

        // --------------------------------------------------------
        // WINDOW PARAMETERS
        // --------------------------------------------------------

        params =
                new WindowManager.LayoutParams(
                        normalBubbleWidth(),
                        bubbleHeight(),

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
        // LOAD SAVED POSITION
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
        // IMPORTANT TOUCH HANDLER
        // --------------------------------------------------------
        //
        // There is NO icon.setOnClickListener().
        //
        // The root receives both:
        //
        //     DOWN
        //     MOVE
        //     UP
        //
        // So dragging the actual logo works correctly.
        // --------------------------------------------------------

        bubble.setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(
                            View view,
                            MotionEvent event
                    ) {

                        switch (
                                event.getActionMasked()
                        ) {

                            // ------------------------------------
                            // TOUCH DOWN
                            // ------------------------------------

                            case MotionEvent.ACTION_DOWN:

                                touchStartRawX =
                                        event.getRawX();

                                touchStartRawY =
                                        event.getRawY();

                                touchStartParamX =
                                        params.x;

                                touchStartParamY =
                                        params.y;

                                touchDownTime =
                                        System.currentTimeMillis();

                                touchMoved = false;

                                return true;

                            // ------------------------------------
                            // DRAG
                            // ------------------------------------

                            case MotionEvent.ACTION_MOVE:

                                float dx =
                                        event.getRawX()
                                                - touchStartRawX;

                                float dy =
                                        event.getRawY()
                                                - touchStartRawY;

                                if (
                                        Math.abs(dx)
                                                > dp(5)
                                                || Math.abs(dy)
                                                > dp(5)
                                ) {

                                    touchMoved = true;
                                }

                                params.x =
                                        Math.max(
                                                0,
                                                touchStartParamX
                                                        + Math.round(dx)
                                        );

                                params.y =
                                        Math.max(
                                                0,
                                                touchStartParamY
                                                        + Math.round(dy)
                                        );

                                saveBubblePosition();

                                try {

                                    wm.updateViewLayout(
                                            bubble,
                                            params
                                    );

                                } catch (Exception ignored) {
                                }

                                return true;

                            // ------------------------------------
                            // RELEASE
                            // ------------------------------------

                            case MotionEvent.ACTION_UP:

                                long duration =
                                        System.currentTimeMillis()
                                                - touchDownTime;

                                /*
                                 * Short tap = scan.
                                 *
                                 * Drag = only move.
                                 */
                                if (
                                        !touchMoved
                                                && duration < 450L
                                ) {

                                    if (continuous) {

                                        stopEverything();

                                    } else {

                                        startContinuous();
                                    }
                                }

                                return true;

                            // ------------------------------------
                            // CANCEL
                            // ------------------------------------

                            case MotionEvent.ACTION_CANCEL:

                                touchMoved = true;

                                return true;
                        }

                        return true;
                    }
                }
        );

        // --------------------------------------------------------
        // ADD WINDOW
        // --------------------------------------------------------

        try {

            wm.addView(
                    bubble,
                    params
            );

        } catch (Exception e) {

            bubble = null;

            icon = null;

            badge = null;
        }
    }

    // ============================================================
    // SAVE POSITION
    // ============================================================

    private void saveBubblePosition() {

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
    }

    // ============================================================
    // SHOW FINAL RESULT
    // ============================================================

    private void showBadge(
            String signal,
            float score
    ) {

        if (bubble == null || badge == null) {
            return;
        }

        if (!signal.equals("UP") && !signal.equals("DOWN")) {
            return;
        }

        if (score < MIN_SIGNAL_SCORE) {
            return;
        }

        score = Math.max(90.0f, Math.min(97.0f, score));

        String percent = String.format(Locale.US, "%.0f%%", score);
        badge.setText(signal + "\n" + percent);
        badge.setTextSize(11);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setCornerRadius(dp(30));

        if (signal.equals("UP")) {
            bg.setColor(Color.argb(238, 0, 115, 68));
            bg.setStroke(dp(2), Color.rgb(65, 245, 165));
        } else {
            bg.setColor(Color.argb(238, 150, 28, 42));
            bg.setStroke(dp(2), Color.rgb(255, 80, 95));
        }

        badge.setBackground(bg);
        badge.setTextColor(Color.WHITE);
        if (icon != null) icon.setVisibility(View.INVISIBLE);
        badge.bringToFront();
        badge.setVisibility(View.VISIBLE);

        // Keep the floating window small; the result is INSIDE the icon.
        params.width = normalBubbleWidth();
        params.height = bubbleHeight();

        try {
            wm.updateViewLayout(bubble, params);
        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // SCANNING STATE INSIDE ICON
    // ============================================================

    private void showScanningState() {

        if (bubble == null || badge == null || params == null) {
            return;
        }

        badge.setText("SCAN\nâ€¦");
        badge.setTextSize(9);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setCornerRadius(dp(30));
        bg.setColor(Color.argb(225, 5, 35, 62));
        bg.setStroke(dp(2), Color.rgb(55, 190, 255));

        badge.setBackground(bg);
        badge.setTextColor(Color.rgb(150, 225, 255));
        if (icon != null) icon.setVisibility(View.INVISIBLE);
        badge.bringToFront();
        badge.setVisibility(View.VISIBLE);

        params.width = normalBubbleWidth();
        params.height = bubbleHeight();

        try {
            wm.updateViewLayout(bubble, params);
        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // HIDE RESULT / SCAN TEXT
    // ============================================================

    private void hideBadge() {

        if (badge != null) {
            badge.setVisibility(View.GONE);
        }

        if (icon != null) {
            icon.setVisibility(View.VISIBLE);
        }

        if (params != null && bubble != null) {
            params.width = normalBubbleWidth();
            params.height = bubbleHeight();

            try {
                wm.updateViewLayout(bubble, params);
            } catch (Exception ignored) {
            }
        }
    }

    // ============================================================
    // BLUE SCAN OVERLAY
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
    // HIDE SCAN OVERLAY
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
    // DP
    // ============================================================

    private int dp(float value) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    // ============================================================
    // DESTROY
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

        icon = null;

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
            // BLUE TRANSPARENT SCREEN COVER
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
            // BLUE TRAIL
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
            // BRIGHT SCAN BAR
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
            // CENTER LINE
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
