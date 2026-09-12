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
import android.graphics.Typeface;
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

    private boolean scanRunning = false;

    private final Handler mainHandler =
            new Handler();

    private static final int
            NOTIFICATION_ID = 2001;

    private static final String
            CHANNEL_ID =
            "md_jibon_floating";

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

        createNotificationChannel();

        try {

            if (Build.VERSION.SDK_INT >= 29) {

                startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        android.content.pm.ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
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

        return new Notification.Builder(this)
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

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent != null &&
                ScreenCaptureService
                        .ACTION_STOP
                        .equals(
                                intent.getAction()
                        )) {

            stopSelf();
        }

        return START_STICKY;
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

                            return;
                        }

                        if (ScreenCaptureService
                                .ACTION_ERROR
                                .equals(action)) {

                            scanRunning = false;

                            removeView(
                                    scanView
                            );

                            scanView = null;

                            createLogo();

                            String message =
                                    intent.getStringExtra(
                                            "message"
                                    );

                            if (message == null) {
                                message =
                                        "Scanner error";
                            }

                            Toast.makeText(
                                    FloatingScannerService.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
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
    }

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

    private void createLogo() {

        if (windowManager == null ||
                logoView != null ||
                scanRunning) {

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
                ImageView.ScaleType.CENTER_INSIDE
        );

        int size =
                dp(44);

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

        if (scanRunning) {
            return;
        }

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

        removeView(logoView);
        logoView = null;

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

            /*
             * ScreenCaptureService ইতিমধ্যেই
             * foreground service হিসেবে চলছে।
             * তাই scan-এর জন্য নতুন FGS start
             * করার প্রয়োজন নেই।
             */
            startService(scan);

        } catch (Exception e) {

            scanRunning = false;

            removeView(scanView);
            scanView = null;

            createLogo();

            Toast.makeText(
                    this,
                    "Scanner start হয়নি",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void createScanView() {

        if (windowManager == null) {
            return;
        }

        scanView =
                new ScanAnimationView(this);

        WindowManager.LayoutParams params =
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

    private class ScanAnimationView
            extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private float scanY =
                -dp(110);

        private int progress = 0;

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

                        if (scanY > getHeight()) {

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

            animationRunning = false;

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
             * শুধু translucent blue scanning band।
             * কোনো radar নেই।
             * কোনো arrow নেই।
             */

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

    private void showResult(
            String signal,
            int confidence
    ) {

        scanRunning = false;

        if (scanView != null) {

            scanView.stopAnimation();

            removeView(scanView);

            scanView = null;
        }

        removeView(resultView);

        resultView = null;

        if (signal == null) {
            signal = "NO TRADE";
        }

        if (!"UP".equals(signal) &&
                !"DOWN".equals(signal) &&
                !"NO TRADE".equals(signal)) {

            signal = "NO TRADE";
        }

        confidence =
                Math.max(
                        0,
                        Math.min(
                                100,
                                confidence
                        )
                );

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.HORIZONTAL
        );

        container.setGravity(
                Gravity.CENTER_VERTICAL
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.argb(
                        235,
                        8,
                        15,
                        25
                )
        );

        background.setCornerRadius(
                dp(12)
        );

        background.setStroke(
                dp(1),
                Color.argb(
                        100,
                        70,
                        180,
                        220
                )
        );

        container.setBackground(
                background
        );

        ImageView icon =
                new ImageView(this);

        int drawableId =
                getResources()
                        .getIdentifier(
                                "md_jibon_logo",
                                "drawable",
                                getPackageName()
                        );

        if (drawableId != 0) {

            icon.setImageResource(
                    drawableId
            );
        }

        LinearLayout.LayoutParams
                iconParams =
                new LinearLayout.LayoutParams(
                        dp(30),
                        dp(30)
                );

        iconParams.setMargins(
                dp(8),
                0,
                dp(5),
                0
        );

        container.addView(
                icon,
                iconParams
        );

        TextView resultText =
                new TextView(this);

        resultText.setTypeface(
                Typeface.DEFAULT_BOLD
        );

        resultText.setTextSize(
                14
        );

        if ("UP".equals(signal)) {

            resultText.setText(
                    "↑ UP " +
                            confidence +
                            "%"
            );

            resultText.setTextColor(
                    Color.rgb(
                            30,
                            235,
                            135
                    )
            );

        } else if ("DOWN".equals(signal)) {

            resultText.setText(
                    "↓ DOWN " +
                            confidence +
                            "%"
            );

            resultText.setTextColor(
                    Color.rgb(
                            255,
                            70,
                            85
                    )
            );

        } else {

            resultText.setText(
                    "WAIT"
            );

            resultText.setTextColor(
                    Color.WHITE
            );
        }

        resultText.setGravity(
                Gravity.CENTER_VERTICAL
        );

        container.addView(
                resultText,
                new LinearLayout.LayoutParams(
                        dp(100),
                        dp(50)
                )
        );

        resultView =
                container;

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams();

        params.width =
                dp(145);

        params.height =
                dp(50);

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

        params.x = savedX;
        params.y =
                savedY + dp(50);

        params.flags =
                WindowManager.LayoutParams
                        .FLAG_NOT_FOCUSABLE;

        params.format =
                PixelFormat.TRANSLUCENT;

        try {

            windowManager.addView(
                    resultView,
                    params
            );

        } catch (Exception e) {

            resultView = null;

            createLogo();

            return;
        }

        mainHandler.postDelayed(
                () -> {

                    removeView(
                            resultView
                    );

                    resultView = null;

                    createLogo();

                },
                3000
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

            windowManager.removeView(
                    view
            );

        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {

        running = false;
        scanRunning = false;

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
        }

        removeView(logoView);
        removeView(scanView);
        removeView(resultView);

        logoView = null;
        scanView = null;
        resultView = null;

        mainHandler.removeCallbacksAndMessages(
                null
        );

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}
