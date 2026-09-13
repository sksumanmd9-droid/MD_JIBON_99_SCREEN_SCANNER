package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {

    public static final String ACTION_RESULT =
            "com.mdjibon.scanner.ACTION_RESULT";
    public static final String ACTION_PROGRESS =
            "com.mdjibon.scanner.ACTION_PROGRESS";
    public static final String ACTION_CAPTURE_STATE =
            "com.mdjibon.scanner.ACTION_CAPTURE_STATE";
    public static final String ACTION_SCAN =
            "com.mdjibon.scanner.ACTION_SCAN";
    public static final String ACTION_STOP =
            "com.mdjibon.scanner.ACTION_STOP";
    public static final String ACTION_ERROR =
            "com.mdjibon.scanner.ACTION_ERROR";

    private static final String CHANNEL_ID = "md_jibon_scanner";
    private static final int NOTIFICATION_ID = 1001;

    private static volatile boolean captureActive = false;
    private static volatile boolean scanning = false;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Bitmap latestFrame;

    private final Object frameLock = new Object();

    private Handler mainHandler;
    private ExecutorService executor;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private long lastFrameTime = 0L;

    private final MediaProjection.Callback projectionCallback =
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopCaptureInternal();
                }
            };

    public static boolean isCaptureActive() {
        return captureActive;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        // ✅ Android 14+ এর জন্য সবচেয়ে গুরুত্বপূর্ণ:
        // onStartCommand-এর একদম শুরুতে startForeground() কল করতে হবে।
        try {
            Notification notification = createNotification();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {
        }

        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopCaptureInternal();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SCAN.equals(action)) {
            requestScan();
            return START_STICKY;
        }

        if (intent.hasExtra("data")) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = getParcelableIntent(intent, "data");
            if (data != null && resultCode != -1) {
                startCapture(resultCode, data);
            }
        }

        return START_STICKY;
    }

    private Intent getParcelableIntent(Intent source, String key) {
        if (Build.VERSION.SDK_INT >= 33) {
            return source.getParcelableExtra(key, Intent.class);
        } else {
            return source.getParcelableExtra(key);
        }
    }

    // ============================================================
    // START CAPTURE — Android 14+ কঠোর ফিক্স
    // ============================================================

    private void startCapture(int resultCode, Intent data) {

        if (captureActive) {
            sendState(true);
            return;
        }

        try {

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(MEDIA_PROJECTION_SERVICE);

            if (manager == null) {
                captureActive = false;
                sendState(false);
                sendError("MEDIA PROJECTION MANAGER UNAVAILABLE");
                return;
            }

            mediaProjection = manager.getMediaProjection(resultCode, data);

            if (mediaProjection == null) {
                captureActive = false;
                sendState(false);
                sendError("MEDIA PROJECTION UNAVAILABLE");
                return;
            }

            mediaProjection.registerCallback(projectionCallback, mainHandler);

            android.util.DisplayMetrics metrics =
                    getResources().getDisplayMetrics();

            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;

            if (screenWidth <= 0 || screenHeight <= 0) {
                captureActive = false;
                sendState(false);
                sendError("INVALID SCREEN SIZE");
                stopCaptureObjects();
                return;
            }

            // ✅ সবচেয়ে গুরুত্বপূর্ণ ধাপ:
            // ImageReader তৈরি করে সাথে সাথে VirtualDisplay তৈরি করা
            imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    3
            );

            imageReader.setOnImageAvailableListener(
                    reader -> copyLatestImage(reader),
                    mainHandler
            );

            // ✅ Android 14+ এ createVirtualDisplay() কল করার আগে
            // অবশ্যই এটা একটা ট্রাই-ক্যাচে রাখতে হবে
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "MD JIBON Scanner",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    mainHandler
            );

            if (virtualDisplay == null) {
                captureActive = false;
                sendState(false);
                sendError("VIRTUAL DISPLAY FAILED");
                stopCaptureObjects();
                return;
            }

            captureActive = true;
            sendState(true);

        } catch (SecurityException e) {
            captureActive = false;
            sendState(false);
            sendError("SCREEN CAPTURE SECURITY ERROR");
            stopCaptureObjects();

        } catch (Exception e) {
            captureActive = false;
            sendState(false);
            sendError("SCREEN CAPTURE START FAILED");
            stopCaptureObjects();
        }
    }

    // ============================================================
    // COPY LATEST IMAGE
    // ============================================================

    private void copyLatestImage(ImageReader reader) {

        long now = System.currentTimeMillis();

        if (now - lastFrameTime < 80) {
            Image old = null;
            try {
                old = reader.acquireLatestImage();
            } catch (Exception ignored) {
            }
            if (old != null) {
                old.close();
            }
            return;
        }

        lastFrameTime = now;

        Image image = null;

        try {
            image = reader.acquireLatestImage();
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) return;

            ByteBuffer buffer = planes[0].getBuffer();

            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();

            int rowPadding = rowStride - pixelStride * screenWidth;
            int bitmapWidth = screenWidth +
                    rowPadding / Math.max(1, pixelStride);

            Bitmap raw = Bitmap.createBitmap(
                    bitmapWidth,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );

            raw.copyPixelsFromBuffer(buffer);

            Bitmap cropped;

            if (bitmapWidth != screenWidth) {
                cropped = Bitmap.createBitmap(
                        raw, 0, 0, screenWidth, screenHeight
                );
                raw.recycle();
            } else {
                cropped = raw;
            }

            synchronized (frameLock) {
                if (latestFrame != null && !latestFrame.isRecycled()) {
                    latestFrame.recycle();
                }
                latestFrame = cropped;
            }

        } catch (Exception ignored) {

        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private Bitmap getFrameCopy() {
        synchronized (frameLock) {
            if (latestFrame == null || latestFrame.isRecycled()) {
                return null;
            }
            try {
                return latestFrame.copy(Bitmap.Config.ARGB_8888, false);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ============================================================
    // REQUEST SCAN
    // ============================================================

    private void requestScan() {

        if (!captureActive) {
            sendResult("NO TRADE", 0, 0, "UNKNOWN");
            return;
        }

        if (scanning) return;

        scanning = true;

        sendProgress(0);

        executor.execute(() -> {

            Bitmap frame = null;

            for (int i = 0; i < 10; i++) {
                frame = getFrameCopy();
                if (frame != null) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }

            if (frame == null) {
                scanning = false;
                mainHandler.post(() -> sendProgress(200));
                mainHandler.postDelayed(() -> {
                    sendResult("NO TRADE", 0, 0, getTimeframe());
                    scanning = false;
                }, 100);
                return;
            }

            Analyzer.Result result;

            try {
                result = Analyzer.analyze(frame);
            } catch (Exception e) {
                result = new Analyzer.Result();
                result.signal = "NO TRADE";
                result.confidence = 0.0;
                result.evaluatedRules = 0;
                result.detectedCandles = 0;
            }

            if (!frame.isRecycled()) {
                frame.recycle();
            }

            final Analyzer.Result finalResult = result;

            final int[] steps = {
                    20, 50, 80, 110, 140, 165, 185, 195, 200
            };

            for (int i = 0; i < steps.length; i++) {
                final int value = steps[i];
                mainHandler.postDelayed(
                        () -> sendProgress(value),
                        i * 55L
                );
            }

            mainHandler.postDelayed(() -> {
                finishScan(finalResult);
            }, steps.length * 55L + 50L);
        });
    }

    private void finishScan(Analyzer.Result result) {

        scanning = false;

        if (result == null) {
            sendResult("NO TRADE", 0, 0, getTimeframe());
            return;
        }

        String signal = result.signal;

        if (!"UP".equals(signal) &&
                !"DOWN".equals(signal) &&
                !"NO TRADE".equals(signal)) {
            signal = "NO TRADE";
        }

        int rules = result.evaluatedRules;

        if (rules != 200) {
            signal = "NO TRADE";
        }

        if (result.detectedCandles < 50) {
            signal = "NO TRADE";
        }

        int confidenceInt = (int) Math.round(result.confidence);

        sendResult(signal, confidenceInt, rules, getTimeframe());
    }

    private void sendProgress(int progress) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra("progress", Math.max(0, Math.min(200, progress)));
        sendBroadcast(intent);
    }

    private void sendState(boolean active) {
        Intent intent = new Intent(ACTION_CAPTURE_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("active", active);
        sendBroadcast(intent);
    }

    private void sendError(String message) {
        Intent intent = new Intent(ACTION_ERROR);
        intent.setPackage(getPackageName());
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    private void sendResult(String signal, int confidence,
                            int ruleCount, String timeframe) {
        Intent intent = new Intent(ACTION_RESULT);
        intent.setPackage(getPackageName());
        intent.putExtra("signal", signal);
        intent.putExtra("confidence", confidence);
        intent.putExtra("ruleCount", ruleCount);
        intent.putExtra("timeframe", timeframe);
        sendBroadcast(intent);
    }

    private String getTimeframe() {
        SharedPreferences prefs =
                getSharedPreferences("scanner_settings", MODE_PRIVATE);
        return prefs.getString("timeframe", "1 MIN");
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MD JIBON Scanner")
                .setContentText("Screen capture is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MD JIBON Scanner",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager =
                    (NotificationManager) getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void stopCaptureObjects() {
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (mediaProjection != null) {
                try {
                    mediaProjection.unregisterCallback(projectionCallback);
                } catch (Exception ignored) {
                }
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Exception ignored) {
        }

        synchronized (frameLock) {
            if (latestFrame != null && !latestFrame.isRecycled()) {
                latestFrame.recycle();
            }
            latestFrame = null;
        }
    }

    private void stopCaptureInternal() {
        captureActive = false;
        scanning = false;
        stopCaptureObjects();
        sendState(false);
    }

    @Override
    public void onDestroy() {
        stopCaptureInternal();
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
