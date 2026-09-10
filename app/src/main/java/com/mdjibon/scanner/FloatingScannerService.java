package com.mdjibon.scanner;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

public class FloatingScannerService extends Service {

    private static boolean running = false;

    private WindowManager wm;

    private ScannerView bubble;

    private WindowManager.LayoutParams bubbleParams;

    private TextView signalCard;

    private ScanOverlayView scanOverlay;

    private WindowManager.LayoutParams signalParams;

    private WindowManager.LayoutParams scanParams;

    private BroadcastReceiver receiver;

    private float downX;
    private float downY;
    private int startX;
    private int startY;
    private boolean moved;

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
    public void onCreate() {

        super.onCreate();

        running = true;

        wm = (WindowManager)
                getSystemService(
                        WINDOW_SERVICE
                );

        registerScannerReceiver();

        showBubble();
    }

    private void registerScannerReceiver() {

        receiver =
                new BroadcastReceiver() {

                    @Override
                    public void onReceive(
                            Context context,
                            Intent intent
                    ) {

                        String state =
                                intent.getStringExtra(
                                        "state"
                                );

                        if ("SCANNING".equals(state)) {

                            int progress =
                                    intent.getIntExtra(
                                            "progress",
                                            0
                                    );

                            showScanning(progress);

                        } else if ("RESULT".equals(state)) {

                            String signal =
                                    intent.getStringExtra(
                                            "signal"
                                    );

                            int confidence =
                                    intent.getIntExtra(
                                            "confidence",
                                            0
                                    );

                            hideScanning();

                            showSignal(
                                    signal,
                                    confidence
                            );

                        } else if ("ERROR".equals(state)) {

                            hideScanning();

                            String msg =
                                    intent.getStringExtra(
                                            "message"
                                    );

                            showSignal(
                                    "NO TRADE",
                                    0
                            );
                        }
                    }
                };

        IntentFilter f =
                new IntentFilter(
                        ScreenCaptureService.ACTION_UI
                );

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    receiver,
                    f,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    receiver,
                    f
            );
        }
    }

    private int overlayType() {

        if (Build.VERSION.SDK_INT >= 26) {

            return WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY;

        } else {

            return WindowManager.LayoutParams
                    .TYPE_PHONE;
        }
    }

    private void showBubble() {

        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        if (bubble != null) {
            return;
        }

        bubble = new ScannerView(this);

        bubbleParams =
                new WindowManager.LayoutParams(
                        56,
                        56,
                        overlayType(),
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        bubbleParams.gravity =
                Gravity.TOP | Gravity.START;

        bubbleParams.x = getSharedPreferences(
                "scanner",
                MODE_PRIVATE
        ).getInt("x", 20);

        bubbleParams.y = getSharedPreferences(
                "scanner",
                MODE_PRIVATE
        ).getInt("y", 300);

        bubble.setOnTouchListener(
                (v, event) -> {

                    switch (event.getActionMasked()) {

                        case MotionEvent.ACTION_DOWN:

                            downX = event.getRawX();
                            downY = event.getRawY();

                            startX =
                                    bubbleParams.x;

                            startY =
                                    bubbleParams.y;

                            moved = false;

                            return true;

                        case MotionEvent.ACTION_MOVE:

                            float dx =
                                    event.getRawX()
                                            - downX;

                            float dy =
                                    event.getRawY()
                                            - downY;

                            if (Math.abs(dx) > 8 ||
                                    Math.abs(dy) > 8) {

                                moved = true;
                            }

                            bubbleParams.x =
                                    startX + (int) dx;

                            bubbleParams.y =
                                    startY + (int) dy;

                            keepInsideScreen();

                            wm.updateViewLayout(
                                    bubble,
                                    bubbleParams
                            );

                            return true;

                        case MotionEvent.ACTION_UP:

                            if (!moved) {
                                startScan();
                            }

                            savePosition();

                            return true;
                    }

                    return true;
                }
        );

        wm.addView(
                bubble,
                bubbleParams
        );
    }

    private void keepInsideScreen() {

        int w =
                wm.getDefaultDisplay()
                        .getWidth();

        int h =
                wm.getDefaultDisplay()
                        .getHeight();

        bubbleParams.x =
                Math.max(
                        0,
                        Math.min(
                                bubbleParams.x,
                                w - 56
                        )
                );

        bubbleParams.y =
                Math.max(
                        0,
                        Math.min(
                                bubbleParams.y,
                                h - 56
                        )
                );
    }

    private void savePosition() {

        getSharedPreferences(
                "scanner",
                MODE_PRIVATE
        )
                .edit()
                .putInt("x", bubbleParams.x)
                .putInt("y", bubbleParams.y)
                .apply();
    }

    private void startScan() {

        if (!ScreenCaptureService.isCaptureActive()) {

            showSignal(
                    "CAPTURE OFF",
                    0
            );

            return;
        }

        Intent i =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        i.setAction("SCAN_NOW");

        startService(i);
    }

    private void showScanning(int progress) {

        if (bubble != null) {

            bubble.setVisibility(
                    View.GONE
            );
        }

        if (scanOverlay == null) {

            scanOverlay =
                    new ScanOverlayView(this);

            scanParams =
                    new WindowManager.LayoutParams(
                            -1,
                            -1,
                            overlayType(),
                            WindowManager.LayoutParams
                                    .FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams
                                    .FLAG_NOT_TOUCHABLE,
                            PixelFormat.TRANSLUCENT
                    );

            scanParams.gravity =
                    Gravity.TOP | Gravity.START;

            wm.addView(
                    scanOverlay,
                    scanParams
            );
        }

        scanOverlay.setProgress(
                progress
        );
    }

    private void hideScanning() {

        if (scanOverlay != null) {

            try {
                wm.removeView(scanOverlay);
            } catch (Exception ignored) {
            }

            scanOverlay = null;
        }

        if (bubble != null) {
            bubble.setVisibility(
                    View.VISIBLE
            );
        }
    }

    private void showSignal(
            String signal,
            int confidence
    ) {

        if (signalCard != null) {

            try {
                wm.removeView(signalCard);
            } catch (Exception ignored) {
            }

            signalCard = null;
        }

        signalCard =
                new TextView(this);

        signalCard.setGravity(
                Gravity.CENTER
        );

        signalCard.setTextSize(23);

        signalCard.setTypeface(
                android.graphics.Typeface.DEFAULT_BOLD
        );

        signalCard.setPadding(
                25,
                20,
                25,
                20
        );

        if ("UP".equals(signal)) {

            signalCard.setText(
                    "↑  UP\n" +
                    confidence +
                    "%"
            );

            signalCard.setTextColor(
                    Color.WHITE
            );

            signalCard.setBackground(
                    cardBackground(
                            Color.rgb(
                                    30,
                                    190,
                                    60
                            )
                    )
            );

        } else if ("DOWN".equals(signal)) {

            signalCard.setText(
                    "↓  DOWN\n" +
                    confidence +
                    "%"
            );

            signalCard.setTextColor(
                    Color.WHITE
            );

            signalCard.setBackground(
                    cardBackground(
                            Color.rgb(
                                    225,
                                    45,
                                    55
                            )
                    )
            );

        } else {

            signalCard.setText(
                    signal
            );

            signalCard.setTextColor(
                    Color.WHITE
            );

            signalCard.setBackground(
                    cardBackground(
                            Color.rgb(
                                    25,
                                    35,
                                    45
                            )
                    )
            );
        }

        signalParams =
                new WindowManager.LayoutParams(
                        210,
                        125,
                        overlayType(),
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        signalParams.gravity =
                Gravity.CENTER;

        signalCard.setOnClickListener(
                v -> {

                    try {
                        wm.removeView(
                                signalCard
                        );
                    } catch (Exception ignored) {
                    }

                    signalCard = null;

                    if (bubble != null) {
                        bubble.setVisibility(
                                View.VISIBLE
                        );
                    }
                }
        );

        wm.addView(
                signalCard,
                signalParams
        );

        if (bubble != null) {
            bubble.setVisibility(
                    View.GONE
            );
        }
    }

    private GradientDrawable cardBackground(
            int color
    ) {

        GradientDrawable d =
                new GradientDrawable();

        d.setColor(
                Color.argb(
                        235,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                )
        );

        d.setCornerRadius(28);

        d.setStroke(
                3,
                color
        );

        return d;
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (bubble == null) {
            showBubble();
        }

        return START_STICKY;
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
        }

        if (scanOverlay != null) {

            try {
                wm.removeView(
                        scanOverlay
                );
            } catch (Exception ignored) {
            }

            scanOverlay = null;
        }

        if (signalCard != null) {

            try {
                wm.removeView(
                        signalCard
                );
            } catch (Exception ignored) {
            }

            signalCard = null;
        }

        if (bubble != null) {

            try {
                wm.removeView(
                        bubble
                );
            } catch (Exception ignored) {
            }

            bubble = null;
        }

        super.onDestroy();
    }

    public static class ScannerView
            extends View {

        private final Paint p =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        public ScannerView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {

            super.onDraw(c);

            float w = getWidth();
            float h = getHeight();

            p.setStyle(Paint.Style.FILL);
            p.setColor(
                    Color.rgb(
                            0,
                            115,
                            255
                    )
            );

            c.drawCircle(
                    w / 2,
                    h / 2,
                    26,
                    p
            );

            p.setStyle(
                    Paint.Style.STROKE
            );

            p.setStrokeWidth(2);

            p.setColor(
                    Color.rgb(
                            80,
                            210,
                            255
                    )
            );

            c.drawCircle(
                    w / 2,
                    h / 2,
                    20,
                    p
            );

            c.drawCircle(
                    w / 2,
                    h / 2,
                    12,
                    p
            );

            p.setStyle(
                    Paint.Style.FILL
            );

            PathArrow.draw(
                    c,
                    p,
                    w / 2 - 2,
                    h / 2 + 9,
                    w / 2 + 17,
                    h / 2 - 12
            );
        }
    }

    private static class PathArrow {

        static void draw(
                Canvas c,
                Paint p,
                float x1,
                float y1,
                float x2,
                float y2
        ) {

            p.setColor(Color.WHITE);

            float dx = x2 - x1;
            float dy = y2 - y1;

            float len =
                    (float) Math.sqrt(
                            dx * dx + dy * dy
                    );

            float ux = dx / len;
            float uy = dy / len;

            float px = -uy;
            float py = ux;

            float bx =
                    x2 - ux * 10;

            float by =
                    y2 - uy * 10;

            android.graphics.Path path =
                    new android.graphics.Path();

            path.moveTo(
                    x1,
                    y1
            );

            path.lineTo(
                    bx + px * 6,
                    by + py * 6
            );

            path.lineTo(
                    bx + px * 12,
                    by + py * 12
            );

            path.lineTo(
                    x2,
                    y2
            );

            path.lineTo(
                    bx - px * 12,
                    by - py * 12
            );

            path.lineTo(
                    bx - px * 6,
                    by - py * 6
            );

            path.close();

            c.drawPath(
                    path,
                    p
            );
        }
    }

    public static class ScanOverlayView
            extends View {

        private final Paint p =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private float progress = 0;

        public ScanOverlayView(Context c) {
            super(c);
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

        @Override
        protected void onDraw(Canvas c) {

            super.onDraw(c);

            float w = getWidth();
            float h = getHeight();

            p.setStyle(
                    Paint.Style.FILL
            );

            p.setColor(
                    Color.argb(
                            18,
                            0,
                            130,
                            255
                    )
            );

            c.drawRect(
                    0,
                    0,
                    w,
                    h,
                    p
            );

            float y =
                    h * progress / 100f;

            p.setColor(
                    Color.argb(
                            75,
                            0,
                            150,
                            255
                    )
            );

            c.drawRect(
                    0,
                    0,
                    w,
                    y,
                    p
            );

            p.setColor(
                    Color.rgb(
                            40,
                            190,
                            255
                    )
            );

            p.setStrokeWidth(5);

            c.drawLine(
                    0,
                    y,
                    w,
                    y,
                    p
            );

            p.setStyle(
                    Paint.Style.STROKE
            );

            p.setStrokeWidth(3);

            p.setColor(
                    Color.argb(
                            130,
                            40,
                            180,
                            255
                    )
            );

            c.drawRect(
                    15,
                    15,
                    w - 15,
                    h - 15,
                    p
            );

            p.setStyle(
                    Paint.Style.FILL
            );

            p.setTextAlign(
                    Paint.Align.CENTER
            );

            p.setTextSize(25);

            p.setColor(Color.WHITE);

            c.drawText(
                    "SCANNING...",
                    w / 2,
                    h / 2 - 20,
                    p
            );

            p.setTextSize(21);

            c.drawText(
                    ((int) progress) +
                            " / 100",
                    w / 2,
                    h / 2 + 20,
                    p
            );
        }
    }
}
