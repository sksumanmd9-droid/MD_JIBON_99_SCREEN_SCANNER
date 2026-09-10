package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public class FloatingScannerService extends Service {

    public static boolean isRunning = false;

    private static final String CHANNEL =
            "MDJIBON_FLOATING";

    private WindowManager wm;

    private FrameLayout bubble;

    private WindowManager.LayoutParams bubbleParams;

    private FrameLayout scanOverlay;

    private TextView scanText;

    private TextView signalBox;

    private BroadcastReceiver receiver;

    private float downX;
    private float downY;

    private int startX;
    private int startY;

    private boolean moved;

    private long downTime;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        isRunning = true;

        createNotificationChannel();

        Intent open =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pi =
                PendingIntent.getActivity(
                        this,
                        0,
                        open,
                        Build.VERSION.SDK_INT >= 23
                                ? PendingIntent.FLAG_IMMUTABLE
                                : 0
                );

        Notification n =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL
                )
                        .setContentTitle(
                                "MD JIBON Scanner"
                        )
                        .setContentText(
                                "Floating Scanner চলছে"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_search
                        )
                        .setOngoing(true)
                        .setContentIntent(pi)
                        .build();

        startForeground(
                7001,
                n
        );

        receiver =
                new BroadcastReceiver() {

                    @Override
                    public void onReceive(
                            Context c,
                            Intent i
                    ) {

                        String action =
                                i.getAction();

                        if (
                                ScreenCaptureService
                                        .ACTION_PROGRESS
                                        .equals(action)
                        ) {

                            int p =
                                    i.getIntExtra(
                                            "progress",
                                            0
                                    );

                            showScanning(p);

                        } else if (
                                ScreenCaptureService
                                        .ACTION_RESULT
                                        .equals(action)
                        ) {

                            hideScanning();

                            String signal =
                                    i.getStringExtra(
                                            "signal"
                                    );

                            int confidence =
                                    i.getIntExtra(
                                            "confidence",
                                            0
                                    );

                            showSignal(
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

        showBubble();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        return START_NOT_STICKY;
    }

    private void showBubble() {

        if (bubble != null)
            return;

        wm =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        bubble =
                new FrameLayout(this);

        TextView icon =
                new TextView(this);

        icon.setText("↗");
        icon.setTextSize(22);
        icon.setTypeface(
                Typeface.DEFAULT_BOLD
        );
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.WHITE);

        GradientCircle bg =
                new GradientCircle();

        icon.setBackground(bg);

        bubble.addView(
                icon,
                new FrameLayout.LayoutParams(
                        58,
                        58
                )
        );

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

        bubbleParams =
                new WindowManager.LayoutParams(
                        58,
                        58,
                        type,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        bubbleParams.gravity =
                Gravity.TOP | Gravity.LEFT;

        bubbleParams.x = 35;
        bubbleParams.y = 500;

        icon.setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(
                            View v,
                            MotionEvent event
                    ) {

                        switch (
                                event.getActionMasked()
                        ) {

                            case MotionEvent.ACTION_DOWN:

                                downX =
                                        event.getRawX();

                                downY =
                                        event.getRawY();

                                startX =
                                        bubbleParams.x;

                                startY =
                                        bubbleParams.y;

                                moved = false;

                                downTime =
                                        System.currentTimeMillis();

                                return true;

                            case MotionEvent.ACTION_MOVE:

                                float dx =
                                        event.getRawX()
                                                - downX;

                                float dy =
                                        event.getRawY()
                                                - downY;

                                if (
                                        Math.abs(dx) > 8 ||
                                        Math.abs(dy) > 8
                                ) {
                                    moved = true;
                                }

                                bubbleParams.x =
                                        startX +
                                                (int) dx;

                                bubbleParams.y =
                                        startY +
                                                (int) dy;

                                keepInsideScreen();

                                try {

                                    wm.updateViewLayout(
                                            bubble,
                                            bubbleParams
                                    );

                                } catch (Exception ignored) {
                                }

                                return true;

                            case MotionEvent.ACTION_UP:

                                long duration =
                                        System.currentTimeMillis()
                                                - downTime;

                                if (
                                        !moved &&
                                        duration < 600
                                ) {

                                    performScan();
                                }

                                return true;
                        }

                        return true;
                    }
                }
        );

        wm.addView(
                bubble,
                bubbleParams
        );
    }

    private void performScan() {

        hideSignal();

        showScanning(0);

        Intent scan =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        scan.setAction("SCAN_NOW");

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                startForegroundService(scan);

            } else {

                startService(scan);
            }

        } catch (Exception e) {

            hideScanning();

            showSignal(
                    "NO TRADE",
                    0
            );
        }
    }

    private void showScanning(
            int progress
    ) {

        if (scanOverlay == null) {

            scanOverlay =
                    new FrameLayout(this);

            scanOverlay.setBackgroundColor(
                    Color.argb(
                            35,
                            0,
                            110,
                            255
                    )
            );

            scanText =
                    new TextView(this);

            scanText.setTextColor(
                    Color.WHITE
            );

            scanText.setTextSize(20);

            scanText.setTypeface(
                    Typeface.DEFAULT_BOLD
            );

            scanText.setGravity(
                    Gravity.CENTER
            );

            scanText.setBackground(
                    new BluePanel()
            );

            FrameLayout.LayoutParams tp =
                    new FrameLayout.LayoutParams(
                            300,
                            145
                    );

            tp.gravity =
                    Gravity.CENTER;

            scanOverlay.addView(
                    scanText,
                    tp
            );

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

            WindowManager.LayoutParams sp =
                    new WindowManager.LayoutParams(
                            -1,
                            -1,
                            type,
                            WindowManager.LayoutParams
                                    .FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams
                                    .FLAG_NOT_TOUCHABLE,
                            PixelFormat.TRANSLUCENT
                    );

            sp.gravity =
                    Gravity.TOP | Gravity.LEFT;

            try {

                wm.addView(
                        scanOverlay,
                        sp
                );

            } catch (Exception ignored) {
            }
        }

        if (scanText != null) {

            scanText.setText(
                    "SCANNING...\n\n" +
                    progress +
                    " / 100"
            );
        }
    }

    private void hideScanning() {

        if (scanOverlay != null) {

            try {

                wm.removeView(
                        scanOverlay
                );

            } catch (Exception ignored) {
            }

            scanOverlay = null;
            scanText = null;
        }
    }

    private void showSignal(
            String signal,
            int confidence
    ) {

        hideSignal();

        if (
                signal == null ||
                "NO TRADE".equals(signal)
        ) {
            return;
        }

        signalBox =
                new TextView(this);

        signalBox.setGravity(
                Gravity.CENTER
        );

        signalBox.setTypeface(
                Typeface.DEFAULT_BOLD
        );

        signalBox.setTextSize(25);

        signalBox.setTextColor(
                Color.WHITE
        );

        if ("UP".equals(signal)) {

            signalBox.setText(
                    "↑  UP\n" +
                    confidence +
                    "%"
            );

            signalBox.setBackground(
                    new SignalPanel(
                            Color.rgb(
                                    20,
                                    155,
                                    30
                            )
                    )
            );

        } else {

            signalBox.setText(
                    "↓  DOWN\n" +
                    confidence +
                    "%"
            );

            signalBox.setBackground(
                    new SignalPanel(
                            Color.rgb(
                                    210,
                                    30,
                                    35
                            )
                    )
            );
        }

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

        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        230,
                        155,
                        type,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        p.gravity =
                Gravity.TOP | Gravity.LEFT;

        p.x =
                bubbleParams != null
                        ? bubbleParams.x
                        : 35;

        p.y =
                bubbleParams != null
                        ? bubbleParams.y + 75
                        : 575;

        try {

            wm.addView(
                    signalBox,
                    p
            );

        } catch (Exception ignored) {
        }
    }

    private void hideSignal() {

        if (signalBox != null) {

            try {

                wm.removeView(
                        signalBox
                );

            } catch (Exception ignored) {
            }

            signalBox = null;
        }
    }

    private void keepInsideScreen() {

        if (wm == null)
            return;

        android.util.DisplayMetrics dm =
                getResources()
                        .getDisplayMetrics();

        int maxX =
                dm.widthPixels - 58;

        int maxY =
                dm.heightPixels - 58;

        if (bubbleParams.x < 0)
            bubbleParams.x = 0;

        if (bubbleParams.y < 0)
            bubbleParams.y = 0;

        if (bubbleParams.x > maxX)
            bubbleParams.x = maxX;

        if (bubbleParams.y > maxY)
            bubbleParams.y = maxY;
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel c =
                    new NotificationChannel(
                            CHANNEL,
                            "Floating Scanner",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            NotificationManager nm =
                    getSystemService(
                            NotificationManager.class
                    );

            nm.createNotificationChannel(c);
        }
    }

    @Override
    public void onDestroy() {

        isRunning = false;

        if (receiver != null) {

            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        }

        hideScanning();
        hideSignal();

        if (bubble != null) {

            try {
                wm.removeView(bubble);
            } catch (Exception ignored) {
            }

            bubble = null;
        }

        super.onDestroy();
    }

    private class GradientCircle
            extends android.graphics.drawable.GradientDrawable {

        GradientCircle() {

            setShape(
                    android.graphics.drawable
                            .GradientDrawable.OVAL
            );

            setColor(
                    Color.rgb(
                            0,
                            105,
                            235
                    )
            );

            setStroke(
                    3,
                    Color.rgb(
                            70,
                            200,
                            255
                    )
            );
        }
    }

    private class BluePanel
            extends android.graphics.drawable.GradientDrawable {

        BluePanel() {

            setShape(
                    android.graphics.drawable
                            .GradientDrawable.RECTANGLE
            );

            setColor(
                    Color.argb(
                            225,
                            5,
                            30,
                            70
                    )
            );

            setStroke(
                    4,
                    Color.rgb(
                            0,
                            150,
                            255
                    )
            );

            setCornerRadius(30);
        }
    }

    private class SignalPanel
            extends android.graphics.drawable.GradientDrawable {

        SignalPanel(int color) {

            setShape(
                    android.graphics.drawable
                            .GradientDrawable.RECTANGLE
            );

            setColor(
                    Color.argb(
                            230,
                            Color.red(color),
                            Color.green(color),
                            Color.blue(color)
                    )
            );

            setStroke(
                    4,
                    Color.WHITE
            );

            setCornerRadius(30);
        }
    }
}
