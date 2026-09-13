package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class FloatingScannerService extends Service {

    private static volatile boolean running = false;

    private WindowManager windowManager;

    private View logoView;
    private View badgeView;        // UP/DOWN ছোট ব্যাজ
    private ScanAnimationView scanView;
    private View resultView;

    private BroadcastReceiver receiver;

    private int savedX = 20;
    private int savedY = 250;

    private boolean scanRunning = false;

    private final Handler mainHandler = new Handler();

    private static final int NOTIFICATION_ID = 2001;

    private static final String CHANNEL_ID =
            "md_jibon_floating";

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
                        getSystemService(WINDOW_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {

            createNotificationChannel();

            Notification notification = createNotification();

            if (Build.VERSION.SDK_INT >= 34) {

                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );

            } else {

                startForeground(NOTIFICATION_ID, notification);
            }
        }

        registerScannerReceiver();

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

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < 26) return;

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "MD JIBON Floating Scanner",
                        NotificationManager.IMPORTANCE_LOW
                );

        channel.setDescription("MD JIBON floating scanner");

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {

        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("MD JIBON Scanner")
                    .setContentText("Floating scanner is active")
                    .setSmallIcon(android.R.drawable.ic_menu_search)
                    .setOngoing(true)
                    .build();
        }

        return new Notification.Builder(this)
                .setContentTitle("MD JIBON Scanner")
                .setContentText("Floating scanner is active")
                .setSmallIcon(android.R.drawable.ic_menu_search)
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

            String action = intent.getAction();

            if (ScreenCaptureService.ACTION_STOP.equals(action)) {
                stopSelf();
            }
        }

        return START_STICKY;
    }

    // ============================================================
    // RECEIVER
    // ============================================================

    private void registerScannerReceiver() {

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent == null) return;

                String action = intent.getAction();

                // ----------------------------------------
                // SCAN PROGRESS
                // ----------------------------------------

                if (ScreenCaptureService.ACTION_PROGRESS.equals(action)) {

                    int progress = intent.getIntExtra("progress", 0);

                    if (scanView != null) {
                        scanView.setProgress(progress);
                    }

                    return;
                }

                // ----------------------------------------
                // SCAN RESULT
                // ----------------------------------------

                if (ScreenCaptureService.ACTION_RESULT.equals(action)) {

                    String signal = intent.getStringExtra("signal");
                    int confidence = intent.getIntExtra("confidence", 0);

                    showResultBadge(signal, confidence);
                }
            }
        };

        IntentFilter filter = new IntentFilter();

        filter.addAction(ScreenCaptureService.ACTION_PROGRESS);
        filter.addAction(ScreenCaptureService.ACTION_RESULT);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    // ============================================================
    // DP
    // ============================================================

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ============================================================
    // OVERLAY PARAMETERS
    // ============================================================

    private WindowManager.LayoutParams overlayParams() {

        int type;

        if (Build.VERSION.SDK_INT >= 26) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = savedX;
        params.y = savedY;

        return params;
    }

    // ============================================================
    // SMALL FLOATING ICON (ট্যাপ ইভেন্ট ফিক্স করা)
    // ============================================================

    private void createLogo() {

        if (windowManager == null) return;
        if (logoView != null) return;
        if (scanRunning) return;

        ImageView image = new ImageView(this);

        int drawableId = getResources().getIdentifier(
                "md_jibon_logo", "drawable", getPackageName()
        );

        if (drawableId != 0) {
            image.setImageResource(drawableId);
        } else {
            image.setImageResource(android.R.drawable.ic_menu_search);
        }

        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int size = dp(52);
        image.setLayoutParams(new android.view.ViewGroup.LayoutParams(size, size));

        // ✅ গুরুত্বপূর্ণ ফিক্স — ট্যাপ ইভেন্ট কাজ করার জন্য
        image.setClickable(true);
        image.setFocusable(true);
        image.setFocusableInTouchMode(true);

        logoView = image;

        final WindowManager.LayoutParams params = overlayParams();

        image.setOnTouchListener(new View.OnTouchListener() {

            private int downX;
            private int downY;
            private int startX;
            private int startY;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:

                        downX = (int) event.getRawX();
                        downY = (int) event.getRawY();

                        startX = params.x;
                        startY = params.y;

                        downTime = System.currentTimeMillis();

                        return true;

                    case MotionEvent.ACTION_MOVE:

                        int dx = (int) event.getRawX() - downX;
                        int dy = (int) event.getRawY() - downY;

                        params.x = startX + dx;
                        params.y = startY + dy;

                        try {
                            windowManager.updateViewLayout(image, params);
                        } catch (Exception ignored) {
                        }

                        return true;

                    case MotionEvent.ACTION_UP:

                        savedX = params.x;
                        savedY = params.y;

                        long duration = System.currentTimeMillis() - downTime;

                        int totalMove =
                                Math.abs((int) event.getRawX() - downX)
                                        + Math.abs((int) event.getRawY() - downY);

                        // ট্যাপ হলে স্ক্যান, ড্র্যাগ হলে না
                        if (duration < 400 && totalMove < dp(15)) {
                            startScan();
                        }

                        return true;
                }

                return true;
            }
        });

        try {
            windowManager.addView(image, params);
        } catch (Exception e) {
            logoView = null;
        }
    }

    // ============================================================
    // START SCAN
    // ============================================================

    private void startScan() {

        if (scanRunning) return;

        if (!ScreenCaptureService.isCaptureActive()) {

            Toast.makeText(
                    this,
                    "আগে SCREEN CAPTURE ON করুন",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        scanRunning = true;

        // পুরনো badge সরান
        removeBadge();

        // Floating icon সরান
        removeView(logoView);
        logoView = null;

        // নীল স্ক্যান অ্যানিমেশন তৈরি করুন
        createScanView();

        // ScreenCaptureService-এ scan request পাঠান
        Intent scan = new Intent(this, ScreenCaptureService.class);
        scan.setAction(ScreenCaptureService.ACTION_SCAN);

        try {

            // ✅ ফিক্স — startForegroundService ব্যবহার
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(scan);
            } else {
                startService(scan);
            }

        } catch (Exception e) {

            scanRunning = false;

            if (scanView != null) {
                scanView.stopAnimation();
                removeView(scanView);
                scanView = null;
            }

            createLogo();

            Toast.makeText(
                    this,
                    "Scanner start হয়নি",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ============================================================
    // SCAN ANIMATION (নীল আবরণ উপরে থেকে নিচে)
    // ============================================================

    private void createScanView() {

        if (windowManager == null) return;
        if (scanView != null) return;

        scanView = new ScanAnimationView(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();

        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;

        if (Build.VERSION.SDK_INT >= 26) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params.gravity = Gravity.TOP | Gravity.START;

        params.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        params.format = PixelFormat.TRANSLUCENT;

        try {
            windowManager.addView(scanView, params);
        } catch (Exception e) {
            scanView = null;
            scanRunning = false;
            createLogo();
        }
    }

    // ============================================================
    // SCAN ANIMATION VIEW — নীল ব্যান্ড + 200/200 প্রোগ্রেস
    // ============================================================

    private class ScanAnimationView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float scanY = -dp(140);
        private int progress = 0;
        private boolean animationRunning = true;

        private final Runnable animationRunnable = new Runnable() {

            @Override
            public void run() {

                if (!animationRunning) return;

                scanY += dp(10);

                if (scanY > getHeight()) {
                    scanY = -dp(140);
                }

                invalidate();
                postDelayed(this, 20);
            }
        };

        ScanAnimationView(Context context) {

            super(context);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            glowPaint.setColor(Color.argb(35, 30, 190, 255));
            edgePaint.setColor(Color.argb(200, 60, 220, 255));
            edgePaint.setStrokeWidth(dp(2));

            progressPaint.setColor(Color.argb(220, 40, 210, 255));
            progressBgPaint.setColor(Color.argb(80, 30, 80, 120));

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(13));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);

            post(animationRunnable);
        }

        void setProgress(int value) {

            progress = Math.max(0, Math.min(200, value));
            invalidate();
        }

        void stopAnimation() {

            animationRunning = false;
            removeCallbacks(animationRunnable);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();

            // ==========================================
            // নীল স্ক্যানিং ব্যান্ড (আপনার ছবির মতো)
            // ==========================================

            float bandHeight = dp(140);
            float top = scanY;
            float bottom = scanY + bandHeight;

            canvas.drawRect(0, top, width, bottom, glowPaint);

            // মধ্যের উজ্জ্বল স্তর
            Paint middle = new Paint(Paint.ANTI_ALIAS_FLAG);
            middle.setColor(Color.argb(75, 40, 200, 255));
            canvas.drawRect(0, top + dp(45), width, top + dp(95), middle);

            // উজ্জ্বল প্রান্ত
            float edgeY = scanY + dp(70);
            canvas.drawLine(0, edgeY, width, edgeY, edgePaint);

            // ==========================================
            // নিচে প্রোগ্রেস বার (200/200)
            // ==========================================

            float barHeight = dp(4);
            float barY = height - dp(8);

            canvas.drawRect(0, barY, width, barY + barHeight, progressBgPaint);

            float progressWidth = width * (progress / 200f);
            canvas.drawRect(0, barY, progressWidth, barY + barHeight, progressPaint);

            // ==========================================
            // মাঝখানে "SCANNING... 200/200" টেক্সট
            // ==========================================

            if (progress > 0) {

                String text = "SCANNING... " + progress + " / 200";

                float textY = height / 2f;

                // ব্যাকগ্রাউন্ড প্যানেল
                Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
                panel.setColor(Color.argb(200, 5, 15, 30));

                float textWidth = textPaint.measureText(text);
                float panelW = textWidth + dp(40);
                float panelH = dp(46);

                float panelLeft = (width - panelW) / 2f;
                float panelTop = textY - panelH / 2f;

                canvas.drawRoundRect(
                        panelLeft,
                        panelTop,
                        panelLeft + panelW,
                        panelTop + panelH,
                        dp(10),
                        dp(10),
                        panel
                );

                // নীল বর্ডার
                Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
                border.setStyle(Paint.Style.STROKE);
                border.setStrokeWidth(dp(1.5f));
                border.setColor(Color.argb(200, 40, 210, 255));

                canvas.drawRoundRect(
                        panelLeft,
                        panelTop,
                        panelLeft + panelW,
                        panelTop + panelH,
                        dp(10),
                        dp(10),
                        border
                );

                canvas.drawText(text, width / 2f, textY + dp(5), textPaint);
            }
        }
    }

    // ============================================================
    // SHOW UP/DOWN BADGE (Icon-এর পাশে ছোট ব্যাজ)
    // ============================================================

    private void showResultBadge(String signal, int confidence) {

        scanRunning = false;

        // স্ক্যান অ্যানিমেশন বন্ধ করুন
        if (scanView != null) {
            scanView.stopAnimation();
            removeView(scanView);
            scanView = null;
        }

        // পুরনো ব্যাজ সরান
        removeBadge();

        if (signal == null) signal = "NO TRADE";

        if (!"UP".equals(signal) && !"DOWN".equals(signal)) {
            signal = "NO TRADE";
        }

        confidence = Math.max(0, Math.min(100, confidence));

        // ====================================================
        // ছোট ব্যাজ কন্টেইনার
        // ====================================================

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(6), dp(3), dp(8), dp(3));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(245, 5, 12, 22));
        bg.setCornerRadius(dp(12));

        int borderColor;

        if ("UP".equals(signal)) {
            borderColor = Color.rgb(20, 235, 120);
        } else if ("DOWN".equals(signal)) {
            borderColor = Color.rgb(255, 55, 65);
        } else {
            borderColor = Color.rgb(120, 150, 175);
        }

        bg.setStroke(dp(1), borderColor);
        container.setBackground(bg);

        // ====================================================
        // ছোট আইকন (নীল লোগো)
        // ====================================================

        ImageView icon = new ImageView(this);

        int drawableId = getResources().getIdentifier(
                "md_jibon_logo", "drawable", getPackageName()
        );

        if (drawableId != 0) {
            icon.setImageResource(drawableId);
        } else {
            icon.setImageResource(android.R.drawable.ic_menu_search);
        }

        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams.rightMargin = dp(5);

        container.addView(icon, iconParams);

        // ====================================================
        // UP/DOWN টেক্সট
        // ====================================================

        TextView tv = new TextView(this);

        String text;
        int textColor;

        if ("UP".equals(signal)) {
            text = "↑ UP  " + confidence + "%";
            textColor = Color.rgb(25, 240, 125);
        } else if ("DOWN".equals(signal)) {
            text = "↓ DOWN  " + confidence + "%";
            textColor = Color.rgb(255, 60, 70);
        } else {
            text = "WAIT";
            textColor = Color.WHITE;
        }

        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        container.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
        ));

        badgeView = container;

        // ====================================================
        // ব্যাজ পজিশন (Icon-এর ঠিক ডান পাশে)
        // ====================================================

        WindowManager.LayoutParams params = overlayParams();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = dp(44);
        params.x = Math.max(0, savedX + dp(56));  // আইকনের ডান পাশে
        params.y = Math.max(0, savedY + dp(4));

        try {
            windowManager.addView(container, params);
        } catch (Exception e) {
            badgeView = null;
            createLogo();
            return;
        }

        // ====================================================
        // সিগন্যাল ৫ সেকেন্ড দেখিয়ে আবার আইকন ফিরিয়ে আনুন
        // ====================================================

        final View currentBadge = badgeView;

        mainHandler.postDelayed(() -> {

            removeView(currentBadge);

            if (badgeView == currentBadge) {
                badgeView = null;
            }

            // আবার ছোট floating icon
            createLogo();

        }, 5000);  // ৫ সেকেন্ড
    }

    // ============================================================
    // REMOVE BADGE
    // ============================================================

    private void removeBadge() {

        if (badgeView != null) {
            removeView(badgeView);
            badgeView = null;
        }
    }

    // ============================================================
    // REMOVE VIEW
    // ============================================================

    private void removeView(View view) {

        if (view == null || windowManager == null) return;

        try {
            windowManager.removeView(view);
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

        mainHandler.removeCallbacksAndMessages(null);

        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiver = null;
        }

        removeView(logoView);
        removeView(badgeView);
        removeView(scanView);
        removeView(resultView);

        logoView = null;
        badgeView = null;
        scanView = null;
        resultView = null;

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
