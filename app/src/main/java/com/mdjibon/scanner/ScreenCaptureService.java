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

    private static final String CHANNEL_ID =
            "md_jibon_scanner";

    private static final int NOTIFICATION_ID = 1001;

    private static volatile boolean captureActive = false;
    private static volatile boolean scanning = false;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private Bitmap latestFrame;

    private final Object frameLock =
            new Object();

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

        mainHandler =
                new Handler(
                        Looper.getMainLooper()
                );

        executor =
                Executors.newSingleThreadExecutor();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent == null) {

            /*
             * MediaProjection token পুনরুদ্ধার করা যায় না।
             * তাই পুরোনো capture নিজে থেকে নতুন করে
             * শুরু করার চেষ্টা করা হবে না।
             */
            return START_NOT_STICKY;
        }

        String action =
                intent.getAction();

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

            int resultCode =
                    intent.getIntExtra(
                            "resultCode",
                            -1
                    );

            Intent data;

            if (Build.VERSION.SDK_INT >= 33) {

                data =
                        intent.getParcelableExtra(
                                "data",
                                Intent.class
                        );

            } else {

                data =
                        intent.getParcelableExtra(
                                "data"
                        );
            }

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

    private void startCapture(
            int resultCode,
            Intent data
    ) {

        if (captureActive) {
            return;
        }

        try {

            if (Build.VERSION.SDK_INT >= 29) {

                startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );

            } else {

                startForeground(
                        NOTIFICATION_ID,
                        createNotification()
                );
            }

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    MEDIA_PROJECTION_SERVICE
                            );

            if (manager == null) {

                sendError(
                        "Screen Capture unavailable"
                );

                stopSelf();

                return;
            }

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            data
                    );

            if (mediaProjection == null) {

                sendError(
                        "MediaProjection পাওয়া যায়নি"
                );

                stopSelf();

                return;
            }

            mediaProjection.registerCallback(
                    projectionCallback,
                    mainHandler
            );

            android.util.DisplayMetrics metrics =
                    getResources()
                            .getDisplayMetrics();

            screenWidth =
                    metrics.widthPixels;

            screenHeight =
                    metrics.heightPixels;

            screenDensity =
                    metrics.densityDpi;

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

                sendError(
                        "Virtual Display তৈরি হয়নি"
                );

                stopCaptureInternal();

                stopSelf();

                return;
            }

            captureActive = true;

            sendState(true);

        } catch (Exception e) {

            captureActive = false;

            sendState(false);

            String error =
                    e.getMessage();

            if (error == null ||
                    error.trim().isEmpty()) {

                error =
                        "SCREEN CAPTURE START FAILED";
            }

            sendError(
                    "SCREEN CAPTURE ERROR: " +
                            error
            );

            stopCaptureInternal();
        }
    }

    private void copyLatestImage(
            ImageReader reader
    ) {

        long now =
                System.currentTimeMillis();

        if (now - lastFrameTime < 80) {

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

    private void requestScan() {

        if (!captureActive) {

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

        if (scanning) {
            return;
        }

        scanning = true;

        sendProgress(0);

        executor.execute(
                () -> {

                    Bitmap frame = null;

                    long start =
                            System.currentTimeMillis();

                    /*
                     * Screen Capture ON হওয়ার পরে
                     * ImageReader-এর প্রথম frame আসতে
                     * কিছু সময় লাগতে পারে।
                     */
                    while (
                            System.currentTimeMillis()
                                    - start
                                    < 2000
                    ) {

                        frame =
                                getFrameCopy();

                        if (frame != null) {
                            break;
                        }

                        try {

                            Thread.sleep(80);

                        } catch (InterruptedException e) {

                            Thread.currentThread()
                                    .interrupt();

                            break;
                        }
                    }

                    if (frame == null) {

                        mainHandler.post(
                                () -> {

                                    scanning = false;

                                    sendResult(
                                            "NO TRADE",
                                            0,
                                            0.0,
                                            0,
                                            0,
                                            getTimeframe()
                                    );
                                }
                        );

                        return;
                    }

                    Analyzer.Result result;

                    try {

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

                        final String error =
                                e.getMessage();

                        mainHandler.post(
                                () -> {

                                    if (error != null &&
                                            !error.trim()
                                                    .isEmpty()) {

                                        sendError(
                                                "Analyzer error: " +
                                                        error
                                        );
                                    }
                                }
                        );
                    }

                    if (!frame.isRecycled()) {
                        frame.recycle();
                    }

                    final Analyzer.Result finalResult =
                            result;

                    mainHandler.post(
                            () ->
                                    sendProgress(100)
                    );

                    mainHandler.postDelayed(
                            () ->
                                    finishScan(
                                            finalResult
                                    ),
                            180
                    );
                }
        );
    }

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

            signal = "NO TRADE";
        }

        int rules =
                result.evaluatedRules;

        if (rules != 100) {
            signal = "NO TRADE";
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
    }

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

        sendBroadcast(intent);
    }

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

        sendBroadcast(intent);
    }

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

    private void stopCaptureInternal() {

        captureActive = false;
        scanning = false;

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

                mediaProjection.unregisterCallback(
                        projectionCallback
                );

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

        sendState(false);
    }

    @Override
    public void onDestroy() {

        stopCaptureInternal();

        if (executor != null) {

            executor.shutdownNow();

            executor = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}
