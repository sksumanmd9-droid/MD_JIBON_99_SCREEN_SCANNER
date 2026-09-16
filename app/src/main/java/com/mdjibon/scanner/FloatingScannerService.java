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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class FloatingScannerService extends Service {

    private static final String TAG = "MDJIBON";

    private static final String CHANNEL_ID =
            "md_jibon_floating_scanner";

    private static final int NOTIFICATION_ID = 9902;

    public static final String ACTION_SCAN =
            "com.mdjibon.scanner.ACTION_SCAN";

    public static final String ACTION_RESULT =
            "com.mdjibon.scanner.ACTION_RESULT";

    public static final String ACTION_PROGRESS =
            "com.mdjibon.scanner.ACTION_PROGRESS";

    private static final int MIN_SCANS_BEFORE_SIGNAL = 3;
    private static final int MAX_SCANS = 5;

    private static final float MIN_SIGNAL_SCORE = 90.0f;

    private static final long SCAN_INTERVAL_MS = 2600L;
    private static final long OVERLAY_MS = 1450L;

    private WindowManager windowManager;

    private View logoView;
    private WindowManager.LayoutParams logoParams;

    private ResultBadgeView badgeView;
    private WindowManager.LayoutParams badgeParams;

    private ScanOverlay scanOverlay;
    private WindowManager.LayoutParams scanOverlayParams;

    private BroadcastReceiver receiver;

    private final Handler handler = new Handler();

    private boolean running = false;
    private boolean continuous = false;
    private boolean scanBusy = false;

    private int scanCount = 0;

    private String bestStrongSignal = "";
    private float bestStrongScore = 0.0f;

    private int savedX = 20;
    private int savedY = 250;

    private final Runnable continuousRunnable = new Runnable() {
        @Override
        public void run() {

            if (!continuous) {
                return;
            }

            if (scanCount >= MAX_SCANS) {
                stopContinuous(false);
                return;
            }

            requestOneScan();

            if (continuous && scanCount < MAX_SCANS) {
                handler.postDelayed(
                        this,
                        SCAN_INTERVAL_MS
                );
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);

        createNotificationChannel();

        registerScannerReceiver();

        running = true;

        if (Settings.canDrawOverlays(this)) {
            createFloatingLogo();
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        startFloatingForeground();

        running = true;

        if (!Settings.canDrawOverlays(this)) {

            Intent settingsIntent =
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    );

            settingsIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            try {
                startActivity(settingsIntent);
            } catch (Exception ignored) {
            }

            return START_STICKY;
        }

        if (logoView == null) {
            createFloatingLogo();
        }

        if (intent != null) {

            String action = intent.getAction();

            if (ACTION_SCAN.equals(action)) {
                startContinuous();
            }
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "MD JIBON Floating Scanner",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "MD JIBON screen scanner floating overlay"
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startFloatingForeground() {

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_search
                        )
                        .setContentTitle(
                                "MD JIBON Scanner"
                        )
                        .setContentText(
                                "Floating scanner is active"
                        )
                        .setOngoing(true)
                        .setPriority(
                                NotificationCompat.PRIORITY_LOW
                        )
                        .build();

        startForeground(
                NOTIFICATION_ID,
                notification
        );
    }

    private void registerScannerReceiver() {

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent
            ) {

                if (intent == null) {
                    return;
                }

                String action = intent.getAction();

                if (ScreenCaptureService.ACTION_SCAN_STATUS
                        .equals(action)) {

                    String status =
                            intent.getStringExtra("status");

                    if ("working".equalsIgnoreCase(status)) {

                        showScanOverlay();
                        showLoadingBadge();

                    } else if ("done".equalsIgnoreCase(status)) {

                        hideScanOverlay();

                        /*
                         * Result broadcast may arrive immediately
                         * after this status. The result handler below
                         * also resets scanBusy.
                         */
                        scanBusy = false;
                    }

                    return;
                }

                if (ACTION_PROGRESS.equals(action)) {

                    showLoadingBadge();

                    return;
                }

                if (ACTION_RESULT.equals(action)) {

                    handleScanResult(intent);
                }
            }
        };

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                ScreenCaptureService.ACTION_SCAN_STATUS
        );

        filter.addAction(
                ACTION_PROGRESS
        );

        filter.addAction(
                ACTION_RESULT
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

    private void handleScanResult(Intent intent) {

        hideScanOverlay();

        scanBusy = false;

        String signal =
                intent.getStringExtra("signal");

        boolean strong =
                intent.getBooleanExtra(
                        "strong",
                        false
                );

        float score =
                intent.getFloatExtra(
                        "score",
                        50.0f
                );

        if (signal == null) {
            signal = "NO SIGNAL";
        }

        signal =
                signal.trim().toUpperCase(
                        Locale.US
                );

        if (!"UP".equals(signal)
                && !"DOWN".equals(signal)) {

            signal = "NO SIGNAL";
        }

        if (Float.isNaN(score)
                || Float.isInfinite(score)) {

            score = 50.0f;
        }

        if (score < 0.0f) {
            score = 0.0f;
        }

        if (score > 100.0f) {
            score = 100.0f;
        }

        /*
         * Store only a genuinely strong result.
         */
        if (strong
                && ("UP".equals(signal)
                || "DOWN".equals(signal))
                && score >= MIN_SIGNAL_SCORE) {

            if (score > bestStrongScore) {

                bestStrongScore = score;
                bestStrongSignal = signal;
            }

            /*
             * Do not finish before at least 3 complete scans.
             */
            if (scanCount >= MIN_SCANS_BEFORE_SIGNAL) {

                showStrongBadge(
                        bestStrongSignal,
                        bestStrongScore
                );

                stopContinuous(true);

                Toast.makeText(
                        this,
                        "STRONG "
                                + bestStrongSignal
                                + " • SCAN STOPPED",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }
        }

        /*
         * If an earlier scan already produced strong evidence,
         * keep that strongest evidence.
         */
        if (bestStrongScore >= MIN_SIGNAL_SCORE
                && scanCount >= MIN_SCANS_BEFORE_SIGNAL) {

            showStrongBadge(
                    bestStrongSignal,
                    bestStrongScore
            );

            stopContinuous(true);

            return;
        }

        /*
         * Maximum five scans.
         */
        if (scanCount >= MAX_SCANS) {

            stopContinuous(false);

            hideBadge();

            Toast.makeText(
                    this,
                    "NO STRONG SIGNAL • SCAN STOPPED",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        /*
         * Keep LOADING visible while continuous scanning
         * is still running.
         */
        if (continuous) {
            showLoadingBadge();
        }
    }

    private void createFloatingLogo() {

        if (logoView != null) {
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        logoView =
                new LogoView(this);

        int size =
                dp(62);

        logoParams =
                new WindowManager.LayoutParams();

        logoParams.width = size;
        logoParams.height = size;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            logoParams.type =
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        } else {

            logoParams.type =
                    WindowManager.LayoutParams.TYPE_PHONE;
        }

        logoParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        logoParams.format =
                android.graphics.PixelFormat.TRANSLUCENT;

        logoParams.gravity =
                Gravity.TOP | Gravity.START;

        logoParams.x = savedX;
        logoParams.y = savedY;

        try {

            windowManager.addView(
                    logoView,
                    logoParams
            );

        } catch (Exception e) {

            logoView = null;
            logoParams = null;
        }
    }

    private void createBadge() {

        if (badgeView != null) {
            return;
        }

        badgeView =
                new ResultBadgeView(this);

        badgeParams =
                new WindowManager.LayoutParams();

        badgeParams.width =
                dp(110);

        badgeParams.height =
                dp(36);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            badgeParams.type =
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        } else {

            badgeParams.type =
                    WindowManager.LayoutParams.TYPE_PHONE;
        }

        badgeParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        badgeParams.format =
                android.graphics.PixelFormat.TRANSLUCENT;

        badgeParams.gravity =
                Gravity.TOP | Gravity.START;

        updateBadgePosition();

        try {

            windowManager.addView(
                    badgeView,
                    badgeParams
            );

            badgeView.setVisibility(View.GONE);

        } catch (Exception e) {

            badgeView = null;
            badgeParams = null;
        }
    }

    private void updateBadgePosition() {

        if (badgeParams == null) {
            return;
        }

        int logoSize = dp(62);

        /*
         * Badge appears immediately beside the floating icon.
         */
        badgeParams.x =
                savedX + logoSize + dp(6);

        badgeParams.y =
                savedY + dp(13);
    }

    private void showLoadingBadge() {

        createBadge();

        if (badgeView == null) {
            return;
        }

        badgeView.setMode(
                ResultBadgeView.MODE_LOADING
        );

        updateBadgePosition();

        badgeView.setVisibility(
                View.VISIBLE
        );

        try {
            windowManager.updateViewLayout(
                    badgeView,
                    badgeParams
            );
        } catch (Exception ignored) {
        }
    }

    private void showStrongBadge(
            String signal,
            float score
    ) {

        if (!"UP".equals(signal)
                && !"DOWN".equals(signal)) {

            return;
        }

        createBadge();

        if (badgeView == null) {
            return;
        }

        /*
         * Displayed evidence score is intentionally limited
         * to the designed 90–97 display range.
         */
        float displayScore =
                Math.max(
                        90.0f,
                        Math.min(
                                97.0f,
                                score
                        )
                );

        badgeView.setSignal(
                signal,
                displayScore
        );

        updateBadgePosition();

        badgeView.setVisibility(
                View.VISIBLE
        );

        try {
            windowManager.updateViewLayout(
                    badgeView,
                    badgeParams
            );
        } catch (Exception ignored) {
        }
    }

    private void hideBadge() {

        if (badgeView != null) {

            badgeView.setVisibility(
                    View.GONE
            );
        }
    }

    private void showScanOverlay() {

        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        if (scanOverlay == null) {

            scanOverlay =
                    new ScanOverlay(this);

            scanOverlayParams =
                    new WindowManager.LayoutParams();

            scanOverlayParams.width =
                    WindowManager.LayoutParams.MATCH_PARENT;

            scanOverlayParams.height =
                    WindowManager.LayoutParams.MATCH_PARENT;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                scanOverlayParams.type =
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

            } else {

                scanOverlayParams.type =
                        WindowManager.LayoutParams.TYPE_PHONE;
            }

            scanOverlayParams.flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

            scanOverlayParams.format =
                    android.graphics.PixelFormat.TRANSLUCENT;

            scanOverlayParams.gravity =
                    Gravity.TOP | Gravity.START;

            try {

                windowManager.addView(
                        scanOverlay,
                        scanOverlayParams
                );

            } catch (Exception e) {

                scanOverlay = null;
                scanOverlayParams = null;
            }

        } else {

            scanOverlay.setVisibility(
                    View.VISIBLE
            );

            scanOverlay.startAnimation();
        }
    }

    private void hideScanOverlay() {

        if (scanOverlay != null) {

            scanOverlay.stopAnimation();

            scanOverlay.setVisibility(
                    View.GONE
            );
        }
    }

    private void startScan() {

        if (scanBusy) {
            return;
        }

        if (!ScreenCaptureService.isCaptureActive()) {

            Toast.makeText(
                    this,
                    "Please turn ON Screen Capture first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        startContinuous();
    }

    private void startContinuous() {

        if (scanBusy && continuous) {
            return;
        }

        continuous = true;
        scanBusy = false;

        scanCount = 0;

        bestStrongSignal = "";
        bestStrongScore = 0.0f;

        showLoadingBadge();

        handler.removeCallbacks(
                continuousRunnable
        );

        /*
         * First scan starts immediately.
         */
        requestOneScan();

        /*
         * Following scans are spaced apart.
         */
        handler.postDelayed(
                continuousRunnable,
                SCAN_INTERVAL_MS
        );
    }

    private void requestOneScan() {

        if (!continuous) {
            return;
        }

        if (scanBusy) {
            return;
        }

        if (scanCount >= MAX_SCANS) {

            stopContinuous(false);

            return;
        }

        if (!ScreenCaptureService.isCaptureActive()) {

            stopContinuous(false);

            hideBadge();

            Toast.makeText(
                    this,
                    "Screen Capture is not active",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        scanBusy = true;

        scanCount++;

        showLoadingBadge();

        Intent scanIntent =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        scanIntent.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        /*
         * Tell the capture service where the floating icon is.
         * This allows the icon to be excluded from analysis.
         */
        if (logoParams != null) {

            Rect exclusion =
                    new Rect(
                            logoParams.x,
                            logoParams.y,
                            logoParams.x
                                    + logoParams.width,
                            logoParams.y
                                    + logoParams.height
                    );

            scanIntent.putExtra(
                    "excludeLeft",
                    exclusion.left
            );

            scanIntent.putExtra(
                    "excludeTop",
                    exclusion.top
            );

            scanIntent.putExtra(
                    "excludeRight",
                    exclusion.right
            );

            scanIntent.putExtra(
                    "excludeBottom",
                    exclusion.bottom
            );
        }

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(
                        scanIntent
                );

            } else {

                startService(
                        scanIntent
                );
            }

        } catch (Exception e) {

            scanBusy = false;

            Toast.makeText(
                    this,
                    "Unable to start screen scan",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        /*
         * Blue full-screen scanning animation.
         */
        showScanOverlay();

        handler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {

                        /*
                         * Do not remove the final result.
                         * The result handler controls the final state.
                         */
                        if (scanBusy) {
                            hideScanOverlay();
                        }
                    }
                },
                OVERLAY_MS
        );
    }

    private void stopContinuous(
            boolean keepResult
    ) {

        continuous = false;

        scanBusy = false;

        handler.removeCallbacks(
                continuousRunnable
        );

        hideScanOverlay();

        if (!keepResult) {
            hideBadge();
        }
    }

    private int dp(int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                value * density
        );
    }

    @Override
    public void onDestroy() {

        running = false;

        continuous = false;

        handler.removeCallbacksAndMessages(
                null
        );

        hideScanOverlay();

        if (receiver != null) {

            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }

            receiver = null;
        }

        if (logoView != null) {

            try {
                windowManager.removeView(
                        logoView
                );
            } catch (Exception ignored) {
            }

            logoView = null;
        }

        if (badgeView != null) {

            try {
                windowManager.removeView(
                        badgeView
                );
            } catch (Exception ignored) {
            }

            badgeView = null;
        }

        if (scanOverlay != null) {

            try {
                windowManager.removeView(
                        scanOverlay
                );
            } catch (Exception ignored) {
            }

            scanOverlay = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isRunning() {
        return false;
    }

    // ============================================================
    // FLOATING LOGO
    // ============================================================

    private final class LogoView extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private float downX;
        private float downY;

        private int startX;
        private int startY;

        private long downTime;

        private boolean moved;

        public LogoView(Context context) {

            super(context);

            setBackgroundResource(
                    R.drawable.md_jibon_logo
            );

            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );
        }

        @Override
        public boolean onTouchEvent(
                MotionEvent event
        ) {

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:

                    downX = event.getRawX();
                    downY = event.getRawY();

                    startX =
                            logoParams != null
                                    ? logoParams.x
                                    : savedX;

                    startY =
                            logoParams != null
                                    ? logoParams.y
                                    : savedY;

                    downTime =
                            System.currentTimeMillis();

                    moved = false;

                    return true;

                case MotionEvent.ACTION_MOVE:

                    if (logoParams == null) {
                        return true;
                    }

                    float dx =
                            event.getRawX()
                                    - downX;

                    float dy =
                            event.getRawY()
                                    - downY;

                    if (Math.abs(dx) > dp(12)
                            || Math.abs(dy) > dp(12)) {

                        moved = true;
                    }

                    logoParams.x =
                            startX
                                    + Math.round(dx);

                    logoParams.y =
                            startY
                                    + Math.round(dy);

                    savedX = logoParams.x;
                    savedY = logoParams.y;

                    try {

                        windowManager.updateViewLayout(
                                logoView,
                                logoParams
                        );

                    } catch (Exception ignored) {
                    }

                    updateBadgePosition();

                    if (badgeView != null
                            && badgeView.getVisibility()
                            == View.VISIBLE) {

                        try {

                            windowManager.updateViewLayout(
                                    badgeView,
                                    badgeParams
                            );

                        } catch (Exception ignored) {
                        }
                    }

                    return true;

                case MotionEvent.ACTION_UP:

                    long duration =
                            System.currentTimeMillis()
                                    - downTime;

                    if (!moved
                            && duration < 350L) {

                        startScan();
                    }

                    return true;
            }

            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            /*
             * Subtle blue glow around the floating logo.
             */
            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    dp(1)
            );

            paint.setColor(
                    Color.argb(
                            80,
                            0,
                            180,
                            255
                    )
            );

            paint.setShadowLayer(
                    dp(5),
                    0,
                    0,
                    Color.argb(
                            120,
                            0,
                            140,
                            255
                    )
            );

            canvas.drawCircle(
                    getWidth() / 2f,
                    getHeight() / 2f,
                    Math.min(
                            getWidth(),
                            getHeight()
                    ) / 2f - dp(1),
                    paint
            );

            paint.clearShadowLayer();
        }
    }

    // ============================================================
    // RESULT BADGE
    // ============================================================

    private final class ResultBadgeView extends View {

        static final int MODE_LOADING = 1;
        static final int MODE_SIGNAL = 2;

        private final Paint backgroundPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint strokePaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint textPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private int mode =
                MODE_LOADING;

        private String signal =
                "";

        private float score =
                0.0f;

        ResultBadgeView(Context context) {

            super(context);

            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );

            textPaint.setTypeface(
                    Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                    )
            );

            textPaint.setTextAlign(
                    Paint.Align.CENTER
            );
        }

        void setMode(int newMode) {

            mode = newMode;

            invalidate();
        }

        void setSignal(
                String newSignal,
                float newScore
        ) {

            mode = MODE_SIGNAL;

            signal = newSignal;

            score = newScore;

            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            float w =
                    getWidth();

            float h =
                    getHeight();

            float radius =
                    dp(11);

            if (mode == MODE_LOADING) {

                backgroundPaint.setColor(
                        Color.rgb(
                                12,
                                48,
                                82
                        )
                );

                strokePaint.setColor(
                        Color.rgb(
                                0,
                                170,
                                255
                        )
                );

            } else if ("UP".equals(signal)) {

                backgroundPaint.setColor(
                        Color.rgb(
                                10,
                                76,
                                42
                        )
                );

                strokePaint.setColor(
                        Color.rgb(
                                40,
                                255,
                                130
                        )
                );

            } else {

                backgroundPaint.setColor(
                        Color.rgb(
                                92,
                                25,
                                28
                        )
                );

                strokePaint.setColor(
                        Color.rgb(
                                255,
                                75,
                                75
                        )
                );
            }

            backgroundPaint.setStyle(
                    Paint.Style.FILL
            );

            backgroundPaint.setShadowLayer(
                    dp(7),
                    0,
                    0,
                    Color.argb(
                            120,
                            0,
                            0,
                            0
                    )
            );

            canvas.drawRoundRect(
                    1,
                    1,
                    w - 1,
                    h - 1,
                    radius,
                    radius,
                    backgroundPaint
            );

            backgroundPaint.clearShadowLayer();

            strokePaint.setStyle(
                    Paint.Style.STROKE
            );

            strokePaint.setStrokeWidth(
                    dp(1.5f)
            );

            canvas.drawRoundRect(
                    1,
                    1,
                    w - 1,
                    h - 1,
                    radius,
                    radius,
                    strokePaint
            );

            textPaint.setTextSize(
                    dp(12)
            );

            if (mode == MODE_LOADING) {

                textPaint.setColor(
                        Color.WHITE
                );

                canvas.drawText(
                        "LOADING...",
                        w / 2f,
                        h / 2f
                                - (
                                textPaint.ascent()
                                        + textPaint.descent()
                        ) / 2f,
                        textPaint
                );

            } else {

                String resultText =
                        "STRONG "
                                + signal
                                + " "
                                + Math.round(score)
                                + "%";

                textPaint.setColor(
                        Color.WHITE
                );

                canvas.drawText(
                        resultText,
                        w / 2f,
                        h / 2f
                                - (
                                textPaint.ascent()
                                        + textPaint.descent()
                        ) / 2f,
                        textPaint
                );
            }
        }
    }

    // ============================================================
    // BLUE SCAN OVERLAY
    // ============================================================

    private final class ScanOverlay extends View {

        private final Paint fillPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint linePaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Handler animationHandler =
                new Handler();

        private float lineY = 0.0f;

        private boolean animating = false;

        private final Runnable animationRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (!animating) {
                            return;
                        }

                        lineY += dp(8);

                        if (lineY > getHeight()) {
                            lineY = 0.0f;
                        }

                        invalidate();

                        animationHandler.postDelayed(
                                this,
                                30L
                        );
                    }
                };

        ScanOverlay(Context context) {

            super(context);

            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );

            fillPaint.setStyle(
                    Paint.Style.FILL
            );

            linePaint.setStyle(
                    Paint.Style.STROKE
            );

            linePaint.setStrokeWidth(
                    dp(2)
            );
        }

        void startAnimation() {

            animating = true;

            lineY = 0.0f;

            animationHandler.removeCallbacks(
                    animationRunnable
            );

            animationHandler.post(
                    animationRunnable
            );

            invalidate();
        }

        void stopAnimation() {

            animating = false;

            animationHandler.removeCallbacks(
                    animationRunnable
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            int width =
                    getWidth();

            int height =
                    getHeight();

            /*
             * Full-screen translucent BLUE scanning layer.
             */
            fillPaint.setColor(
                    Color.argb(
                            42,
                            0,
                            90,
                            180
                    )
            );

            canvas.drawRect(
                    0,
                    0,
                    width,
                    height,
                    fillPaint
            );

            /*
             * Blue scan line.
             */
            linePaint.setColor(
                    Color.argb(
                            235,
                            0,
                            180,
                            255
                    )
            );

            linePaint.setStrokeWidth(
                    dp(2)
            );

            linePaint.setShadowLayer(
                    dp(9),
                    0,
                    0,
                    Color.argb(
                            210,
                            0,
                            150,
                            255
                    )
            );

            canvas.drawLine(
                    0,
                    lineY,
                    width,
                    lineY,
                    linePaint
            );

            linePaint.clearShadowLayer();

            /*
             * Secondary thin scan line.
             */
            linePaint.setColor(
                    Color.argb(
                            75,
                            100,
                            210,
                            255
                    )
            );

            linePaint.setStrokeWidth(
                    dp(1)
            );

            canvas.drawLine(
                    0,
                    lineY + dp(7),
                    width,
                    lineY + dp(7),
                    linePaint
            );
        }
    }
}
