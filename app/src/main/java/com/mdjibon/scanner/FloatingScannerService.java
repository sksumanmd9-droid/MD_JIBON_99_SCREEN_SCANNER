package com.mdjibon.scanner;

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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class FloatingScannerService extends Service {

    private static volatile boolean running = false;

    private WindowManager windowManager;

    private View logoView;
    private ScanAnimationView scanView;
    private View resultView;

    private BroadcastReceiver receiver;

    private int savedX = 20;
    private int savedY = 250;

    private final Handler mainHandler =
            new Handler();

    private boolean scanRunning = false;

    // ============================================================
    // SERVICE STATE
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

            running = false;

            stopSelf();

            return;
        }

        createLogo();
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

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

                        // ------------------------------
                        // PROGRESS
                        // ------------------------------

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

                        // ------------------------------
                        // RESULT
                        // ------------------------------

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

    // ============================================================
    // DP
    // ============================================================

    private int dp(float value) {

        return (int)
                (
                        value *
                                getResources()
                                        .getDisplayMetrics()
                                        .density
                                + 0.5f
                );
    }

    // ============================================================
    // OVERLAY PARAMS
    // ============================================================

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
                                .FLAG_NOT_FOCUSABLE
                                |
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

        ImageView image =
                new ImageView(this);

        int drawableId =
                getResources()
                        .getIdentifier(
                                "md_jibon_logo",
                                "drawable",
                                getPackageName()
                        );

        image.setImageResource(
                drawableId
        );

        image.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        /*
         * ছোট app-এর মতো floating icon
         */
        int size =
                dp(46);

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

                        switch (
                                event.getAction()
                        ) {

                            case MotionEvent.ACTION_DOWN:

                                downX =
                                        (int)
                                                event.getRawX();

                                downY =
                                        (int)
                                                event.getRawY();

                                startX =
                                        params.x;

                                startY =
                                        params.y;

                                downTime =
                                        System.currentTimeMillis();

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                int dx =
                                        (int)
                                                event.getRawX()
                                                - downX;

                                int dy =
                                        (int)
                                                event.getRawY()
                                                - downY;

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

                            case MotionEvent.ACTION_UP:

                                savedX =
                                        params.x;

                                savedY =
                                        params.y;

                                long duration =
                                        System.currentTimeMillis()
                                                - downTime;

                                int totalMove =
                                        Math.abs(
                                                (int)
                                                        event.getRawX()
                                                        - downX
                                        )
                                        +
                                        Math.abs(
                                                (int)
                                                        event.getRawY()
                                                        - downY
                                        );

                                /*
                                 * শুধু tap করলে scan হবে।
                                 * drag করলে scan হবে না।
                                 */
                                if (duration < 350
                                        &&
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
         * Screen Capture অবশ্যই ON থাকতে হবে।
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
         * Floating icon সাময়িকভাবে hide।
         */
        removeView(
                logoView
        );

        /*
         * ছোট scanning animation।
         */
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

                startService(
                        scan
                );
            }

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
    // SCAN ANIMATION
    // ============================================================

    private void createScanView() {

        scanView =
                new ScanAnimationView(
                        this
                );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams();

        /*
         * পুরো screen transparent থাকবে।
         * শুধু চলমান blue scanning layer দেখা যাবে।
         */
        params.width =
                WindowManager.LayoutParams.MATCH_PARENT;

        params.height =
                WindowManager.LayoutParams.MATCH_PARENT;

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
                        .FLAG_LAYOUT_IN_SCREEN
                        |
                        WindowManager.LayoutParams
                        .FLAG_LAYOUT_NO_LIMITS;

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
    // SCAN ANIMATION VIEW
    // ============================================================

    private class ScanAnimationView
            extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        /*
         * scanning band-এর current position
         */
        private float scanY =
                -dp(70);

        private int progress = 0;

        private boolean animationRunning =
                true;

        private final Runnable animationRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (!animationRunning) {
                            return;
                        }

                        /*
                         * উপর থেকে নিচে চলবে।
                         */
                        scanY +=
                                dp(9);

                        if (scanY >
                                getHeight()) {

                            /*
                             * analysis যদি একটু বেশি সময় নেয়,
                             * আবার উপর থেকে শুরু করবে।
                             */
                            scanY =
                                    -dp(70);
                        }

                        invalidate();

                        postDelayed(
                                this,
                                24
                        );
                    }
                };

        public ScanAnimationView(
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

        public void setProgress(
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

        public void stopAnimation() {

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

            super.onDraw(canvas);

            int width =
                    getWidth();

            int height =
                    getHeight();

            /*
             * ====================================================
             * BLUE SCANNING COVER
             * ====================================================
             *
             * এটি মাঝখানে স্থির কোনো দাগ নয়।
             *
             * উপর থেকে নিচে একটি translucent blue
             * scanning area চলে যাবে।
             */

            float bandHeight =
                    dp(110);

            float top =
                    scanY;

            float bottom =
                    scanY +
                            bandHeight;

            /*
             * বড় soft blue scanning glow।
             */
            Paint glow =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            glow.setColor(
                    Color.argb(
                            32,
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

            /*
             * scanning-এর মাঝের উজ্জ্বল অংশ।
             */
            Paint middle =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            middle.setColor(
                    Color.argb(
                            72,
                            30,
                            190,
                            255
                    )
            );

            float middleTop =
                    scanY +
                            dp(40);

            float middleBottom =
                    scanY +
                            dp(70);

            canvas.drawRect(
                    0,
                    middleTop,
                    width,
                    middleBottom,
                    middle
            );

            /*
             * একটি খুব পাতলা bright blue edge।
             *
             * এটা স্থায়ী center line নয়।
             * scanning band-এর সঙ্গে উপর থেকে নিচে যায়।
             */
            Paint edge =
                    new Paint(
                            Paint.ANTI_ALIAS_FLAG
                    );

            edge.setColor(
                    Color.argb(
                            190,
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

            /*
             * Progress indicator খুব ছোট করে।
             * কোনো বড় text নেই।
             */
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
    // SHOW SMALL RESULT
    // ============================================================

    private void showResult(
            String signal,
            int confidence
    ) {

        scanRunning = false;

        /*
         * Scan animation বন্ধ।
         */
        if (scanView != null) {

            scanView.stopAnimation();

            removeView(
                    scanView
            );

            scanView = null;
        }

        /*
         * পুরোনো result থাকলে remove।
         */
        removeView(
                resultView
        );

        if (signal == null) {

            signal =
                    "NO TRADE";
        }

        /*
         * Invalid signal হলে NO TRADE।
         */
        if (!"UP".equals(signal)
                &&
                !"DOWN".equals(signal)
                &&
                !"NO TRADE".equals(signal)) {

            signal =
                    "NO TRADE";
        }

        /*
         * --------------------------------------------------------
         * ছোট result container
         * --------------------------------------------------------
         */
        android.widget.LinearLayout container =
                new android.widget.LinearLayout(
                        this
                );

        container.setOrientation(
                android.widget.LinearLayout.HORIZONTAL
        );

        container.setGravity(
                Gravity.CENTER_VERTICAL
        );

        container.setPadding(
                dp(5),
                dp(4),
                dp(8),
                dp(4)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.argb(
                        245,
                        5,
                        12,
                        22
                )
        );

        background.setCornerRadius(
                dp(14)
        );

        int borderColor;

        if ("UP".equals(signal)) {

            borderColor =
                    Color.rgb(
                            20,
                            235,
                            120
                    );

        } else if ("DOWN".equals(signal)) {

            borderColor =
                    Color.rgb(
                            255,
                            55,
                            65
                    );

        } else {

            borderColor =
                    Color.rgb(
                            120,
                            150,
                            175
                    );
        }

        background.setStroke(
                dp(1),
                borderColor
        );

        container.setBackground(
                background
        );

        // ========================================================
        // SMALL LOGO
        // ========================================================

        ImageView icon =
                new ImageView(this);

        int drawableId =
                getResources()
                        .getIdentifier(
                                "md_jibon_logo",
                                "drawable",
                                getPackageName()
                        );

        icon.setImageResource(
                drawableId
        );

        icon.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        android.widget.LinearLayout.LayoutParams
                iconParams =
                new android.widget.LinearLayout.LayoutParams(
                        dp(34),
                        dp(34)
                );

        iconParams.rightMargin =
                dp(5);

        container.addView(
                icon,
                iconParams
        );

        // ========================================================
        // SIGNAL TEXT
        // ========================================================

        TextView resultText =
                new TextView(this);

        String text;

        if ("UP".equals(signal)) {

            text =
                    "↑ UP  " +
                            confidence +
                            "%";

            resultText.setTextColor(
                    Color.rgb(
                            25,
                            240,
                            125
                    )
            );

        } else if ("DOWN".equals(signal)) {

            text =
                    "↓ DOWN  " +
                            confidence +
                            "%";

            resultText.setTextColor(
                    Color.rgb(
                            255,
                            60,
                            70
                    )
            );

        } else {

            text =
                    "WAIT";

            resultText.setTextColor(
                    Color.WHITE
            );
        }

        resultText.setText(
                text
        );

        /*
         * ছোট লেখা।
         */
        resultText.setTextSize(
                15
        );

        resultText.setGravity(
                Gravity.CENTER_VERTICAL
        );

        resultText.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        );

        container.addView(
                resultText,
                new android.widget.LinearLayout.LayoutParams(
                        dp(95),
                        dp(40)
                )
        );

        resultView =
                container;

        // ========================================================
        // RESULT POSITION
        // ========================================================

        WindowManager.LayoutParams params =
                overlayParams();

        /*
         * Result icon-এর কাছাকাছি থাকবে।
         */
        params.width =
                dp(145);

        params.height =
                dp(50);

        params.x =
                Math.max(
                        0,
                        savedX
                );

        params.y =
                Math.max(
                        0,
                        savedY
                );

        try {

            windowManager.addView(
                    container,
                    params
            );

        } catch (Exception ignored) {

            resultView = null;

            createLogo();

            return;
        }

        /*
         * Result 3 seconds থাকবে।
         */
        final View currentResult =
                resultView;

        mainHandler.postDelayed(
                () -> {

                    removeView(
                            currentResult
                    );

                    if (resultView ==
                            currentResult) {

                        resultView =
                                null;
                    }

                    /*
                     * আবার ছোট floating icon।
                     */
                    createLogo();

                },
                3000
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

        if (scanView != null) {

            scanView.stopAnimation();
        }

        mainHandler.removeCallbacksAndMessages(
                null
        );

        if (receiver != null) {

            try {

                unregisterReceiver(
                        receiver
                );

            } catch (Exception ignored) {
            }

            receiver = null;
        }

        removeView(
                logoView
        );

        removeView(
                scanView
        );

        removeView(
                resultView
        );

        logoView = null;
        scanView = null;
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
