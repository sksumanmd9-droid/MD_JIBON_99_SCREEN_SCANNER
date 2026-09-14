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
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class FloatingScannerService extends Service {

    // Kept for compatibility with MainActivity. The action now starts a finite
    // multi-scan session, not an endless scanner.
    public static final String ACTION_START_CONTINUOUS =
            "com.mdjibon.scanner.ACTION_START_CONTINUOUS";

    private static final int MAX_SCANS = 5;
    private static final int MIN_SCANS_FOR_DECISION = 3;
    private static final float STRONG_SCORE = 90.0f;
    private static final float VERY_STRONG_SCORE = 95.0f;
    private static final int MIN_DIRECTION_AGREEMENT = 3;
    private static final long SCAN_INTERVAL_MS = 1800L;
    private static final long OVERLAY_MS = 1250L;

    private static volatile boolean running = false;

    private WindowManager wm;
    private FrameLayout bubble;
    private TextView icon;
    private TextView badge;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private View scanOverlay;

    private boolean scanning = false;
    private boolean scanBusy = false;
    private int scanCount = 0;
    private int upCount = 0;
    private int downCount = 0;
    private float upScoreSum = 0f;
    private float downScoreSum = 0f;
    private float bestScore = 0f;
    private String bestSignal = "";
    private Runnable nextScan;

    public static boolean isRunning() {
        return running;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ScreenCaptureService.ACTION_SCAN_STATUS.equals(action)) {
                String state = intent.getStringExtra("state");
                if ("working".equals(state)) {
                    showScanOverlay();
                } else if ("done".equals(state)) {
                    hideScanOverlay();
                    scanBusy = false;
                }
                return;
            }

            if (ScreenCaptureService.ACTION_RESULT.equals(action)) {
                hideScanOverlay();
                scanBusy = false;
                handleScanResult(intent);
                return;
            }

            if (ScreenCaptureService.ACTION_ERROR.equals(action)) {
                scanBusy = false;
                hideScanOverlay();
                String message = intent.getStringExtra("message");
                if (message == null) message = "Scan failed.";
                finishScan(false, message);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        startForegroundCompat();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenCaptureService.ACTION_RESULT);
        filter.addAction(ScreenCaptureService.ACTION_SCAN_STATUS);
        filter.addAction(ScreenCaptureService.ACTION_ERROR);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        if (Settings.canDrawOverlays(this)) createBubble();
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (bubble == null && Settings.canDrawOverlays(this)) createBubble();

        if (intent != null && ACTION_START_CONTINUOUS.equals(intent.getAction())) {
            startScanSession();
        }
        return START_STICKY;
    }

    private void startScanSession() {
        if (!ScreenCaptureService.isCaptureActive()) {
            Toast.makeText(this, "Start screen capture first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (scanning) return;

        scanning = true;
        scanBusy = false;
        scanCount = 0;
        upCount = 0;
        downCount = 0;
        upScoreSum = 0f;
        downScoreSum = 0f;
        bestScore = 0f;
        bestSignal = "";

        setBadgeText("SCAN 1/5");
        requestOneScan();
    }

    private void requestOneScan() {
        if (!scanning || scanBusy || !ScreenCaptureService.isCaptureActive()) return;
        if (scanCount >= MAX_SCANS) {
            finishAfterMaxScans();
            return;
        }

        scanBusy = true;
        scanCount++;
        setBadgeText("SCAN " + scanCount + "/" + MAX_SCANS);

        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction(ScreenCaptureService.ACTION_SCAN);
        if (params != null) {
            intent.putExtra("excludeX", params.x);
            intent.putExtra("excludeY", params.y);
            intent.putExtra("excludeW", params.width);
            intent.putExtra("excludeH", params.height);
        }

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);

        showScanOverlay();
        handler.postDelayed(this::hideScanOverlay, OVERLAY_MS);
    }

    private void handleScanResult(Intent intent) {
        if (!scanning) return;

        String signal = intent.getStringExtra("signal");
        float score = intent.getFloatExtra("score", 0f);
        boolean strong = intent.getBooleanExtra("strong", false);

        if (!"UP".equals(signal) && !"DOWN".equals(signal)) {
            signal = "";
        }

        if (signal.isEmpty()) {
            scheduleNextScan();
            return;
        }

        if ("UP".equals(signal)) {
            upCount++;
            upScoreSum += score;
        } else {
            downCount++;
            downScoreSum += score;
        }

        if (score > bestScore) {
            bestScore = score;
            bestSignal = signal;
        }

        // A single strong frame is never enough. We require at least 3 scans
        // and at least 3 agreeing directions before displaying a final signal.
        int count = "UP".equals(signal) ? upCount : downCount;
        float average = "UP".equals(signal)
                ? upScoreSum / Math.max(1, upCount)
                : downScoreSum / Math.max(1, downCount);

        if (scanCount >= MIN_SCANS_FOR_DECISION && strong && score >= STRONG_SCORE
                && count >= MIN_DIRECTION_AGREEMENT && average >= STRONG_SCORE) {
            announceFinal("UP".equals(signal) ? upCount >= downCount ? "UP" : "DOWN"
                    : downCount >= upCount ? "DOWN" : "UP", average);
            return;
        }

        if (scanCount >= MAX_SCANS) {
            finishAfterMaxScans();
        } else {
            scheduleNextScan();
        }
    }

    private void scheduleNextScan() {
        if (!scanning) return;
        if (nextScan != null) handler.removeCallbacks(nextScan);
        nextScan = () -> {
            if (scanning) requestOneScan();
        };
        handler.postDelayed(nextScan, SCAN_INTERVAL_MS);
    }

    private void finishAfterMaxScans() {
        if (!scanning) return;

        String finalSignal = upCount >= downCount ? "UP" : "DOWN";
        int directionCount = "UP".equals(finalSignal) ? upCount : downCount;
        float average = "UP".equals(finalSignal)
                ? upScoreSum / Math.max(1, upCount)
                : downScoreSum / Math.max(1, downCount);

        // Do not manufacture a 90/95% number. If five scans do not contain
        // strong evidence, show NO STRONG SIGNAL and stop this session.
        if (directionCount >= MIN_DIRECTION_AGREEMENT && average >= STRONG_SCORE) {
            announceFinal(finalSignal, Math.min(97f, average));
        } else {
            scanning = false;
            scanBusy = false;
            if (nextScan != null) handler.removeCallbacks(nextScan);
            hideScanOverlay();
            setBadgeNoSignal();
            sendSessionStatus("done", "NO STRONG SIGNAL â€¢ 5 scans completed");
            Toast.makeText(this,
                    "NO STRONG SIGNAL â€¢ 5 scans completed",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void announceFinal(String signal, float score) {
        scanning = false;
        scanBusy = false;
        if (nextScan != null) handler.removeCallbacks(nextScan);
        hideScanOverlay();

        float shown = Math.max(90f, Math.min(97f, score));
        showBadge(signal, shown);

        String strength = shown >= VERY_STRONG_SCORE ? "VERY STRONG" : "STRONG";
        sendSessionStatus("done",
                signal + " " + String.format(Locale.US, "%.0f%%", shown)
                        + " â€¢ " + strength + " â€¢ SCAN STOPPED");
        Toast.makeText(this,
                signal + " " + String.format(Locale.US, "%.0f%%", shown)
                        + " â€¢ " + strength + " â€¢ SCAN STOPPED",
                Toast.LENGTH_LONG).show();
    }

    private void setBadgeNoSignal() {
        setBadgeText("NO SIGNAL");
    }

    private void sendSessionStatus(String state, String message) {
        Intent out = new Intent(ScreenCaptureService.ACTION_SCAN_STATUS);
        out.setPackage(getPackageName());
        out.putExtra("state", state);
        out.putExtra("message", message);
        sendBroadcast(out);
    }

    private void stopScanSession() {
        scanning = false;
        scanBusy = false;
        if (nextScan != null) handler.removeCallbacks(nextScan);
        nextScan = null;
        hideScanOverlay();
        setBadgeText("SCAN");
    }

    private void createBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return;

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble = new FrameLayout(this);

        icon = new TextView(this);
        icon.setText("MD");
        icon.setTextSize(16);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.rgb(70, 250, 165));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(Color.rgb(5, 20, 29));
        iconBg.setStroke(dp(2), Color.rgb(50, 235, 155));
        icon.setBackground(iconBg);

        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(60), dp(60));
        iconLp.leftMargin = 0;
        iconLp.topMargin = dp(2);
        bubble.addView(icon, iconLp);

        badge = new TextView(this);
        badge.setText("SCAN");
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(10);
        badge.setTextColor(Color.WHITE);
        setBadgeBackground(false, false);

        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(dp(72), dp(28));
        badgeLp.leftMargin = dp(62);
        badgeLp.topMargin = dp(18);
        bubble.addView(badge, badgeLp);

        params = new WindowManager.LayoutParams(
                dp(138), dp(66),
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        android.content.SharedPreferences pref =
                getSharedPreferences("scanner_settings", MODE_PRIVATE);
        params.x = pref.getInt("bubbleX", dp(15));
        params.y = pref.getInt("bubbleY", dp(280));

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            int startParamX, startParamY;
            long downTime;
            boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startParamX = params.x;
                        startParamY = params.y;
                        downTime = System.currentTimeMillis();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        params.x = Math.max(0, startParamX + (int) dx);
                        params.y = Math.max(0, startParamY + (int) dy);
                        getSharedPreferences("scanner_settings", MODE_PRIVATE).edit()
                                .putInt("bubbleX", params.x)
                                .putInt("bubbleY", params.y)
                                .apply();
                        try { wm.updateViewLayout(bubble, params); } catch (Exception ignored) { }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - downTime;
                        if (!moved && duration < 450) {
                            if (scanning) stopScanSession();
                            else startScanSession();
                        }
                        return true;
                }
                return true;
            }
        });

        try {
            wm.addView(bubble, params);
        } catch (Exception e) {
            bubble = null;
        }
    }

    private void showScanOverlay() {
        if (scanOverlay != null || wm == null || !Settings.canDrawOverlays(this)) return;

        scanOverlay = new ScanOverlay(this);
        WindowManager.LayoutParams overlayParams = new WindowManager.LayoutParams(
                -1, -1,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(scanOverlay, overlayParams);
        } catch (Exception e) {
            scanOverlay = null;
        }
    }

    private void hideScanOverlay() {
        if (scanOverlay != null) {
            try { wm.removeView(scanOverlay); } catch (Exception ignored) { }
            scanOverlay = null;
        }
    }

    private void showBadge(String signal, float score) {
        if (badge == null) return;
        badge.setText(signal + " " + String.format(Locale.US, "%.0f%%", score));
        setBadgeBackground(true, "UP".equals(signal));
    }

    private void setBadgeText(String text) {
        if (badge == null) return;
        badge.setText(text);
        setBadgeBackground(false, false);
    }

    private void setBadgeBackground(boolean result, boolean up) {
        if (badge == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (result) {
            bg.setColor(up ? Color.rgb(0, 120, 70) : Color.rgb(145, 25, 38));
            bg.setStroke(dp(1), up
                    ? Color.rgb(60, 240, 165)
                    : Color.rgb(255, 75, 90));
        } else {
            bg.setColor(Color.rgb(8, 20, 31));
            bg.setStroke(dp(1), Color.rgb(55, 170, 235));
        }
        badge.setBackground(bg);
    }

    private void startForegroundCompat() {
        String channelId = "md_jibon_floating";
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    channelId,
                    "MD JIBON Floating Scanner",
                    NotificationManager.IMPORTANCE_LOW
            ));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        startForeground(
                9903,
                builder.setContentTitle("MD JIBON Scanner")
                        .setContentText("Finite current-screen scanner active")
                        .setSmallIcon(android.R.drawable.ic_menu_search)
                        .build()
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        running = false;
        stopScanSession();
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        if (bubble != null && wm != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) { }
        }
        bubble = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class ScanOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler animationHandler = new Handler(Looper.getMainLooper());
        private float y = -40f;
        private final Runnable animation = new Runnable() {
            @Override
            public void run() {
                y += Math.max(10f, getResources().getDisplayMetrics().heightPixels / 42f);
                if (y > getHeight() + 40) y = -40f;
                invalidate();
                animationHandler.postDelayed(this, 28L);
            }
        };

        ScanOverlay(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            animationHandler.post(animation);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(48, 0, 110, 255));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(Color.argb(58, 40, 145, 255));
            canvas.drawRect(0, Math.max(0, y - 105), getWidth(), y, paint);
            paint.setColor(Color.argb(205, 55, 165, 255));
            canvas.drawRect(0, y - 5, getWidth(), y + 5, paint);
            paint.setColor(Color.argb(120, 100, 200, 255));
            canvas.drawRect(0, y - 1, getWidth(), y + 1, paint);
        }

        @Override
        protected void onDetachedFromWindow() {
            animationHandler.removeCallbacks(animation);
            super.onDetachedFromWindow();
        }
    }
}
