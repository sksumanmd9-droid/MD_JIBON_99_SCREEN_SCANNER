package com.mdjibon.scanner;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

    private static volatile boolean captureActive = false;

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;

    private Bitmap latest;

    private final Object lock =
            new Object();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private Handler main;

    // ================================================================
    // CAPTURE STATUS
    // ================================================================

    public static boolean isCaptureActive() {

        return captureActive;
    }

    // ================================================================
    // MEDIA PROJECTION CALLBACK
    // ================================================================

    private final MediaProjection.Callback projectionCallback =
            new MediaProjection.Callback() {

                @Override
                public void onStop() {

                    captureActive = false;

                    releaseObjects(false);

                    sendState(false);
                }
            };

    // ================================================================
    // SERVICE CREATE
    // ================================================================

    @Override
    public void onCreate() {

        super.onCreate();

        main =
                new Handler(
                        Looper.getMainLooper()
                );

        startForegroundCompat();
    }

    // ================================================================
    // START COMMAND
    // ================================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent == null) {

            return START_STICKY;
        }

        String action =
                intent.getAction();

        // ============================================================
        // START SCREEN CAPTURE
        // ============================================================

        if (ACTION_START_CAPTURE.equals(action)) {

            int resultCode =
                    intent.getIntExtra(
                            "resultCode",
                            Activity.RESULT_CANCELED
                    );

            Intent data =
                    getIntentExtra(
                            intent,
                            "data"
                    );

            startCapture(
                    resultCode,
                    data
            );
        }

        // ============================================================
        // SCAN CURRENT SCREEN
        // ============================================================

        else if (ACTION_SCAN.equals(action)) {

            scan(
                    intent.getIntExtra(
                            "excludeX",
                            -1
                    ),
                    intent.getIntExtra(
                            "excludeY",
                            -1
                    ),
                    intent.getIntExtra(
                            "excludeW",
                            0
                    ),
                    intent.getIntExtra(
                            "excludeH",
                            0
                    )
            );
        }

        // ============================================================
        // STOP
        // ============================================================

        else if ("STOP".equals(action)) {

            releaseObjects(true);

            stopSelf();
        }

        return START_STICKY;
    }

    // ================================================================
    // ANDROID 13+ PARCELABLE COMPATIBILITY
    // ================================================================

    @SuppressWarnings("deprecation")
    private Intent getIntentExtra(
            Intent intent,
            String key) {

        if (Build.VERSION.SDK_INT >= 33) {

            return intent.getParcelableExtra(
                    key,
                    Intent.class
            );
        }

        return intent.getParcelableExtra(
                key
        );
    }

    // ================================================================
    // FOREGROUND SERVICE
    // ================================================================

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

        Notification.Builder builder =
                Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(
                        this,
                        channelId
                )
                        : new Notification.Builder(
                        this
                );

        startForeground(
                9902,
                builder
                        .setContentTitle(
                                "MD JIBON Scanner"
                        )
                        .setContentText(
                                "Current screen capture is active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_view
                        )
                        .build()
        );
    }

    // ================================================================
    // START CAPTURE
    // ================================================================

    private void startCapture(
            int resultCode,
            Intent data) {

        if (resultCode != Activity.RESULT_OK
                || data == null) {

            sendError(
                    "Screen capture permission was not granted."
            );

            return;
        }

        try {

            releaseObjects(false);

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
                        "MediaProjection could not start."
                );

                return;
            }

            projection.registerCallback(
                    projectionCallback,
                    main
            );

            android.util.DisplayMetrics metrics =
                    getResources()
                            .getDisplayMetrics();

            int width =
                    metrics.widthPixels;

            int height =
                    metrics.heightPixels;

            int density =
                    metrics.densityDpi;

            // ========================================================
            // IMAGE READER
            // ========================================================

            reader =
                    ImageReader.newInstance(
                            width,
                            height,
                            android.graphics.PixelFormat.RGBA_8888,
                            3
                    );

            reader.setOnImageAvailableListener(
                    r -> copyLatest(
                            r,
                            width,
                            height
                    ),
                    main
            );

            // ========================================================
            // VIRTUAL DISPLAY
            // ========================================================

            display =
                    projection.createVirtualDisplay(
                            "MD_JIBON_Current_Screen",
                            width,
                            height,
                            density,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            reader.getSurface(),
                            null,
                            main
                    );

            captureActive =
                    display != null;

            sendState(
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

    // ================================================================
    // COPY LATEST SCREEN FRAME
    // ================================================================

    private void copyLatest(
            ImageReader imageReader,
            int width,
            int height) {

        Image image = null;

        try {

            image =
                    imageReader.acquireLatestImage();

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

            int padding =
                    rowStride
                            - pixelStride * width;

            int bitmapWidth =
                    width
                            + padding
                            / Math.max(
                            1,
                            pixelStride
                    );

            Bitmap raw =
                    Bitmap.createBitmap(
                            bitmapWidth,
                            height,
                            Bitmap.Config.ARGB_8888
                    );

            raw.copyPixelsFromBuffer(
                    buffer
            );

            Bitmap cropped;

            if (bitmapWidth == width) {

                cropped = raw;

            } else {

                cropped =
                        Bitmap.createBitmap(
                                raw,
                                0,
                                0,
                                width,
                                height
                        );

                raw.recycle();
            }

            synchronized (lock) {

                if (latest != null
                        && !latest.isRecycled()) {

                    latest.recycle();
                }

                latest = cropped;
            }

        } catch (Exception ignored) {

        } finally {

            if (image != null) {

                image.close();
            }
        }
    }

    // ================================================================
    // SCAN CURRENT SCREEN
    // ================================================================

    private void scan(
            int excludeX,
            int excludeY,
            int excludeW,
            int excludeH) {

        Bitmap source;

        synchronized (lock) {

            if (latest == null
                    || latest.isRecycled()) {

                sendError(
                        "No current screen frame is ready yet."
                );

                return;
            }

            source =
                    latest.copy(
                            Bitmap.Config.ARGB_8888,
                            true
                    );
        }

        // ============================================================
        // SCAN STATUS
        // ============================================================

        sendStatus(
                "working",
                "SCANNING LIVE SCREEN • "
                        + Analyzer.TOTAL_RULES
                        + " LOGIC CHECKS..."
        );

        final Bitmap captured =
                source;

        executor.execute(
                () -> {

                    Bitmap clean = null;

                    try {

                        clean =
                                captured.copy(
                                        Bitmap.Config.ARGB_8888,
                                        true
                                );

                        // =================================================
                        // HIDE FLOATING BUBBLE FROM ANALYSIS
                        // =================================================

                        if (excludeW > 0
                                && excludeH > 0) {

                            Canvas canvas =
                                    new Canvas(
                                            clean
                                    );

                            Paint paint =
                                    new Paint(
                                            Paint.ANTI_ALIAS_FLAG
                                    );

                            paint.setColor(
                                    Color.rgb(
                                            3,
                                            8,
                                            15
                                    )
                            );

                            int left =
                                    Math.max(
                                            0,
                                            excludeX
                                    );

                            int top =
                                    Math.max(
                                            0,
                                            excludeY
                                    );

                            int right =
                                    Math.min(
                                            clean.getWidth(),
                                            excludeX
                                                    + excludeW
                                    );

                            int bottom =
                                    Math.min(
                                            clean.getHeight(),
                                            excludeY
                                                    + excludeH
                                    );

                            if (right > left
                                    && bottom > top) {

                                canvas.drawRect(
                                        left,
                                        top,
                                        right,
                                        bottom,
                                        paint
                                );
                            }
                        }

                        // =================================================
                        // TIMEFRAME
                        // =================================================

                        String timeframe =
                                getSharedPreferences(
                                        "scanner_settings",
                                        MODE_PRIVATE
                                )
                                        .getString(
                                                "timeframe",
                                                "1 MIN"
                                        );

                        // =================================================
                        // ANALYZE
                        // =================================================

                        Analyzer.Result result =
                                Analyzer.analyze(
                                        clean,
                                        timeframe
                                );

                        // =================================================
                        // RESULT BROADCAST
                        // =================================================

                        Intent output =
                                new Intent(
                                        ACTION_RESULT
                                );

                        output.setPackage(
                                getPackageName()
                        );

                        output.putExtra(
                                "signal",
                                result.signal
                        );

                        output.putExtra(
                                "strong",
                                result.strongSignal
                        );

                        output.putExtra(
                                "score",
                                (float)
                                        result.confidence
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

                        sendBroadcast(
                                output
                        );

                        // =================================================
                        // DONE STATUS
                        // =================================================

                        if (result.strongSignal) {

                            sendStatus(
                                    "done",
                                    "STRONG EVIDENCE FOUND"
                            );

                        } else {

                            sendStatus(
                                    "done",
                                    "SCANNING • WAITING FOR STRONG AGREEMENT"
                            );
                        }

                    } catch (Exception e) {

                        sendError(
                                "Analysis failed: "
                                        + String.valueOf(
                                        e.getMessage()
                                )
                        );

                    } finally {

                        if (clean != null
                                && !clean.isRecycled()) {

                            clean.recycle();
                        }

                        if (!captured.isRecycled()) {

                            captured.recycle();
                        }
                    }
                }
        );
    }

    // ================================================================
    // CAPTURE STATE
    // ================================================================

    private void sendState(
            boolean active) {

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

    // ================================================================
    // SCAN STATUS
    // ================================================================

    private void sendStatus(
            String state,
            String message) {

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

        intent.putExtra(
                "message",
                message
        );

        sendBroadcast(
                intent
        );
    }

    // ================================================================
    // ERROR
    // ================================================================

    private void sendError(
            String message) {

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

    // ================================================================
    // RELEASE CAPTURE OBJECTS
    // ================================================================

    private void releaseObjects(
            boolean stopProjection) {

        try {

            if (display != null) {

                display.release();
            }

        } catch (Exception ignored) {
        }

        try {

            if (reader != null) {

                reader.close();
            }

        } catch (Exception ignored) {
        }

        if (projection != null) {

            try {

                projection.unregisterCallback(
                        projectionCallback
                );

            } catch (Exception ignored) {
            }

            if (stopProjection) {

                try {

                    projection.stop();

                } catch (Exception ignored) {
                }
            }
        }

        display = null;

        reader = null;

        projection = null;

        synchronized (lock) {

            if (latest != null
                    && !latest.isRecycled()) {

                latest.recycle();
            }

            latest = null;
        }

        captureActive = false;
    }

    // ================================================================
    // DESTROY
    // ================================================================

    @Override
    public void onDestroy() {

        releaseObjects(true);

        executor.shutdownNow();

        super.onDestroy();
    }

    // ================================================================
    // BIND
    // ================================================================

    @Override
    public IBinder onBind(
            Intent intent) {

        return null;
    }
}
