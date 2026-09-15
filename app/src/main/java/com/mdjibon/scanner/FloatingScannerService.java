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

    public static final String ACTION_START_CONTINUOUS =
            "com.mdjibon.scanner.ACTION_START_CONTINUOUS";

    private static final int MIN_SCANS_BEFORE_SIGNAL = 3;
    private static final int MAX_SCANS = 5;
    private static final int SAME_DIRECTION_CONFIRMATIONS = 2;
    private static final float MIN_SIGNAL_SCORE = 90.0f;

    private static final long SCAN_INTERVAL_MS = 2600L;
    private static final long OVERLAY_MS = 1450L;

    private static volatile boolean running = false;

    private WindowManager wm;

    private LinearLayout bubble;
    private ImageView icon;
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

    public static boolean isRunning() {
        return running;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null) {
                return;
            }

            String action = intent.getAction();

            // ========================================================
            // SCAN STATUS
            // ========================================================

            if (ScreenCaptureService.ACTION_SCAN_STATUS.equals(action)) {

                String state = intent.getStringExtra("state");

                if ("working".equals(state)) {
                    showScanOverlay();
                }

                if ("done".equals(state)) {
                    hideScanOverlay();
                    scanBusy = false;
                }

                return;
            }

            // ========================================================
            // ANALYZER RESULT
            // ========================================================

            if (ScreenCaptureService.ACTION_RESULT.equals(action)) {

                hideScanOverlay();
                scanBusy = false;

                String signal =
                        intent.getStringExtra("signal");

                float score =
                        intent.getFloatExtra("score", 0f);

                boolean strong =
                        intent.getBooleanExtra("strong", false);

                if (signal == null) {
                    signal = "NO SIGNAL";
                }

                signal = signal.trim().toUpperCase(Locale.US);

                // ====================================================
                // STRONG RESULT
                // ====================================================

                if (strong
                        && ("UP".equals(signal) || "DOWN".equals(signal))
                        && score >= MIN_SIGNAL_SCORE) {

                    if (signal.equals(lastStrongSignal)) {

                        sameStrongCount++;

                    } else {

                        lastStrongSignal = signal;
                        sameStrongCount = 1;
                    }

                    // =================================================
                    // FINAL CONFIRMATION
                    // =================================================

                    if (scanCount >= MIN_SCANS_BEFORE_SIGNAL
                            && sameStrongCount >= SAME_DIRECTION_CONFIRMATIONS) {

                        showStrongBadge(signal, score);

                        stopLoopOnly();

                        Toast.makeText(
                                FloatingScannerService.this,
                                "STRONG "
                                        + signal
                                        + " "
                                        + formatScore(score)
                                        + " • SCAN STOPPED",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }
                }

                // ====================================================
                // MAXIMUM 5 SCANS
                // ====================================================

                if (scanCount >= MAX_SCANS) {

                    stopLoopOnly();

                    hideBadge();

                    Toast.makeText(
                            FloatingScannerService.this,
                            "NO STRONG SIGNAL • SCAN STOPPED",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                return;
            }

            // ========================================================
            // ERROR
            // ========================================================

            if (ScreenCaptureService.ACTION_ERROR.equals(action)) {

                scanBusy = false;
                hideScanOverlay();
            }
        }
    };

    // ================================================================
    // SERVICE CREATE
    // ================================================================

    @Override
    public void onCreate() {

        super.onCreate();

        handler =
                new Handler(Looper.getMainLooper());

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

        if (Settings.canDrawOverlays(this)) {
            createBubble();
        }

        running = true;
    }

    // ================================================================
    // START COMMAND
    // ================================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (bubble == null
                && Settings.canDrawOverlays(this)) {

            createBubble();
        }

        if (intent != null
                && ACTION_START_CONTINUOUS.equals(
                intent.getAction())) {

            startContinuous();
        }

        return START_STICKY;
    }

    // ================================================================
    // START CONTINUOUS SCAN
    // ================================================================

    private void startContinuous() {

        if (!Settings.canDrawOverlays(this)) {

            Toast.makeText(
                    this,
                    "Display-over-other-apps permission is required.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

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

        hideBadge();

        if (loop != null) {
            handler.removeCallbacks(loop);
        }

        loop = new Runnable() {

            @Override
            public void run() {

                if (!continuous || !running) {
                    return;
                }

                if (scanCount >= MAX_SCANS) {

                    stopLoopOnly();

                    return;
                }

                requestOneScan();

                handler.postDelayed(
                        this,
                        SCAN_INTERVAL_MS
                );
            }
        };

        // First scan immediately
        requestOneScan();

        // Next scans
        handler.postDelayed(
                loop,
                SCAN_INTERVAL_MS
        );
    }

    // ================================================================
    // REQUEST ONE SCAN
    // ================================================================

    private void requestOneScan() {

        if (!continuous
                || scanBusy
                || !ScreenCaptureService.isCaptureActive()) {

            return;
        }

        if (scanCount >= MAX_SCANS) {

            stopLoopOnly();

            return;
        }

        scanBusy = true;

        scanCount++;

        hideBadge();

        Intent intent =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        intent.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

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

        if (Build.VERSION.SDK_INT >= 26) {

            startForegroundService(intent);

        } else {

            startService(intent);
        }

        showScanOverlay();

        handler.postDelayed(
                this::hideScanOverlay,
                OVERLAY_MS
        );
    }

    // ================================================================
    // STOP SCAN LOOP
    // ================================================================

    private void stopLoopOnly() {

        continuous = false;

        scanBusy = false;

        if (loop != null) {

            handler.removeCallbacks(loop);

            loop = null;
        }

        hideScanOverlay();
    }

    // ================================================================
    // CREATE FLOATING ICON
    // ================================================================

    private void createBubble() {

        if (bubble != null
                || !Settings.canDrawOverlays(this)) {

            return;
        }

        wm =
                (WindowManager)
                        getSystemService(WINDOW_SERVICE);

        bubble =
                new LinearLayout(this);

        bubble.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bubble.setGravity(
                Gravity.CENTER_VERTICAL
        );

        bubble.setPadding(
                dp(2),
                dp(2),
                dp(2),
                dp(2)
        );

        // ============================================================
        // ICON
        // ============================================================

        icon =
                new ImageView(this);

        icon.setImageResource(
                R.drawable.md_jibon_logo
        );

        icon.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        bubble.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(62),
                        dp(62)
                )
        );

        // ============================================================
        // RESULT BADGE
        // ============================================================

        badge =
                new TextView(this);

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setTextSize(11);

        badge.setTextColor(
                Color.WHITE
        );

        badge.setSingleLine(true);

        badge.setTypeface(
                android.graphics.Typeface.DEFAULT_BOLD
        );

        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(
                        dp(90),
                        dp(36)
                );

        badgeParams.leftMargin = dp(4);

        bubble.addView(
                badge,
                badgeParams
        );

        hideBadge();

        // ============================================================
        // WINDOW PARAMS
        // ============================================================

        params =
                new WindowManager.LayoutParams(
                        dp(160),
                        dp(68),

                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,

                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,

                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP | Gravity.START;

        android.content.SharedPreferences pref =
                getSharedPreferences(
                        "scanner_settings",
                        MODE_PRIVATE
                );

        params.x =
                pref.getInt(
                        "bubbleX",
                        dp(12)
                );

        params.y =
                pref.getInt(
                        "bubbleY",
                        dp(280)
                );

        // ============================================================
        // DRAG + TAP
        // ============================================================

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
                            MotionEvent event) {

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

                                if (Math.abs(dx) > dp(5)
                                        || Math.abs(dy) > dp(5)) {

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

                                } catch (Exception ignored) {
                                }

                                return true;

                            case MotionEvent.ACTION_UP:

                                if (!moved
                                        && System.currentTimeMillis()
                                        - downTime < 450) {

                                    if (continuous) {

                                        stopLoopOnly();

                                    } else {

                                        startContinuous();
                                    }
                                }

                                return true;

                            default:

                                return true;
                        }
                    }
                }
        );

        // ============================================================
        // ADD BUBBLE
        // ============================================================

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

    // ================================================================
    // SCAN OVERLAY
    // ================================================================

    private void showScanOverlay() {

        if (scanOverlay != null
                || wm == null
                || !Settings.canDrawOverlays(this)) {

            return;
        }

        scanOverlay =
                new ScanOverlay(this);

        WindowManager.LayoutParams overlayParams =
                new WindowManager.LayoutParams(
                        -1,
                        -1,

                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,

                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,

                        PixelFormat.TRANSLUCENT
                );

        overlayParams.gravity =
                Gravity.TOP | Gravity.START;

        try {

            wm.addView(
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

                wm.removeView(
                        scanOverlay
                );

            } catch (Exception ignored) {
            }

            scanOverlay = null;
        }
    }

    // ================================================================
    // SHOW STRONG RESULT
    // ================================================================

    private void showStrongBadge(
            String signal,
            float score) {

        if (badge == null) {
            return;
        }

        float safeScore =
                Math.min(
                        97f,
                        Math.max(
                                0f,
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
                        safeScore
                )
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setCornerRadius(
                dp(10)
        );

        if ("UP".equals(signal)) {

            background.setColor(
                    Color.rgb(
                            0,
                            105,
                            65
                    )
            );

            background.setStroke(
                    dp(1),
                    Color.rgb(
                            55,
                            245,
                            155
                    )
            );

        } else {

            background.setColor(
                    Color.rgb(
                            135,
                            24,
                            38
                    )
            );

            background.setStroke(
                    dp(1),
                    Color.rgb(
                            255,
                            75,
                            90
                    )
            );
        }

        badge.setBackground(
                background
        );
    }

    // ================================================================
    // HIDE BADGE
    // ================================================================

    private void hideBadge() {

        if (badge != null) {

            badge.setVisibility(
                    View.GONE
            );

            badge.setText("");
        }
    }

    // ================================================================
    // SCORE FORMAT
    // ================================================================

    private String formatScore(
            float score) {

        float safeScore =
                Math.min(
                        97f,
                        Math.max(
                                0f,
                                score
                        )
                );

        return String.format(
                Locale.US,
                "%.0f%%",
                safeScore
        );
    }

    // ================================================================
    // FOREGROUND SERVICE
    // ================================================================

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
                                "Floating screen scanner active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_search
                        )
                        .build()
        );
    }

    // ================================================================
    // DP
    // ================================================================

    private int dp(int value) {

        return (int)
                (
                        value
                                * getResources()
                                .getDisplayMetrics()
                                .density
                                + 0.5f
                );
    }

    // ================================================================
    // DESTROY
    // ================================================================

    @Override
    public void onDestroy() {

        stopLoopOnly();

        running = false;

        try {

            unregisterReceiver(
                    receiver
            );

        } catch (Exception ignored) {
        }

        if (bubble != null
                && wm != null) {

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

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent) {

        return null;
    }

    // ================================================================
    // SCAN OVERLAY VIEW
    // ================================================================

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

        private float y = -40f;

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

                        if (y > getHeight() + 40) {

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
                Context context) {

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
                Canvas canvas) {

            super.onDraw(canvas);

            paint.setStyle(
                    Paint.Style.FILL
            );

            // Blue translucent screen
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

            // Moving scan glow
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

            // Main scan line
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

            // Bright center line
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
