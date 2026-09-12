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

    // ============================================================
    // ACTIONS
    // ============================================================

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

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private static final String CHANNEL_ID =
            "md_jibon_scanner";

    private static final int NOTIFICATION_ID =
            1001;

    // ============================================================
    // STATE
    // ============================================================

    private static volatile boolean captureActive =
            false;

    private static volatile boolean scanning =
            false;

    // ============================================================
    // MEDIA PROJECTION
    // ============================================================

    private MediaProjection mediaProjection;

    private VirtualDisplay virtualDisplay;

    private ImageReader imageReader;

    private Bitmap latestFrame;

    private final Object frameLock =
            new Object();

    // ============================================================
    // THREADS
    // ============================================================

    private Handler mainHandler;

    private ExecutorService executor;

    // ============================================================
    // SCREEN SIZE
    // ============================================================

    private int screenWidth;

    private int screenHeight;

    private int screenDensity;

    private long lastFrameTime =
            0L;

    // ============================================================
    // CALLBACK
    // ============================================================

    private final MediaProjection.Callback
            projectionCallback =
            new MediaProjection.Callback() {

                @Override
                public void onStop() {

                    stopCaptureInternal();
                }
            };

    // ============================================================
    // PUBLIC STATE
    // ============================================================

    public static boolean isCaptureActive() {

        return captureActive;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Override
    public void onCreate() {

        super.onCreate();

        mainHandler =
                new Handler(
                        Looper.getMainLooper()
                );

        executor =
                Executors.newSingleThreadExecutor();

        createNotificationChannel();
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

        /*
         * Android service restart করলে পুরনো MediaProjection
         * permission data পাওয়া যাবে না।
         *
         * তাই null intent এ শুধু service alive রাখা হবে।
         */
        if (intent == null) {

            return START_NOT_STICKY;
        }

        String action =
                intent.getAction();

        // ========================================================
        // STOP
        // ========================================================

        if (ACTION_STOP.equals(action)) {

            stopCaptureInternal();

            stopSelf();

            return START_NOT_STICKY;
        }

        // ========================================================
        // SCAN
        // ========================================================

        if (ACTION_SCAN.equals(action)) {

            requestScan();

            return START_STICKY;
        }

        // ========================================================
        // START CAPTURE
        // ========================================================

        if (intent.hasExtra("data")) {

            int resultCode =
                    intent.getIntExtra(
                            "resultCode",
                            -1
                    );

            Intent data =
                    getParcelableIntent(
                            intent,
                            "data"
                    );

            if (data != null &&
                    resultCode != -1) {

                startCapture(
                        resultCode,
                        data
                );
            }
        }

        return START_STICKY;
    }

    // ============================================================
    // SAFE PARCELABLE INTENT
    // ============================================================

    private Intent getParcelableIntent(
            Intent source,
            String key
    ) {

        if (Build.VERSION.SDK_INT >= 33) {

            return source.getParcelableExtra(
                    key,
                    Intent.class
            );

        } else {

            return source.getParcelableExtra(
                    key
            );
        }
    }

    // ============================================================
    // START CAPTURE
    // ============================================================

    private void startCapture(
            int resultCode,
            Intent data
    ) {

        /*
         * Already active হলে নতুন MediaProjection
         * তৈরি করা হবে না।
         */
        if (captureActive) {

            sendState(true);

            return;
        }

        try {

            // ====================================================
            // IMPORTANT ANDROID 14+ FIX
            // ====================================================

            Notification notification =
                    createNotification();

            if (Build.VERSION.SDK_INT >= 29) {

                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );

            } else {

                startForeground(
                        NOTIFICATION_ID,
                        notification
                );
            }

            // ====================================================
            // MEDIA PROJECTION MANAGER
            // ====================================================

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    MEDIA_PROJECTION_SERVICE
                            );

            if (manager == null) {

                captureActive = false;

                sendState(false);

                return;
            }

            // ====================================================
            // GET MEDIA PROJECTION
            // ====================================================

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            data
                    );

            if (mediaProjection == null) {

                captureActive = false;

                sendState(false);

                return;
            }

            // ====================================================
            // REGISTER CALLBACK
            // ====================================================

            mediaProjection.registerCallback(
                    projectionCallback,
                    mainHandler
            );

            // ====================================================
            // DISPLAY METRICS
            // ====================================================

            android.util.DisplayMetrics metrics =
                    getResources()
                            .getDisplayMetrics();

            screenWidth =
                    metrics.widthPixels;

            screenHeight =
                    metrics.heightPixels;

            screenDensity =
                    metrics.densityDpi;

            if (screenWidth <= 0 ||
                    screenHeight <= 0) {

                captureActive = false;

                sendState(false);

                return;
            }

            // ====================================================
            // IMAGE READER
            // ====================================================

            imageReader =
                    ImageReader.newInstance(
                            screenWidth,
                            screenHeight,
                            PixelFormat.RGBA_8888,
                            3
                    );

            imageReader.setOnImageAvailableListener(
                    reader -> copyLatestImage(reader),
                    mainHandler
            );

            // ====================================================
            // VIRTUAL DISPLAY
            // ====================================================

            virtualDisplay =
                    mediaProjection.createVirtualDisplay(
                            "MD JIBON Scanner",
                            screenWidth,
                            screenHeight,
                            screenDensity,
                            DisplayManager
                                    .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            imageReader.getSurface(),
                            null,
                            mainHandler
                    );

            if (virtualDisplay == null) {

                stopCaptureInternal();

                return;
            }

            // ====================================================
            // CAPTURE IS REALLY READY
            // ====================================================

            captureActive = true;

            sendState(true);

        } catch (SecurityException e) {

            captureActive = false;

            sendState(false);

            sendError(
                    "SCREEN CAPTURE SECURITY ERROR"
            );

            stopCaptureObjects();

        } catch (Exception e) {

            captureActive = false;

            sendState(false);

            sendError(
                    "SCREEN CAPTURE START FAILED"
            );

            stopCaptureObjects();
        }
    }

    // ============================================================
    // COPY LATEST IMAGE
    // ============================================================

    private void copyLatestImage(
            ImageReader reader
    ) {

        long now =
                System.currentTimeMillis();

        /*
         * প্রতি 100ms-এর বেশি ঘন ঘন bitmap তৈরি করব না।
         */
        if (now - lastFrameTime < 100) {

            Image old = null;

            try {

                old =
                        reader.acquireLatestImage();

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

            image =
                    reader.acquireLatestImage();

            if (image == null) {

                return;
            }

            Image.Plane[] planes =
                    image.getPlanes();

            if (planes == null ||
                    planes.length == 0) {

                return;
            }

            ByteBuffer buffer =
                    planes[0].getBuffer();

            int pixelStride =
                    planes[0].getPixelStride();

            int rowStride =
                    planes[0].getRowStride();

            int rowPadding =
                    rowStride -
                            pixelStride *
                                    screenWidth;

            int bitmapWidth =
                    screenWidth +
                            rowPadding /
                                    Math.max(
                                            1,
                                            pixelStride
                                    );

            Bitmap raw =
                    Bitmap.createBitmap(
                            bitmapWidth,
                            screenHeight,
                            Bitmap.Config.ARGB_8888
                    );

            raw.copyPixelsFromBuffer(
                    buffer
            );

            Bitmap cropped;

            if (bitmapWidth != screenWidth) {

                cropped =
                        Bitmap.createBitmap(
                                raw,
                                0,
                                0,
                                screenWidth,
                                screenHeight
                        );

                raw.recycle();

            } else {

                cropped = raw;
            }

            synchronized (frameLock) {

                if (latestFrame != null &&
                        !latestFrame.isRecycled()) {

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

    // ============================================================
    // GET FRAME COPY
    // ============================================================

    private Bitmap getFrameCopy() {

        synchronized (frameLock) {

            if (latestFrame == null ||
                    latestFrame.isRecycled()) {

                return null;
            }

            try {

                return latestFrame.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                );

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

            sendResult(
                    "NO TRADE",
                    0.0,
                    0.0,
                    0,
                    0,
                    "UNKNOWN"
            );

            return;
        }

        if (scanning) {

            return;
        }

        final Bitmap frame =
                getFrameCopy();

        if (frame == null) {

            sendResult(
                    "NO TRADE",
                    0.0,
                    0.0,
                    0,
                    0,
                    "UNKNOWN"
            );

            return;
        }

        scanning = true;

        sendProgress(0);

        executor.execute(
                () -> {

                    Analyzer.Result result;

                    try {

                        /*
                         * এখানে আপনার আসল Analyzer।
                         *
                         * Analyzer.java পরিবর্তন করা হয়নি।
                         */
                        result =
                                Analyzer.analyze(
                                        frame
                                );

                    } catch (Exception e) {

                        result =
                                new Analyzer.Result();

                        result.signal =
                                "NO TRADE";

                        result.confidence =
                                0.0;

                        result.quality =
                                0.0;

                        result.evaluatedRules =
                                0;

                        result.detectedCandles =
                                0;
                    }

                    // --------------------------------------------
                    // FRAME RELEASE
                    // --------------------------------------------

                    if (!frame.isRecycled()) {

                        frame.recycle();
                    }

                    final Analyzer.Result finalResult =
                            result;

                    // --------------------------------------------
                    // PROGRESS
                    // --------------------------------------------

                    mainHandler.post(
                            () -> sendProgress(20)
                    );

                    mainHandler.postDelayed(
                            () -> sendProgress(45),
                            120
                    );

                    mainHandler.postDelayed(
                            () -> sendProgress(70),
                            240
                    );

                    mainHandler.postDelayed(
                            () -> sendProgress(90),
                            360
                    );

                    mainHandler.postDelayed(
                            () -> {

                                sendProgress(100);

                                finishScan(
                                        finalResult
                                );

                            },
                            520
                    );
                }
        );
    }

    // ============================================================
    // FINISH SCAN
    // ============================================================

    private void finishScan(
            Analyzer.Result result
    ) {

        scanning = false;

        if (result == null) {

            sendResult(
                    "NO TRADE",
                    0.0,
                    0.0,
                    0,
                    0,
                    getTimeframe()
            );

            return;
        }

        String signal =
                result.signal;

        if (!"UP".equals(signal) &&
                !"DOWN".equals(signal) &&
                !"NO TRADE".equals(signal)) {

            signal =
                    "NO TRADE";
        }

        int rules =
                result.evaluatedRules;

        /*
         * 100 rules সম্পূর্ণ evaluate না হলে
         * UP/DOWN দেখানো হবে না।
         */
        if (rules != 100) {

            signal =
                    "NO TRADE";
        }

        sendResult(
                signal,
                result.confidence,
                result.quality,
                rules,
                result.detectedCandles,
                getTimeframe()
        );
    }

    // ============================================================
    // PROGRESS BROADCAST
    // ============================================================

    private void sendProgress(
            int progress
    ) {

        Intent intent =
                new Intent(
                        ACTION_PROGRESS
                );

        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                "progress",
                Math.max(
                        0,
                        Math.min(
                                100,
                                progress
                        )
                )
        );

        sendBroadcast(intent);
    }

    // ============================================================
    // CAPTURE STATE BROADCAST
    // ============================================================

    private void sendState(
            boolean active
    ) {

        Intent intent =
                new Intent(
                        ACTION_CAPTURE_STATE
                );

        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                "active",
                active
        );

        sendBroadcast(intent);
    }

    // ============================================================
    // ERROR BROADCAST
    // ============================================================

    private void sendError(
            String message
    ) {

        Intent intent =
                new Intent(
                        "com.mdjibon.scanner.ACTION_ERROR"
                );

        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                "message",
                message
        );

        sendBroadcast(intent);
    }

    // ============================================================
    // RESULT
    // ============================================================

    private void sendResult(
            String signal,
            double confidence,
            double quality,
            int ruleCount,
            int candles,
            String timeframe
    ) {

        Intent intent =
                new Intent(
                        ACTION_RESULT
                );

        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                "signal",
                signal
        );

        intent.putExtra(
                "confidence",
                confidence
        );

        intent.putExtra(
                "quality",
                quality
        );

        intent.putExtra(
                "ruleCount",
                ruleCount
        );

        intent.putExtra(
                "detectedCandles",
                candles
        );

        intent.putExtra(
                "timeframe",
                timeframe
        );

        intent.putExtra(
                "candleSize",
                "SCREEN CHART"
        );

        sendBroadcast(intent);
    }

    // ============================================================
    // TIMEFRAME
    // ============================================================

    private String getTimeframe() {

        SharedPreferences prefs =
                getSharedPreferences(
                        "scanner_settings",
                        MODE_PRIVATE
                );

        return prefs.getString(
                "timeframe",
                "1 MIN"
        );
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private Notification createNotification() {

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle(
                        "MD JIBON Scanner"
                )
                .setContentText(
                        "Screen capture is active"
                )
                .setSmallIcon(
                        android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build();
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "MD JIBON Scanner",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }

    // ============================================================
    // STOP CAPTURE OBJECTS
    // ============================================================

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

                    mediaProjection.unregisterCallback(
                            projectionCallback
                    );

                } catch (Exception ignored) {
                }

                mediaProjection.stop();

                mediaProjection = null;
            }

        } catch (Exception ignored) {
        }

        synchronized (frameLock) {

            if (latestFrame != null &&
                    !latestFrame.isRecycled()) {

                latestFrame.recycle();
            }

            latestFrame = null;
        }
    }

    // ============================================================
    // STOP CAPTURE
    // ============================================================

    private void stopCaptureInternal() {

        captureActive = false;

        scanning = false;

        stopCaptureObjects();

        sendState(false);
    }

    // ============================================================
    // DESTROY
    // ============================================================

    @Override
    public void onDestroy() {

        stopCaptureInternal();

        if (executor != null) {

            executor.shutdownNow();
        }

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
