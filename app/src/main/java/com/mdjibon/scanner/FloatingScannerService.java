package com.mdjibon.scanner;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingScannerService extends Service {

    private static boolean running = false;

    private WindowManager windowManager;

    private LogoButton logoButton;

    private WindowManager.LayoutParams logoParams;

    private View scanView;
    private WindowManager.LayoutParams scanParams;

    private View resultView;
    private WindowManager.LayoutParams resultParams;

    private BroadcastReceiverHolder receiver;

    private boolean dragging = false;
    private boolean scanVisible = false;

    private float downRawX;
    private float downRawY;

    private int downX;
    private int downY;

    private final Handler handler =
            new Handler();

    public static boolean isRunning() {
        return running;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        running = true;

        if (logoButton == null) {
            createLogo();
        }

        if (receiver == null) {
            receiver =
                    new BroadcastReceiverHolder(
                            this
                    );

            receiver.register();
        }

        return START_STICKY;
    }

    private void createLogo() {

        windowManager =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        logoButton =
                new LogoButton(this);

        logoParams =
                new WindowManager.LayoutParams(
                        dp(58),
                        dp(58),
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        android.graphics.PixelFormat.TRANSLUCENT
                );

        SharedPreferences prefs =
                getSharedPreferences(
                        "scanner_position",
                        MODE_PRIVATE
                );

        logoParams.gravity =
                Gravity.TOP |
                Gravity.LEFT;

        logoParams.x =
                prefs.getInt(
                        "x",
                        18
                );

        logoParams.y =
                prefs.getInt(
                        "y",
                        300
                );

        logoButton.setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event
                    ) {

                        switch (event.getActionMasked()) {

                            case MotionEvent.ACTION_DOWN:

                                dragging = false;

                                downRawX =
                                        event.getRawX();

                                downRawY =
                                        event.getRawY();

                                downX =
                                        logoParams.x;

                                downY =
                                        logoParams.y;

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                float dx =
                                        event.getRawX()
                                        - downRawX;

                                float dy =
                                        event.getRawY()
                                        - downRawY;

                                if (Math.abs(dx) > dp(8) ||
                                        Math.abs(dy) > dp(8)) {

                                    dragging = true;

                                    logoParams.x =
                                            downX +
                                            (int) dx;

                                    logoParams.y =
                                            downY +
                                            (int) dy;

                                    clampLogo();

                                    windowManager.updateViewLayout(
                                            logoButton,
                                            logoParams
                                    );
                                }

                                return true;

                            case MotionEvent.ACTION_UP:

                                if (!dragging) {
                                    startScan();
                                } else {

                                    prefs.edit()
                                            .putInt(
                                                    "x",
                                                    logoParams.x
                                            )
                                            .putInt(
                                                    "y",
                                                    logoParams.y
                                            )
                                            .apply();
                                }

                                return true;
                        }

                        return true;
                    }
                }
        );

        windowManager.addView(
                logoButton,
                logoParams
        );
    }

    private void clampLogo() {

        int width =
                getResources()
                        .getDisplayMetrics()
                        .widthPixels;

        int height =
                getResources()
                        .getDisplayMetrics()
                        .heightPixels;

        logoParams.x =
                Math.max(
                        0,
                        Math.min(
                                logoParams.x,
                                width - dp(58)
                        )
                );

        logoParams.y =
                Math.max(
                        0,
                        Math.min(
                                logoParams.y,
                                height - dp(58)
                        )
                );
    }

    private void startScan() {

        if (scanVisible) {
            return;
        }

        if (!ScreenCaptureService.isCaptureActive()) {

            showResult(
                    "SCREEN CAPTURE",
                    0,
                    "Screen Capture OFF",
                    0,
                    0,
                    false
            );

            return;
        }

        scanVisible = true;

        logoButton.setVisibility(
                View.INVISIBLE
        );

        showScanView();

        Intent scan =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        scan.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        startService(scan);
    }

    private void showScanView() {

        final ScanView view =
                new ScanView(this);

        scanView = view;

        scanParams =
                new WindowManager.LayoutParams(
                        dp(290),
                        dp(340),
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        android.graphics.PixelFormat.TRANSLUCENT
                );

        scanParams.gravity =
                Gravity.CENTER;

        windowManager.addView(
                scanView,
                scanParams
        );
    }

    private void showResult(
            String signal,
            int confidence,
            String timeframe,
            int candles,
            int quality,
            boolean realSignal
    ) {

        removeScanView();

        scanVisible = false;

        if (logoButton != null) {
            logoButton.setVisibility(
                    View.VISIBLE
            );
        }

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(
                Gravity.CENTER
        );

        card.setPadding(
                dp(15),
                dp(12),
                dp(15),
                dp(12)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.rgb(5, 18, 35)
        );

        background.setCornerRadius(
                dp(18)
        );

        if ("UP".equals(signal)) {

            background.setStroke(
                    dp(2),
                    Color.rgb(25, 235, 135)
            );

        } else if ("DOWN".equals(signal)) {

            background.setStroke(
                    dp(2),
                    Color.rgb(255, 65, 80)
            );

        } else {

            background.setStroke(
                    dp(2),
                    Color.rgb(30, 160, 255)
            );
        }

        card.setBackground(
                background
        );

        ImageView logo =
                new ImageView(this);

        logo.setImageResource(
                R.drawable.md_jibon_logo
        );

        logo.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        card.addView(
                logo,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );

        TextView signalText =
                new TextView(this);

        signalText.setText(
                signal
        );

        signalText.setTextSize(24);

        signalText.setGravity(
                Gravity.CENTER
        );

        if ("UP".equals(signal)) {

            signalText.setTextColor(
                    Color.rgb(30, 235, 135)
            );

        } else if ("DOWN".equals(signal)) {

            signalText.setTextColor(
                    Color.rgb(255, 65, 80)
            );

        } else {

            signalText.setTextColor(
                    Color.WHITE
            );
        }

        card.addView(signalText);

        TextView score =
                new TextView(this);

        score.setText(
                realSignal
                        ? confidence + "%"
                        : timeframe
        );

        score.setTextSize(19);

        score.setGravity(
                Gravity.CENTER
        );

        score.setTextColor(
                Color.WHITE
        );

        card.addView(score);

        TextView details =
                new TextView(this);

        if (realSignal) {

            details.setText(
                    timeframe +
                    "\nCandles: " +
                    candles +
                    "\nQuality: " +
                    quality +
                    "%"
            );

        } else {

            details.setText(
                    "Tap scanner again after\n" +
                    "Screen Capture is enabled."
            );
        }

        details.setTextSize(12);

        details.setGravity(
                Gravity.CENTER
        );

        details.setTextColor(
                Color.LTGRAY
        );

        card.addView(details);

        resultView = card;

        resultParams =
                new WindowManager.LayoutParams(
                        dp(190),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        android.graphics.PixelFormat.TRANSLUCENT
                );

        resultParams.gravity =
                Gravity.TOP |
                Gravity.LEFT;

        resultParams.x =
                logoParams.x;

        resultParams.y =
                Math.max(
                        10,
                        logoParams.y - dp(205)
                );

        card.setOnClickListener(
                v -> dismissResult()
        );

        windowManager.addView(
                resultView,
                resultParams
        );
    }

    private void dismissResult() {

        if (resultView != null) {

            try {
                windowManager.removeView(
                        resultView
                );
            } catch (Exception ignored) {
            }

            resultView = null;
        }

        if (logoButton != null) {
            logoButton.setVisibility(
                    View.VISIBLE
            );
        }
    }

    private void removeScanView() {

        if (scanView != null) {

            try {
                windowManager.removeView(
                        scanView
                );
            } catch (Exception ignored) {
            }

            scanView = null;
        }
    }

    private int dp(int value) {

        return (int)
                (value *
                        getResources()
                                .getDisplayMetrics()
                                .density +
                        0.5f);
    }

    private class LogoButton extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private Bitmap bitmap;

        LogoButton(Context context) {

            super(context);

            bitmap =
                    android.graphics.BitmapFactory
                            .decodeResource(
                                    getResources(),
                                    R.drawable.md_jibon_logo
                            );

            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );
        }

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(canvas);

            RectF dst =
                    new RectF(
                            0,
                            0,
                            getWidth(),
                            getHeight()
                    );

            canvas.drawBitmap(
                    bitmap,
                    null,
                    dst,
                    paint
            );
        }
    }

    private class ScanView extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private Bitmap bitmap;

        private float progress = 0;

        private long startTime;

        ScanView(Context context) {

            super(context);

            bitmap =
                    android.graphics.BitmapFactory
                            .decodeResource(
                                    getResources(),
                                    R.drawable.md_jibon_logo
                            );

            startTime =
                    System.currentTimeMillis();

            handler.post(
                    animation
            );
        }

        private final Runnable animation =
                new Runnable() {

                    @Override
                    public void run() {

                        long elapsed =
                                System.currentTimeMillis()
                                - startTime;

                        progress =
                                Math.min(
                                        100f,
                                        elapsed / 22f
                                );

                        invalidate();

                        if (progress < 100) {
                            handler.postDelayed(
                                    this,
                                    20
                            );
                        }
                    }
                };

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();

            paint.setColor(
                    Color.argb(
                            245,
                            3,
                            17,
                            38
                    )
            );

            canvas.drawRoundRect(
                    new RectF(
                            0,
                            0,
                            w,
                            h
                    ),
                    dp(30),
                    dp(30),
                    paint
            );

            float size =
                    Math.min(
                            w - dp(30),
                            dp(245)
                    );

            float left =
                    (w - size) / 2f;

            float top =
                    dp(18);

            RectF logoRect =
                    new RectF(
                            left,
                            top,
                            left + size,
                            top + size
                    );

            paint.setAlpha(255);

            canvas.drawBitmap(
                    bitmap,
                    null,
                    logoRect,
                    paint
            );

            paint.setColor(
                    Color.rgb(
                            0,
                            190,
                            255
                    )
            );

            paint.setStrokeWidth(
                    dp(3)
            );

            float lineY =
                    top +
                    (size *
                            progress /
                            100f);

            canvas.drawLine(
                    left,
                    lineY,
                    left + size,
                    lineY,
                    paint
            );

            paint.setTextAlign(
                    Paint.Align.CENTER
            );

            paint.setTextSize(
                    dp(20)
            );

            paint.setColor(
                    Color.WHITE
            );

            canvas.drawText(
                    "SCANNING...",
                    w / 2f,
                    dp(292),
                    paint
            );

            paint.setTextSize(
                    dp(25)
            );

            paint.setColor(
                    Color.rgb(
                            30,
                            190,
                            255
                    )
            );

            canvas.drawText(
                    ((int) progress) +
                    "/100",
                    w / 2f,
                    dp(325),
                    paint
            );
        }
    }

    private class BroadcastReceiverHolder
            extends android.content.BroadcastReceiver {

        private final Context context;

        BroadcastReceiverHolder(Context c) {
            context = c;
        }

        void register() {

            IntentFilter filter =
                    new IntentFilter();

            filter.addAction(
                    ScreenCaptureService.ACTION_PROGRESS
            );

            filter.addAction(
                    ScreenCaptureService.ACTION_RESULT
            );

            if (Build.VERSION.SDK_INT >= 33) {

                context.registerReceiver(
                        this,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                );

            } else {

                context.registerReceiver(
                        this,
                        filter
                );
            }
        }

        @Override
        public void onReceive(
                Context context,
                Intent intent
        ) {

            String action =
                    intent.getAction();

            if (ScreenCaptureService.ACTION_PROGRESS
                    .equals(action)) {

                if (scanView instanceof ScanView) {

                    ScanView view =
                            (ScanView) scanView;

                    view.progress =
                            intent.getIntExtra(
                                    "progress",
                                    0
                            );

                    view.invalidate();
                }
            }

            if (ScreenCaptureService.ACTION_RESULT
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

                int candles =
                        intent.getIntExtra(
                                "detectedCandles",
                                0
                        );

                int quality =
                        intent.getIntExtra(
                                "quality",
                                0
                        );

                String timeframe =
                        intent.getStringExtra(
                                "timeframe"
                        );

                String candleSize =
                        intent.getStringExtra(
                                "candleSize"
                        );

                showResult(
                        signal,
                        confidence,
                        timeframe +
                                " • " +
                                candleSize,
                        candles,
                        quality,
                        true
                );
            }
        }
    }

    @Override
    public void onDestroy() {

        running = false;

        handler.removeCallbacksAndMessages(
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

        removeScanView();

        if (resultView != null) {

            try {
                windowManager.removeView(
                        resultView
                );
            } catch (Exception ignored) {
            }

            resultView = null;
        }

        if (logoButton != null) {

            try {
                windowManager.removeView(
                        logoButton
                );
            } catch (Exception ignored) {
            }

            logoButton = null;
        }

        super.onDestroy();
    }
}
