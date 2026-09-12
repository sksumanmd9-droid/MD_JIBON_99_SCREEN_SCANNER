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
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class FloatingScannerService
        extends Service {

    private static volatile boolean running =
            false;

    private WindowManager windowManager;

    private View logoView;

    private ScanAnimationView scanView;

    private View resultView;

    private BroadcastReceiver receiver;

    private int savedX = 20;

    private int savedY = 250;

    private boolean scanRunning =
            false;

    private final Handler mainHandler =
            new Handler();

    private static final int NOTIFICATION_ID =
            2001;

    private static final String CHANNEL_ID =
            "md_jibon_floating";

    // ============================================================
    // PUBLIC STATE
    // ============================================================

    public static boolean isRunning() {

        return running;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Override
    public void onCreate() {

        super.onCreate();

        running = true;

        windowManager =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        createNotificationChannel();

        /*
         * Android 14+:
         * SPECIAL_USE foreground service type.
         *
         * Android 8-13:
         * normal foreground service startup.
         */
        try {

            if (Build.VERSION.SDK_INT >= 34) {

                startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        android.content.pm.ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );

            } else if (Build.VERSION.SDK_INT >= 26) {

                startForeground(
                        NOTIFICATION_ID,
                        createNotification()
                );

            } else {

                startForeground(
                        NOTIFICATION_ID,
                        createNotification()
                );
            }

        } catch (Exception e) {

            running = false;

            stopSelf();

            return;
        }

        if (!Settings.canDrawOverlays(this)) {

            Toast.makeText(
                    this,
                    "Overlay permission দিন",
                    Toast.LENGTH_LONG
            ).show();

            running = false;

            stopSelf();

            return;
        }

        registerScannerReceiver();

        createLogo();
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < 26) {

            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "MD JIBON Floating Scanner",
                        NotificationManager
                                .IMPORTANCE_LOW
                );

        channel.setDescription(
                "MD JIBON floating scanner"
        );

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if (manager != null) {

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private Notification createNotification() {

        if (Build.VERSION.SDK_INT >= 26) {

            return new Notification.Builder(
                    this,
                    CHANNEL_ID
            )
                    .setContentTitle(
                            "MD JIBON Scanner"
                    )
                    .setContentText(
                            "Floating scanner is active"
                    )
                    .setSmallIcon(
                            android.R.drawable
                                    .ic_menu_search
                    )
                    .setOngoing(true)
                    .build();
        }

        return new Notification.Builder(
                this
        )
                .setContentTitle(
                        "MD JIBON Scanner"
                )
                .setContentText(
                        "Floating scanner is active"
                )
                .setSmallIcon(
                        android.R.drawable
                                .ic_menu_search
                )
                .setOngoing(true)
                .build();
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

        if (intent != null) {

            String action =
                    intent.getAction();

            /*
             * Only explicit STOP should stop
             * the floating service.
             */
            if (ScreenCaptureService.ACTION_STOP
                    .equals(action)) {

                stopSelf();

                return START_NOT_STICKY;
            }
        }

        /*
         * Keep service alive under normal
         * Android service restart conditions.
         */
        return START_STICKY;
    }

    // ============================================================
    // RECEIVER
    // ============================================================

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

                        // ----------------------------------------
                        // PROGRESS
                        // ----------------------------------------

                        if (ScreenCaptureService
                                .ACTION_PROGRESS
                                .equals(action)) {

                            int progress =
                                    intent.getIntExtra(
                                            "progress",
                                            0
                                    );

                            if (scanView != null) {

                                scanView.setProgress(
                                        progress
                                );
                            }

                            return;
                        }

                        // ----------------------------------------
                        // RESULT
                        // ----------------------------------------

                        if (ScreenCaptureService
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

                            showResult(
                                    signal,
                                    confidence
                            );
                        }
                    }
                };

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                ScreenCaptureService
                        .ACTION_PROGRESS
        );

        filter.addAction(
                ScreenCaptureService
                        .ACTION_RESULT
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

    // ============================================================
    // DP
    // ============================================================

    private int dp(
            float value
    ) {

        return (int)
                (
                        value *
                                getResources()
                                        .getDisplayMetrics()
                                        .density
                                +
                                0.5f
                );
    }

    // ============================================================
    // OVERLAY PARAMS
    // ============================================================

    private WindowManager.LayoutParams
    overlayParams() {

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
                        WindowManager.LayoutParams
                                .WRAP_CONTENT,
                        WindowManager.LayoutParams
                                .WRAP_CONTENT,
                        type,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP |
                        Gravity.START;

        params.x = savedX;

        params.y = savedY;

        return params;
    }

    // ============================================================
    // SMALL FLOATING ICON
    // ============================================================

    private void createLogo() {

        if (windowManager == null) {

            return;
        }

        if (logoView != null) {

            return;
        }

        if (scanRunning) {

            return;
        }

        ImageView image =
                new ImageView(this);

        int drawableId =
                getResources()
                        .getIdentifier(
                                "md_jibon_logo",
                                "drawable",
                                getPackageName()
                        );

        if (drawableId != 0) {

            image.setImageResource(
                    drawableId
            );

        } else {

            image.setImageResource(
                    android.R.drawable
                            .ic_menu_search
            );
        }

        image.setScaleType(
                ImageView.ScaleType
                        .CENTER_INSIDE
        );

        int size =
                dp(46);

        image.setLayoutParams(
                new android.view.ViewGroup
                        .LayoutParams(
                                size,
                                size
                        )
        );

        logoView = image;

        final WindowManager.LayoutParams
                params =
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

                        switch (
                                event.getAction()
                        ) {

                            case MotionEvent
                                    .ACTION_DOWN:

                                downX =
                                        (int)
                                                event
                                                        .getRawX();

                                downY =
                                        (int)
                                                event
                                                        .getRawY();

                                startX =
                                        params.x;

                                startY =
                                        params.y;

                                downTime =
                                        System.currentTimeMillis();

                                return true;

                            case MotionEvent
                                    .ACTION_MOVE:

                                int dx =
                                        (int)
                                                event
                                                        .getRawX()
                                                -
                                                downX;

                                int dy =
                                        (int)
                                                event
                                                        .getRawY()
                                                -
                                                downY;

                                params.x =
                                        startX + dx;

                                params.y =
                                        startY + dy;

                                try {

                                    windowManager
                                            .updateViewLayout(
                                                    image,
                                                    params
                                            );

                                } catch (Exception ignored) {
                                }

                                return true;

                            case MotionEvent
                                    .ACTION_UP:

                                savedX =
                                        params.x;

                                savedY =
                                        params.y;

                                long duration =
                                        System.currentTimeMillis()
                                                -
                                                downTime;

                                int totalMove =
                                        Math.abs(
                                                (int)
                                                        event
                                                                .getRawX()
                                                        -
                                                        downX
                                        )
                                        +
                                        Math.abs(
                                                (int)
                                                        event
                                                                .getRawY()
                                                        -
                                                        downY
                                        );

                                /*
                                 * Short tap = scan.
                                 * Drag = move only.
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

    // ============================================================
    // START SCAN
    // ============================================================

    private void startScan() {

        if (scanRunning) {

            return;
        }

        /*
         * Capture must already be active.
         */
        if (!ScreenCaptureService
                .isCaptureActive()) {

            Toast.makeText(
                    this,
                    "আগে SCREEN CAPTURE ON করুন",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        scanRunning = true;

        /*
         * Remove only the small icon during scan.
         * The service itself stays alive.
         */
        removeView(
                logoView
        );

        logoView = null;

        createScanView();

        /*
         * IMPORTANT:
         *
         * ScreenCaptureService is already running
         * as a foreground service.
         *
         * Therefore do NOT call startForegroundService()
         * again here.
         *
         * Send only the scan command.
         */
        Intent scan =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        scan.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        try {

            startService(
                    scan
            );

        } catch (Exception e) {

            scanRunning = false;

            removeView(
                    scanView
            );

            scanView = null;

            createLogo();

            Toast.makeText(
                    this,
                    "Scanner start হয়নি",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ============================================================
    // SCAN VIEW
    // ============================================================

    private void createScanView() {

        if (windowManager == null) {

            return;
        }

        if (scanView != null) {

            return;
        }

        scanView =
                new ScanAnimationView(
                        this
                );

        WindowManager.LayoutParams
                params =
                new WindowManager.LayoutParams();

        params.width =
                WindowManager.LayoutParams
                        .MATCH_PARENT;

        params.height =
                WindowManager.LayoutParams
                        .MATCH_PARENT;

        if (Build.VERSION.SDK_INT >= 26) {

            params.type =
                    WindowManager.LayoutParams
                            .TYPE_APPLICATION_OVERLAY;

        } else {

            params.type =
                    WindowManager.LayoutParams
                            .TYPE_PHONE;
        }

        params.gravity =
                Gravity.TOP |
                        Gravity.START;

        params.flags =
                WindowManager.LayoutParams
                        .FLAG_NOT_FOCUSABLE
                        |
                        WindowManager.LayoutParams
                                .FLAG_NOT_TOUCHABLE
                        |
                        WindowManager.LayoutParams
                                .FLAG_LAYOUT_IN_SCREEN;

        params.format =
                PixelFormat.TRANSLUCENT;

        try {

            windowManager.addView(
                    scanView,
                    params
            );

        } catch (Exception e) {

            scanView = null;

            scanRunning = false;

            createLogo();
        }
    }

    // ============================================================
    // SCAN ANIMATION
    // ============================================================

    private class ScanAnimationView
            extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private float scanY =
                -dp(110);

        private int progress =
                0;

        private boolean animationRunning =
                true;

        private final Runnable
                animationRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (!animationRunning) {

                            return;
                        }

                        scanY += dp(8);

                        if (scanY >
                                getHeight()) {

                            scanY =
                                    -dp(110);
                        }

                        invalidate();

                        postDelayed(
                                this,
                                24
                        );
                    }
                };

        ScanAnimationView(
                Context context
        ) {

            super(context);

            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );

            post(
                    animationRunnable
            );
        }

        void setProgress(
                int value
        ) {

            progress =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    value
                            )
                    );

            invalidate();
        }

        void stopAnimation() {

            animationRunning =
                    false;

            removeCallbacks(
                    animationRunnable
            );
        }

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(
                    canvas
            );

            int width =
                    getWidth();

            int height =
                    getHeight();

            // ----------------------------------------------------
            // MOVING BLUE BAND
            // ----------------------------------------------------

            float bandHeight =
                    dp(110);

            float top =
                    scanY;

            float bottom =
                    scanY +
                            bandHeight;

            Paint glow =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            glow.setColor(
                    Color.argb(
                            30,
                            20,
                            180,
                            255
                    )
            );

            canvas.drawRect(
                    0,
                    top,
                    width,
                    bottom,
                    glow
            );

            Paint middle =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            middle.setColor(
                    Color.argb(
                            65,
                            30,
                            190,
                            255
                    )
            );

            float middleTop =
                    scanY +
                            dp(38);

            float middleBottom =
                    scanY +
                            dp(72);

            canvas.drawRect(
                    0,
                    middleTop,
                    width,
                    middleBottom,
                    middle
            );

            Paint edge =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            edge.setColor(
                    Color.argb(
                            180,
                            30,
                            210,
                            255
                    )
            );

            edge.setStrokeWidth(
                    dp(2)
            );

            float edgeY =
                    scanY +
                            dp(55);

            canvas.drawLine(
                    0,
                    edgeY,
                    width,
                    edgeY,
                    edge
            );

            // ----------------------------------------------------
            // PROGRESS
            // ----------------------------------------------------

            if (progress > 0 &&
                    progress < 100) {

                Paint progressPaint =
                        new Paint(
                                Paint.ANTI_ALIAS_FLAG
                        );

                progressPaint.setColor(
                        Color.argb(
                                190,
                                30,
                                210,
                                255
                        )
                );

                float progressWidth =
                        width *
                                (progress / 100f);

                canvas.drawRect(
                        0,
                        Math.max(
                                0,
                                height - dp(3)
                        ),
                        progressWidth,
                        height,
                        progressPaint
                );
            }
        }
    }

    // ============================================================
    // SHOW RESULT
    // ============================================================

    private void showResult(
            String signal,
            int confidence
    ) {

        scanRunning = false;

        // --------------------------------------------------------
        // Stop scan animation
        // --------------------------------------------------------

        if (scanView != null) {

            scanView.stopAnimation();

            removeView(
                    scanView
            );

            scanView = null;
        }

        // --------------------------------------------------------
        // Recreate floating icon
        // --------------------------------------------------------

        createLogo();

        // --------------------------------------------------------
        // Remove old result
        // --------------------------------------------------------

        if (resultView != null) {

            removeView(
                    resultView
            );

            resultView = null;
        }

        if (signal == null) {

            signal =
                    "NO TRADE";
        }

        if (!"UP".equals(signal) &&
                !"DOWN".equals(signal) &&
                !"NO TRADE".equals(signal)) {

            signal =
                    "NO TRADE";
        }

        int safeConfidence =
                Math.max(
                        0,
                        Math.min(
                                100,
                                confidence
                        )
                );

        // --------------------------------------------------------
        // RESULT CONTAINER
        // --------------------------------------------------------

        LinearLayout box =
                new LinearLayout(
                        this
                );

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setGravity(
                Gravity.CENTER
        );

        int padding =
                dp(8);

        box.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.rgb(
                        8,
                        16,
                        28
                )
        );

        background.setCornerRadius(
                dp(14)
        );

        background.setStroke(
                dp(1),
                Color.argb(
                        100,
                        40,
                        220,
                        255
                )
        );

        box.setBackground(
                background
        );

        // --------------------------------------------------------
        // SIGNAL
        // --------------------------------------------------------

        TextView signalText =
                new TextView(
                        this
                );

        signalText.setText(
                signal
        );

        signalText.setTextSize(
                17
        );

        signalText.setTypeface(
                Typeface.DEFAULT_BOLD
        );

        signalText.setGravity(
                Gravity.CENTER
        );

        // --------------------------------------------------------
        // COLOR
        // --------------------------------------------------------

        if ("UP".equals(signal)) {

            signalText.setTextColor(
                    Color.rgb(
                            30,
                            235,
                            135
                    )
            );

        } else if ("DOWN".equals(signal)) {

            signalText.setTextColor(
                    Color.rgb(
                            255,
                            70,
                            85
                    )
            );

        } else {

            signalText.setTextColor(
                    Color.WHITE
            );
        }

        box.addView(
                signalText,
                new LinearLayout.LayoutParams(
                        dp(85),
                        dp(28)
                )
        );

        // --------------------------------------------------------
        // CONFIDENCE
        // --------------------------------------------------------

        if (!"NO TRADE".equals(signal)) {

            TextView confidenceText =
                    new TextView(
                            this
                    );

            confidenceText.setText(
                    safeConfidence +
                            "%"
            );

            confidenceText.setTextSize(
                    13
            );

            confidenceText.setTextColor(
                    Color.WHITE
            );

            confidenceText.setGravity(
                    Gravity.CENTER
            );

            box.addView(
                    confidenceText,
                    new LinearLayout.LayoutParams(
                            dp(85),
                            dp(22)
                    )
            );
        }

        resultView = box;

        // --------------------------------------------------------
        // RESULT POSITION
        // --------------------------------------------------------

        WindowManager.LayoutParams
                params =
                overlayParams();

        params.x =
                savedX;

        params.y =
                savedY +
                        dp(52);

        try {

            windowManager.addView(
                    resultView,
                    params
            );

        } catch (Exception e) {

            resultView = null;
        }

        // --------------------------------------------------------
        // Auto-hide result after a short time.
        // Floating scanner and Screen Capture
        // remain active.
        // --------------------------------------------------------

        mainHandler.postDelayed(
                () -> {

                    if (resultView != null) {

                        removeView(
                                resultView
                        );

                        resultView = null;
                    }

                },
                3500
        );
    }

    // ============================================================
    // REMOVE VIEW
    // ============================================================

    private void removeView(
            View view
    ) {

        if (view == null ||
                windowManager == null) {

            return;
        }

        try {

            windowManager.removeView(
                    view
            );

        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    @Override
    public void onDestroy() {

        running = false;

        scanRunning = false;

        if (mainHandler != null) {

            mainHandler.removeCallbacksAndMessages(
                    null
            );
        }

        if (receiver != null) {

            try {

                unregisterReceiver(
                        receiver
                );

            } catch (Exception ignored) {
            }

            receiver = null;
        }

        if (scanView != null) {

            scanView.stopAnimation();

            removeView(
                    scanView
            );

            scanView = null;
        }

        removeView(
                logoView
        );

        logoView = null;

        removeView(
                resultView
        );

        resultView = null;

        super.onDestroy();
    }

    // ============================================================
    // BIND
    // ============================================================

    @Nullable
    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
}
