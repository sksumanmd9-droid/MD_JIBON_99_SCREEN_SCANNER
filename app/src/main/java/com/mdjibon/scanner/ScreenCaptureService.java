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

/**
 * Owns the MediaProjection session and analyzes the latest real screen frame.
 */
public class ScreenCaptureService extends Service {

    public static final String ACTION_START_CAPTURE = "com.mdjibon.scanner.START_CAPTURE";
    public static final String ACTION_SCAN = "com.mdjibon.scanner.ACTION_SCAN";
    public static final String ACTION_RESULT = "com.mdjibon.scanner.ACTION_RESULT";
    public static final String ACTION_ERROR = "com.mdjibon.scanner.ACTION_ERROR";
    public static final String ACTION_CAPTURE_STATE = "com.mdjibon.scanner.ACTION_CAPTURE_STATE";

    private static final int NOTIFICATION_ID = 9902;
    private static volatile boolean captureActive = false;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object frameLock = new Object();
    private Bitmap latestFrame;
    private Handler handler;

    public static boolean isCaptureActive() {
        return captureActive;
    }

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        startForegroundCompat();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (ACTION_START_CAPTURE.equals(action)) {
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent data = getParcelableIntent(intent, "data");
            startCapture(resultCode, data);
        } else if (ACTION_SCAN.equals(action)) {
            int ex = intent.getIntExtra("excludeX", -1);
            int ey = intent.getIntExtra("excludeY", -1);
            int ew = intent.getIntExtra("excludeW", 0);
            int eh = intent.getIntExtra("excludeH", 0);
            scan(ex, ey, ew, eh);
        } else if ("STOP".equals(action)) {
            stopCaptureInternal();
            stopSelf();
        }
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Intent getParcelableIntent(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(key, Intent.class);
        }
        return intent.getParcelableExtra(key);
    }

    private void startForegroundCompat() {
        String channelId = "md_jibon_capture";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    channelId, "MD JIBON Screen Capture", NotificationManager.IMPORTANCE_LOW));
        }

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        Notification n = builder
                .setContentTitle("MD JIBON Scanner")
                .setContentText("Screen capture is ready")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private synchronized void startCapture(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            sendError("Screen capture permission was not granted.");
            return;
        }

        if (captureActive && projection != null && virtualDisplay != null) {
            sendCaptureState(true);
            return;
        }

        try {
            stopCaptureObjectsOnly();

            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, data);

            if (projection == null) {
                sendError("MediaProjection could not be created.");
                return;
            }

            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    captureActive = false;
                    handler.post(() -> sendCaptureState(false));
                }
            }, handler);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = dm.widthPixels;
            int height = dm.heightPixels;
            int density = dm.densityDpi;

            imageReader = ImageReader.newInstance(
                    width, height, android.graphics.PixelFormat.RGBA_8888, 3);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;

                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();
                    int rowPadding = rowStride - pixelStride * width;

                    Bitmap full = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height,
                            Bitmap.Config.ARGB_8888);
                    buffer.rewind();
                    full.copyPixelsFromBuffer(buffer);

                    Bitmap cropped = Bitmap.createBitmap(full, 0, 0, width, height);
                    full.recycle();

                    synchronized (frameLock) {
                        if (latestFrame != null && !latestFrame.isRecycled())
                            latestFrame.recycle();
                        latestFrame = cropped;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (image != null) image.close();
                }
            }, handler);

            virtualDisplay = projection.createVirtualDisplay(
                    "MD_JIBON_SCREEN",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, handler);

            captureActive = virtualDisplay != null;
            sendCaptureState(captureActive);

            if (!captureActive) sendError("Virtual screen could not be created.");
        } catch (Exception e) {
            captureActive = false;
            sendError("Capture start failed: " + String.valueOf(e.getMessage()));
        }
    }

    private void scan(int excludeX, int excludeY, int excludeW, int excludeH) {
        Bitmap frame;
        synchronized (frameLock) {
            if (latestFrame == null || latestFrame.isRecycled()) {
                sendError("No screen frame is ready yet. Wait one second and scan again.");
                return;
            }
            frame = latestFrame.copy(Bitmap.Config.ARGB_8888, false);
        }

        final Bitmap source = frame;
        executor.execute(() -> {
            Bitmap clean = source;

            // The floating button itself is part of the captured screen. Masking
            // its exact rectangle prevents a green/red overlay from becoming a
            // false candle.
            if (excludeW > 0 && excludeH > 0) {
                try {
                    clean = source.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(clean);
                    canvas.drawColor(Color.TRANSPARENT,
                            android.graphics.PorterDuff.Mode.CLEAR);

                    // Restore the original image first, then paint only the
                    // exclusion rectangle with the chart background color.
                    canvas.drawBitmap(source, 0, 0, null);
                    android.graphics.Paint p = new android.graphics.Paint();
                    p.setColor(Color.rgb(5, 10, 20));
                    canvas.drawRect(excludeX, excludeY,
                            excludeX + excludeW, excludeY + excludeH, p);
                } catch (Exception ignored) {
                    clean = source;
                }
            }

            Analyzer.Result result = Analyzer.analyze(
                    clean, getSharedPreferences("scanner_settings", MODE_PRIVATE)
                            .getString("timeframe", "1 MIN"));

            if (clean != source && !clean.isRecycled()) clean.recycle();
            if (!source.isRecycled()) source.recycle();

            String savedPath = saveScanImage(clean, result);

            Intent out = new Intent(ACTION_RESULT);
            out.setPackage(getPackageName());
            out.putExtra("signal", result.signal);
            out.putExtra("score", (float) result.confidence);
            out.putExtra("nextColor", result.nextCandleColor);
            out.putExtra("nextSize", result.nextCandleSize);
            out.putExtra("body", (float) result.nextBodyRatio);
            out.putExtra("candles", result.detectedCandles);
            out.putExtra("rules", result.evaluatedRules);
            out.putExtra("quality", (float) result.quality);
            out.putExtra("currentColor", result.currentCandleColor);
            out.putExtra("savedPath", savedPath);
            sendBroadcast(out);
        });
    }

    private String saveScanImage(Bitmap image, Analyzer.Result r) {
        try {
            java.io.File dir = new java.io.File(getFilesDir(), "scans");
            if (!dir.exists()) dir.mkdirs();
            long ts = System.currentTimeMillis();
            java.io.File png = new java.io.File(dir, "scan_" + ts + ".png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(png);
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            java.io.File meta = new java.io.File(dir, "scan_" + ts + ".txt");
            java.io.FileOutputStream m = new java.io.FileOutputStream(meta);
            String text = "Signal=" + r.signal + "\n" +
                    "Score=" + r.confidence + "\n" +
                    "Quality=" + r.quality + "\n" +
                    "Market=" + getSharedPreferences("scanner_settings", MODE_PRIVATE).getString("market", "USD/BRL (OTC)") + "\n" +
                    "Timeframe=" + getSharedPreferences("scanner_settings", MODE_PRIVATE).getString("timeframe", "1 MIN") + "\n" +
                    "Current=" + r.currentCandleColor + "\n" +
                    "Next=" + r.nextCandleColor + "\n" +
                    "Size=" + r.nextCandleSize + "\n" +
                    "Candles=" + r.detectedCandles + "\n" +
                    "Rules=" + r.evaluatedRules;
            m.write(text.getBytes("UTF-8"));
            m.close();
            return png.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void sendCaptureState(boolean active) {
        Intent i = new Intent(ACTION_CAPTURE_STATE);
        i.setPackage(getPackageName());
        i.putExtra("active", active);
        sendBroadcast(i);
    }

    private void sendError(String message) {
        Intent i = new Intent(ACTION_ERROR);
        i.setPackage(getPackageName());
        i.putExtra("message", message);
        sendBroadcast(i);
    }

    private void stopCaptureObjectsOnly() {
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) {}
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        virtualDisplay = null;
        imageReader = null;
        projection = null;

        synchronized (frameLock) {
            if (latestFrame != null && !latestFrame.isRecycled()) latestFrame.recycle();
            latestFrame = null;
        }
        captureActive = false;
    }

    private void stopCaptureInternal() {
        stopCaptureObjectsOnly();
        sendCaptureState(false);
    }

    @Override public void onDestroy() {
        stopCaptureInternal();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
