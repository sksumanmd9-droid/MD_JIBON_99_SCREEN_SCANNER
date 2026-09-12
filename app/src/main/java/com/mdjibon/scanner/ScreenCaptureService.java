package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {

    private static final String TAG =
            "MD_JIBON_CAPTURE";

    // ============================================================
    // ACTIONS
    // ============================================================

    public static final String ACTION_RESULT =
            "com.mdjibon.scanner.ACTION_RESULT";

    public static final String ACTION_PROGRESS =
            "com.mdjibon.scanner.ACTION_PROGRESS";

    public static final String ACTION_CAPTURE_STATE =
            "com.mdjibon.scanner.ACTION_CAPTURE_STATE";

    public static final String ACTION_ERROR =
            "com.mdjibon.scanner.ACTION_ERROR";

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

    private volatile long lastFrameTime =
            0L;

    // ============================================================
    // PROJECTION CALLBACK
    // ============================================================

    private final MediaProjection.Callback
            projectionCallback =
            new MediaProjection.Callback() {

                @Override
                public void onStop() {

                    Log.d(
                            TAG,
                            "MediaProjection stopped"
                    );

                    stopCaptureInternal(
                            true
                    );
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

        if (intent == null) {

            return START_STICKY;
        }

        String action =
                intent.getAction();

        // --------------------------------------------------------
        // STOP
        // --------------------------------------------------------

        if (ACTION_STOP.equals(action)) {

            stopCaptureInternal(
                    false
            );

            stopForegroundService();

            stopSelf();

            return START_NOT_STICKY;
        }

        // --------------------------------------------------------
        // SCAN
        // --------------------------------------------------------

        if (ACTION_SCAN.equals(action)) {

            requestScan();

            return START_STICKY;
        }

        // --------------------------------------------------------
        // START CAPTURE
        // --------------------------------------------------------

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

        try {

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

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not read projection data",
                    e
            );

            return null;
        }
    }

    // ============================================================
    // START SCREEN CAPTURE
    // ============================================================

    private synchronized void startCapture(
            int resultCode,
            Intent data
    ) {

        // Already active.
        if (captureActive &&
                mediaProjection != null &&
                virtualDisplay != null &&
                imageReader != null) {

            sendState(true);

            return;
        }

        // Clean any half-created old session.
        stopCaptureInternal(
                false
        );

        try {

            // ----------------------------------------------------
            // FOREGROUND SERVICE
            // ----------------------------------------------------

            Notification notification =
                    createNotification();

            if (Build.VERSION.SDK_INT >= 29) {

                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );

            } else {

                startForeground(
                        NOTIFICATION_ID,
                        notification
                );
            }

            // ----------------------------------------------------
            // MEDIA PROJECTION MANAGER
            // ----------------------------------------------------

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    MEDIA_PROJECTION_SERVICE
                            );

            if (manager == null) {

                throw new IllegalStateException(
                        "MediaProjectionManager unavailable"
                );
            }

            // ----------------------------------------------------
            // GET MEDIA PROJECTION
            // ----------------------------------------------------

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            data
                    );

            if (mediaProjection == null) {

                throw new IllegalStateException(
                        "MediaProjection is null"
                );
            }

            mediaProjection.registerCallback(
                    projectionCallback,
                    mainHandler
            );

            // ----------------------------------------------------
            // DISPLAY METRICS
            // ----------------------------------------------------

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

                throw new IllegalStateException(
                        "Invalid screen size"
                );
            }

            // ----------------------------------------------------
            // IMAGE READER
            // ----------------------------------------------------

            imageReader =
                    ImageReader.newInstance(
                            screenWidth,
                            screenHeight,
                            PixelFormat.RGBA_8888,
                            3
                    );

            imageReader.setOnImageAvailableListener(
                    reader ->
                            copyLatestImage(reader),
                    mainHandler
            );

            // ----------------------------------------------------
            // VIRTUAL DISPLAY
            // ----------------------------------------------------

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

                throw new IllegalStateException(
                        "VirtualDisplay is null"
                );
            }

            // ----------------------------------------------------
            // CAPTURE IS NOW ACTIVE
            // ----------------------------------------------------

            captureActive = true;

            lastFrameTime = 0L;

            sendState(true);

            Log.d(
                    TAG,
                    "SCREEN CAPTURE ACTIVE"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "SCREEN CAPTURE START FAILED",
                    e
            );

            captureActive = false;

            stopCaptureInternal(
                    false
            );

            sendState(false);

            sendError(
                    "SCREEN CAPTURE START FAILED"
            );
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
         * Maximum roughly 10 frames/sec stored.
         * Old Image is always closed.
         */
        if (now - lastFrameTime < 100) {

            Image old = null;

            try {

                old =
                        reader.acquireLatestImage();

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Unable to discard old image",
                        e
                );
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

            if (buffer == null) {

                return;
            }

            int pixelStride =
                    planes[0].getPixelStride();

            int rowStride =
                    planes[0].getRowStride();

            if (pixelStride <= 0 ||
                    rowStride <= 0) {

                return;
            }

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

            if (bitmapWidth < screenWidth) {

                return;
            }

            Bitmap raw =
                    Bitmap.createBitmap(
                            bitmapWidth,
                            screenHeight,
                            Bitmap.Config.ARGB_8888
                    );

            buffer.rewind();

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

                latestFrame =
                        cropped;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Frame copy failed",
                    e
            );

        } finally {

            if (image != null) {

                image.close();
            }
        }
    }

    // ============================================================
    // SAFE FRAME COPY
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

                Log.e(
                        TAG,
                        "Frame copy unavailable",
                        e
                );

                return null;
            }
        }
    }

    // ============================================================
    // WAIT FOR FIRST FRAME
    // ============================================================

    private Bitmap waitForFrameCopy() {

        final long timeout =
                3500L;

        final long deadline =
                SystemClock.uptimeMillis()
                        + timeout;

        while (
                captureActive &&
                SystemClock.uptimeMillis()
                        < deadline
        ) {

            Bitmap frame =
                    getFrameCopy();

            if (frame != null) {

                return frame;
            }

            SystemClock.sleep(
                    100
            );
        }

        return null;
    }

    // ============================================================
    // REQUEST SCAN
    // ============================================================

    private synchronized void requestScan() {

        /*
         * Capture must be genuinely active.
         */
        if (!captureActive ||
                mediaProjection == null ||
                virtualDisplay == null ||
                imageReader == null) {

            sendResult(
                    "NO TRADE",
                    0,
                    0.0,
                    0,
                    0,
                    getTimeframe()
            );

            return;
        }

        /*
         * Prevent two scans at the same time.
         */
        if (scanning) {

            return;
        }

        scanning = true;

        sendProgress(
                0
        );

        /*
         * Wait for a real screen frame.
         *
         * This is important because immediately after
         * MediaProjection starts, ImageReader may not yet
         * contain a frame.
         */
        executor.execute(
                () -> {

                    Bitmap frame =
                            null;

                    try {

                        sendProgress(
                                10
                        );

                        frame =
                                waitForFrameCopy();

                        if (frame == null) {

                            sendProgress(
                                    100
                            );

                            sendResult(
                                    "NO TRADE",
                                    0,
                                    0.0,
                                    0,
                                    0,
                                    getTimeframe()
                            );

                            return;
                        }

                        sendProgress(
                                20
                        );

                        Analyzer.Result result;

                        try {

                            /*
                             * REAL Analyzer.
                             *
                             * Analyzer.java remains unchanged.
                             * It performs the existing 100 deterministic
                             * logic checks.
                             */
                            result =
                                    Analyzer.analyze(
                                            frame
                                    );

                        } catch (Exception e) {

                            Log.e(
                                    TAG,
                                    "Analyzer failed",
                                    e
                            );

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

                        sendProgress(
                                100
                        );

                        final Analyzer.Result
                                finalResult =
                                result;

                        mainHandler.postDelayed(
                                () ->
                                        finishScan(
                                                finalResult
                                        ),
                                180
                        );

                    } finally {

                        if (frame != null &&
                                !frame.isRecycled()) {

                            frame.recycle();
                        }
                    }
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
                    0,
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
         * HARD SAFETY:
         *
         * If all 100 Analyzer rules did not complete,
         * UP/DOWN is forbidden.
         */
        if (rules != 100) {

            signal =
                    "NO TRADE";
        }

        int confidence =
                (int)
                        Math.round(
                                Math.max(
                                        0.0,
                                        Math.min(
                                                100.0,
                                                result.confidence
                                        )
                                )
                        );

        double quality =
                Math.max(
                        0.0,
                        Math.min(
                                100.0,
                                result.quality
                        )
                );

        sendResult(
                signal,
                confidence,
                quality,
                rules,
                result.detectedCandles,
                getTimeframe()
        );

        /*
         * IMPORTANT:
         *
         * We DO NOT stop MediaProjection here.
         * Screen Capture remains active for the next scan.
         */
    }

    // ============================================================
    // PROGRESS
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

        sendBroadcast(
                intent
        );
    }

    // ============================================================
    // CAPTURE STATE
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

        sendBroadcast(
                intent
        );
    }

    // ============================================================
    // ERROR
    // ============================================================

    private void sendError(
            String message
    ) {

        Intent intent =
                new Intent(
                        ACTION_ERROR
                );

        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                "message",
                message
        );

        sendBroadcast(
                intent
        );
    }

    // ============================================================
    // RESULT
    // ============================================================

    private void sendResult(
            String signal,
            int confidence,
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

        sendBroadcast(
                intent
        );
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

        if (Build.VERSION.SDK_INT < 26) {

            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "MD JIBON Scanner",
                        NotificationManager
                                .IMPORTANCE_LOW
                );

        channel.setDescription(
                "MD JIBON Screen Capture"
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

    // ============================================================
    // STOP CAPTURE
    // ============================================================

    private synchronized void stopCaptureInternal(
            boolean fromProjectionCallback
    ) {

        captureActive = false;

        scanning = false;

        // --------------------------------------------------------
        // Virtual Display
        // --------------------------------------------------------

        try {

            if (virtualDisplay != null) {

                virtualDisplay.release();

                virtualDisplay = null;
            }

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "VirtualDisplay release failed",
                    e
            );
        }

        // --------------------------------------------------------
        // Image Reader
        // --------------------------------------------------------

        try {

            if (imageReader != null) {

                imageReader.close();

                imageReader = null;
            }

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "ImageReader close failed",
                    e
            );
        }

        // --------------------------------------------------------
        // Media Projection
        // --------------------------------------------------------

        MediaProjection projection =
                mediaProjection;

        mediaProjection = null;

        if (projection != null) {

            try {

                projection.unregisterCallback(
                        projectionCallback
                );

            } catch (Exception ignored) {
            }

            /*
             * Do not call stop() again if Android itself
             * already stopped the projection.
             */
            if (!fromProjectionCallback) {

                try {

                    projection.stop();

                } catch (Exception ignored) {
                }
            }
        }

        // --------------------------------------------------------
        // Latest Frame
        // --------------------------------------------------------

        synchronized (frameLock) {

            if (latestFrame != null &&
                    !latestFrame.isRecycled()) {

                latestFrame.recycle();
            }

            latestFrame = null;
        }

        lastFrameTime = 0L;

        sendState(false);
    }

    // ============================================================
    // STOP FOREGROUND
    // ============================================================

    private void stopForegroundService() {

        try {

            if (Build.VERSION.SDK_INT >= 24) {

                stopForeground(
                        STOP_FOREGROUND_REMOVE
                );

            } else {

                stopForeground(
                        true
                );
            }

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "stopForeground failed",
                    e
            );
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    @Override
    public void onDestroy() {

        stopCaptureInternal(
                false
        );

        stopForegroundService();

        if (mainHandler != null) {

            mainHandler.removeCallbacksAndMessages(
                    null
            );
        }

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
