package com.mdjibon.scanner;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {

    public static final String ACTION_START_CAPTURE =
            "com.mdjibon.scanner.START_CAPTURE";

    public static final String ACTION_SCAN =
            "com.mdjibon.scanner.ACTION_SCAN";

    public static final String ACTION_RESULT =
            "com.mdjibon.scanner.ACTION_RESULT";

    public static final String ACTION_ERROR =
            "com.mdjibon.scanner.ACTION_ERROR";

    public static final String ACTION_CAPTURE_STATE =
            "com.mdjibon.scanner.ACTION_CAPTURE_STATE";

    public static final String ACTION_SCAN_STATUS =
            "com.mdjibon.scanner.ACTION_SCAN_STATUS";

    private static final int NOTIFICATION_ID = 9902;

    private static volatile boolean captureActive = false;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Object frameLock =
            new Object();

    private Bitmap latestFrame;

    private Handler handler;

    public static boolean isCaptureActive() {
        return captureActive;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        handler =
                new Handler(
                        Looper.getMainLooper()
                );

        startForegroundCompat();
    }

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

        if (
                ACTION_START_CAPTURE.equals(action)
        ) {

            int resultCode =
                    intent.getIntExtra(
                            "resultCode",
                            Activity.RESULT_CANCELED
                    );

            Intent data =
                    getProjectionIntent(
                            intent,
                            "data"
                    );

            startCapture(
                    resultCode,
                    data
            );

        } else if (
                ACTION_SCAN.equals(action)
        ) {

            int excludeX =
                    intent.getIntExtra(
                            "excludeX",
                            -1
                    );

            int excludeY =
                    intent.getIntExtra(
                            "excludeY",
                            -1
                    );

            int excludeW =
                    intent.getIntExtra(
                            "excludeW",
                            0
                    );

            int excludeH =
                    intent.getIntExtra(
                            "excludeH",
                            0
                    );

            scan(
                    excludeX,
                    excludeY,
                    excludeW,
                    excludeH
            );

        } else if (
                "STOP".equals(action)
        ) {

            stopCaptureInternal();
            stopSelf();
        }

        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Intent getProjectionIntent(
            Intent intent,
            String key
    ) {

        if (Build.VERSION.SDK_INT >= 33) {

            return intent.getParcelableExtra(
                    key,
                    Intent.class
            );
        }

        return intent.getParcelableExtra(key);
    }

    private void startForegroundCompat() {

        String channelId =
                "md_jibon_capture";

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if (Build.VERSION.SDK_INT >= 26) {

            manager.createNotificationChannel(
                    new NotificationChannel(
                            channelId,
                            "MD JIBON Screen Capture",
                            NotificationManager.IMPORTANCE_LOW
                    )
            );
        }

        Intent open =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | (
                                Build.VERSION.SDK_INT >= 23
                                        ? PendingIntent.FLAG_IMMUTABLE
                                        : 0
                        )
                );

        Notification.Builder builder =
                Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(
                        this,
                        channelId
                )
                        : new Notification.Builder(this);

        Notification notification =
                builder
                        .setContentTitle(
                                "MD JIBON Scanner"
                        )
                        .setContentText(
                                "Screen capture is ready"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_view
                        )
                        .setContentIntent(
                                pendingIntent
                        )
                        .build();

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
    }

    private synchronized void startCapture(
            int resultCode,
            Intent data
    ) {

        if (
                resultCode != Activity.RESULT_OK
                        || data == null
        ) {

            sendError(
                    "Screen capture permission was not granted."
            );

            return;
        }

        if (
                captureActive
                        && projection != null
                        && virtualDisplay != null
        ) {

            sendCaptureState(true);
            return;
        }

        try {

            stopCaptureObjectsOnly();

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    MEDIA_PROJECTION_SERVICE
                            );

            projection =
                    manager.getMediaProjection(
                            resultCode,
                            data
                    );

            if (projection == null) {

                sendError(
                        "MediaProjection could not be created."
                );

                return;
            }

            projection.registerCallback(
                    new MediaProjection.Callback() {

                        @Override
                        public void onStop() {

                            captureActive = false;

                            handler.post(
                                    () ->
                                            sendCaptureState(
                                                    false
                                            )
                            );
                        }
                    },
                    handler
            );

            DisplayMetrics metrics =
                    getResources()
                            .getDisplayMetrics();

            int width =
                    metrics.widthPixels;

            int height =
                    metrics.heightPixels;

            int density =
                    metrics.densityDpi;

            imageReader =
                    ImageReader.newInstance(
                            width,
                            height,
                            android.graphics.PixelFormat
                                    .RGBA_8888,
                            3
                    );

            imageReader.setOnImageAvailableListener(
                    reader -> {

                        Image image = null;

                        try {

                            image =
                                    reader.acquireLatestImage();

                            if (image == null) {
                                return;
                            }

                            Image.Plane plane =
                                    image.getPlanes()[0];

                            ByteBuffer buffer =
                                    plane.getBuffer();

                            int pixelStride =
                                    plane.getPixelStride();

                            int rowStride =
                                    plane.getRowStride();

                            int rowPadding =
                                    rowStride
                                            - pixelStride * width;

                            Bitmap full =
                                    Bitmap.createBitmap(
                                            width
                                                    + rowPadding
                                                    / pixelStride,
                                            height,
                                            Bitmap.Config.ARGB_8888
                                    );

                            buffer.rewind();

                            full.copyPixelsFromBuffer(
                                    buffer
                            );

                            Bitmap cropped =
                                    Bitmap.createBitmap(
                                            full,
                                            0,
                                            0,
                                            width,
                                            height
                                    );

                            full.recycle();

                            synchronized (frameLock) {

                                if (
                                        latestFrame != null
                                                && !latestFrame.isRecycled()
                                ) {
                                    latestFrame.recycle();
                                }

                                latestFrame =
                                        cropped;
                            }

                        } catch (Exception ignored) {

                        } finally {

                            if (image != null) {
                                image.close();
                            }
                        }

                    },
                    handler
            );

            virtualDisplay =
                    projection.createVirtualDisplay(
                            "MD_JIBON_SCREEN",
                            width,
                            height,
                            density,
                            DisplayManager
                                    .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            imageReader.getSurface(),
                            null,
                            handler
                    );

            captureActive =
                    virtualDisplay != null;

            sendCaptureState(
                    captureActive
            );

            if (!captureActive) {

                sendError(
                        "Virtual screen could not be created."
                );
            }

        } catch (Exception e) {

            captureActive = false;

            sendError(
                    "Capture start failed: "
                            + String.valueOf(
                            e.getMessage()
                    )
            );
        }
    }

    private void scan(
            int excludeX,
            int excludeY,
            int excludeW,
            int excludeH
    ) {

        if (!captureActive) {

            sendError(
                    "MARKET SCREEN CAPTURE IS NOT ACTIVE."
            );

            return;
        }

        Bitmap frame;

        synchronized (frameLock) {

            if (
                    latestFrame == null
                            || latestFrame.isRecycled()
            ) {

                sendError(
                        "No current market screen frame is ready."
                );

                return;
            }

            frame =
                    latestFrame.copy(
                            Bitmap.Config.ARGB_8888,
                            false
                    );
        }

        final Bitmap source =
                frame;

        sendScanStatus("working");

        executor.execute(
                () -> {

                    Bitmap clean =
                            source;

                    try {

                        /*
                         * Remove the floating icon area
                         * from the captured frame.
                         */
                        if (
                                excludeW > 0
                                        && excludeH > 0
                        ) {

                            try {

                                clean =
                                        source.copy(
                                                Bitmap.Config.ARGB_8888,
                                                true
                                        );

                                Canvas canvas =
                                        new Canvas(clean);

                                android.graphics.Paint paint =
                                        new android.graphics.Paint(
                                                android.graphics.Paint.ANTI_ALIAS_FLAG
                                        );

                                paint.setColor(
                                        Color.rgb(
                                                5,
                                                10,
                                                20
                                        )
                                );

                                canvas.drawRect(
                                        Math.max(
                                                0,
                                                excludeX
                                        ),
                                        Math.max(
                                                0,
                                                excludeY
                                        ),
                                        Math.min(
                                                clean.getWidth(),
                                                excludeX
                                                        + excludeW
                                        ),
                                        Math.min(
                                                clean.getHeight(),
                                                excludeY
                                                        + excludeH
                                        ),
                                        paint
                                );

                            } catch (Exception ignored) {

                                clean =
                                        source;
                            }
                        }

                        String timeframe =
                                getSharedPreferences(
                                        "scanner_settings",
                                        MODE_PRIVATE
                                )
                                        .getString(
                                                "timeframe",
                                                "1 MIN"
                                        );

                        Analyzer.Result result =
                                Analyzer.analyze(
                                        clean,
                                        timeframe
                                );

                        Intent output =
                                new Intent(
                                        ACTION_RESULT
                                );

                        output.setPackage(
                                getPackageName()
                        );

                        /*
                         * IMPORTANT:
                         * Weak result remains NONE.
                         */
                        String signal =
                                result.strongSignal
                                        ? result.signal
                                        : "NONE";

                        float score =
                                result.strongSignal
                                        ? (float)
                                        result.confidence
                                        : 0.0f;

                        output.putExtra(
                                "signal",
                                signal
                        );

                        output.putExtra(
                                "score",
                                score
                        );

                        output.putExtra(
                                "strong",
                                result.strongSignal
                        );

                        output.putExtra(
                                "chartDetected",
                                result.chartDetected
                        );

                        output.putExtra(
                                "nextColor",
                                result.nextCandleColor
                        );

                        output.putExtra(
                                "nextSize",
                                result.nextCandleSize
                        );

                        output.putExtra(
                                "body",
                                (float)
                                        result.nextBodyRatio
                        );

                        output.putExtra(
                                "candles",
                                result.detectedCandles
                        );

                        output.putExtra(
                                "rules",
                                result.evaluatedRules
                        );

                        output.putExtra(
                                "quality",
                                (float)
                                        result.quality
                        );

                        output.putExtra(
                                "currentColor",
                                result.currentCandleColor
                        );

                        output.putExtra(
                                "currentBody",
                                (float)
                                        result.currentBodyRatio
                        );

                        sendBroadcast(
                                output
                        );

                    } catch (Exception e) {

                        sendError(
                                "Analysis failed: "
                                        + String.valueOf(
                                        e.getMessage()
                                )
                        );

                    } finally {

                        if (
                                clean != source
                                        && !clean.isRecycled()
                        ) {
                            clean.recycle();
                        }

                        if (
                                !source.isRecycled()
                        ) {
                            source.recycle();
                        }

                        sendScanStatus("done");
                    }
                }
        );
    }

    private void sendScanStatus(
            String state
    ) {

        Intent intent =
                new Intent(
                        ACTION_SCAN_STATUS
                );

        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                "state",
                state
        );

        sendBroadcast(intent);
    }

    private void sendCaptureState(
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

    private void stopCaptureObjectsOnly() {

        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
        } catch (Exception ignored) {}

        try {
            if (imageReader != null) {
                imageReader.close();
            }
        } catch (Exception ignored) {}

        try {
            if (projection != null) {
                projection.stop();
            }
        } catch (Exception ignored) {}

        virtualDisplay = null;
        imageReader = null;
        projection = null;

        synchronized (frameLock) {

            if (
                    latestFrame != null
                            && !latestFrame.isRecycled()
            ) {
                latestFrame.recycle();
            }

            latestFrame = null;
        }

        captureActive = false;
    }

    private void stopCaptureInternal() {

        stopCaptureObjectsOnly();

        sendCaptureState(false);
    }

    @Override
    public void onDestroy() {

        stopCaptureInternal();

        executor.shutdownNow();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {
        return null;
    }
}
