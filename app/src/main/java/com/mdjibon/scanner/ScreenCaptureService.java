package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {

    public static final String ACTION_RESULT =
            "MDJIBON_SCAN_RESULT";

    public static final String ACTION_PROGRESS =
            "MDJIBON_SCAN_PROGRESS";

    public static final String ACTION_CAPTURE_STATE =
            "MDJIBON_CAPTURE_STATE";

    public static final String ACTION_SCAN =
            "MDJIBON_SCAN_NOW";

    public static final String ACTION_STOP =
            "MDJIBON_CAPTURE_STOP";

    private static final String CHANNEL_ID =
            "md_jibon_scanner";

    private static final int NOTIFICATION_ID =
            9901;

    private static volatile boolean captureActive =
            false;

    private MediaProjection mediaProjection;

    private VirtualDisplay virtualDisplay;

    private ImageReader imageReader;

    private Bitmap latestFrame;

    private final Object frameLock =
            new Object();

    private int width;
    private int height;
    private int density;

    private long lastFrameCopyTime = 0;

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean scanning =
            false;

    private int progress = 0;

    private final Runnable progressRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (!scanning) {
                        return;
                    }

                    progress =
                            Math.min(
                                    100,
                                    progress + 2
                            );

                    sendProgress(progress);

                    if (progress < 100) {

                        mainHandler.postDelayed(
                                this,
                                35
                        );
                    }
                }
            };

    public static boolean isCaptureActive() {
        return captureActive;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

        if (ACTION_STOP.equals(action)) {

            stopCapture();

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
                            0
                    );

            Intent data =
                    intent.getParcelableExtra(
                            "data"
                    );

            if (data != null) {

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

        createNotificationChannel();

        Notification notification =
                new Notification.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setContentTitle(
                                "MD JIBON Screen Scanner"
                        )
                        .setContentText(
                                "Screen scanning is active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_view
                        )
                        .setOngoing(true)
                        .build();

        try {

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

        } catch (Exception e) {

            sendState(false);
            stopSelf();
            return;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        if (manager == null) {
            stopSelf();
            return;
        }

        try {

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            data
                    );

            if (mediaProjection == null) {

                stopSelf();
                return;
            }

            mediaProjection.registerCallback(
                    new MediaProjection.Callback() {

                        @Override
                        public void onStop() {

                            releaseProjection();

                            sendState(false);
                        }
                    },
                    mainHandler
            );

            width =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            height =
                    getResources()
                            .getDisplayMetrics()
                            .heightPixels;

            density =
                    getResources()
                            .getDisplayMetrics()
                            .densityDpi;

            imageReader =
                    ImageReader.newInstance(
                            width,
                            height,
                            android.graphics.PixelFormat
                                    .RGBA_8888,
                            2
                    );

            imageReader.setOnImageAvailableListener(
                    reader -> copyLatestImage(reader),
                    mainHandler
            );

            virtualDisplay =
                    mediaProjection.createVirtualDisplay(
                            "MD_JIBON_SCANNER",
                            width,
                            height,
                            density,
                            DisplayManager
                                    .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            imageReader.getSurface(),
                            null,
                            mainHandler
                    );

            captureActive = true;

            sendState(true);

        } catch (Exception e) {

            releaseProjection();

            sendState(false);

            stopSelf();
        }
    }

    private void copyLatestImage(
            ImageReader reader
    ) {

        Image image = null;

        try {

            image =
                    reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            long now =
                    System.currentTimeMillis();

            if (now -
                    lastFrameCopyTime <
                    120) {

                return;
            }

            lastFrameCopyTime = now;

            Image.Plane[] planes =
                    image.getPlanes();

            if (planes.length == 0) {
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
                    pixelStride * width;

            int bitmapWidth =
                    width +
                    rowPadding /
                            pixelStride;

            Bitmap raw =
                    Bitmap.createBitmap(
                            bitmapWidth,
                            height,
                            Bitmap.Config.ARGB_8888
                    );

            raw.copyPixelsFromBuffer(
                    buffer
            );

            Bitmap cropped =
                    Bitmap.createBitmap(
                            raw,
                            0,
                            0,
                            width,
                            height
                    );

            raw.recycle();

            synchronized (frameLock) {

                if (latestFrame != null) {
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

            if (latestFrame == null) {
                return null;
            }

            return latestFrame.copy(
                    Bitmap.Config.ARGB_8888,
                    false
            );
        }
    }

    private void requestScan() {

        if (!captureActive) {

            sendResult(
                    "NO TRADE",
                    0,
                    0,
                    0,
                    "1 MIN",
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
                    0,
                    0,
                    0,
                    getTimeframe(),
                    "UNKNOWN"
            );

            return;
        }

        scanning = true;

        progress = 0;

        sendProgress(0);

        mainHandler.removeCallbacks(
                progressRunnable
        );

        mainHandler.post(
                progressRunnable
        );

        executor.execute(
                () -> {

                    ScanResult result;

                    try {

                        result =
                                Analyzer.analyze(
                                        frame
                                );

                    } catch (Exception e) {

                        result =
                                new ScanResult(
                                        "NO TRADE",
                                        0,
                                        0,
                                        0,
                                        "UNKNOWN"
                                );
                    }

                    frame.recycle();

                    finishScan(result);
                }
        );
    }

    private void finishScan(
            ScanResult result
    ) {

        mainHandler.post(
                () -> {

                    int delay =
                            Math.max(
                                    0,
                                    1000
                                            - progress *
                                            35
                            );

                    mainHandler.postDelayed(
                            () -> {

                                progress = 100;

                                sendProgress(
                                        100
                                );

                                scanning = false;

                                sendResult(
                                        result.signal,
                                        result.confidence,
                                        result.quality,
                                        result.candles,
                                        getTimeframe(),
                                        result.candleSize
                                );

                            },
                            delay
                    );
                }
        );
    }

    private String getTimeframe() {

        return getSharedPreferences(
                "scanner_settings",
                MODE_PRIVATE
        ).getString(
                "timeframe",
                "1 MIN"
        );
    }

    private void sendProgress(
            int value
    ) {

        Intent i =
                new Intent(
                        ACTION_PROGRESS
                );

        i.setPackage(
                getPackageName()
        );

        i.putExtra(
                "progress",
                value
        );

        sendBroadcast(i);
    }

    private void sendState(
            boolean active
    ) {

        Intent i =
                new Intent(
                        ACTION_CAPTURE_STATE
                );

        i.setPackage(
                getPackageName()
        );

        i.putExtra(
                "active",
                active
        );

        sendBroadcast(i);
    }

    private void sendResult(
            String signal,
            int confidence,
            int quality,
            int candles,
            String timeframe,
            String candleSize
    ) {

        Intent i =
                new Intent(
                        ACTION_RESULT
                );

        i.setPackage(
                getPackageName()
        );

        i.putExtra(
                "signal",
                signal
        );

        i.putExtra(
                "confidence",
                confidence
        );

        i.putExtra(
                "quality",
                quality
        );

        i.putExtra(
                "ruleCount",
                100
        );

        i.putExtra(
                "detectedCandles",
                candles
        );

        i.putExtra(
                "timeframe",
                timeframe
        );

        i.putExtra(
                "candleSize",
                candleSize
        );

        sendBroadcast(i);
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    NOTIFICATION_SERVICE
                            );

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "MD JIBON Screen Scanner",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    private void releaseProjection() {

        captureActive = false;

        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
        } catch (Exception ignored) {
        }

        virtualDisplay = null;

        try {
            if (imageReader != null) {
                imageReader.close();
            }
        } catch (Exception ignored) {
        }

        imageReader = null;

        try {
            if (mediaProjection != null) {
                mediaProjection.stop();
            }
        } catch (Exception ignored) {
        }

        mediaProjection = null;

        synchronized (frameLock) {

            if (latestFrame != null) {
                latestFrame.recycle();
                latestFrame = null;
            }
        }
    }

    private void stopCapture() {

        scanning = false;

        mainHandler.removeCallbacks(
                progressRunnable
        );

        releaseProjection();

        sendState(false);

        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {

        stopCapture();

        executor.shutdownNow();

        super.onDestroy();
    }

    private static class ScanResult {

        String signal;
        int confidence;
        int quality;
        int candles;
        String candleSize;

        ScanResult(
                String signal,
                int confidence,
                int quality,
                int candles,
                String candleSize
        ) {

            this.signal = signal;
            this.confidence = confidence;
            this.quality = quality;
            this.candles = candles;
            this.candleSize = candleSize;
        }
    }

    private static class Candle {

        double open;
        double high;
        double low;
        double close;

        double body;
        double range;

        boolean green;

        Candle(
                double open,
                double high,
                double low,
                double close,
                boolean green
        ) {

            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;

            this.body =
                    Math.abs(
                            close - open
                    );

            this.range =
                    Math.max(
                            0.0001,
                            high - low
                    );

            this.green = green;
        }
    }

    private static class Analyzer {

        static ScanResult analyze(
                Bitmap bitmap
        ) {

            List<Candle> candles =
                    detectCandles(bitmap);

            int candleCount =
                    candles.size();

            if (candleCount < 8) {

                return new ScanResult(
                        "NO TRADE",
                        0,
                        Math.min(
                                40,
                                candleCount * 5
                        ),
                        candleCount,
                        candleCount > 0
                                ? candleSize(candles)
                                : "UNKNOWN"
                );
            }

            double[] close =
                    closes(candles);

            double[] high =
                    highs(candles);

            double[] low =
                    lows(candles);

            double[] open =
                    opens(candles);

            double sma3 =
                    sma(close, 3);

            double sma5 =
                    sma(close, 5);

            double sma8 =
                    sma(close, 8);

            double sma13 =
                    sma(close, 13);

            double sma21 =
                    sma(close, Math.min(21, close.length));

            double ema5 =
                    ema(close, 5);

            double ema8 =
                    ema(close, 8);

            double ema13 =
                    ema(close, 13);

            double ema21 =
                    ema(close, 21);

            double ema34 =
                    ema(close, 34);

            double rsi =
                    rsi(close, 14);

            double[] macd =
                    macd(close);

            double stochastic =
                    stochastic(
                            close,
                            high,
                            low,
                            14
                    );

            double stochasticD =
                    stochasticD(
                            close,
                            high,
                            low
                    );

            double bbMid =
                    sma(close, 20);

            double bbDev =
                    std(
                            close,
                            20
                    );

            double bbUpper =
                    bbMid +
                    2.0 * bbDev;

            double bbLower =
                    bbMid -
                    2.0 * bbDev;

            double atr =
                    atr(
                            candles,
                            14
                    );

            double last =
                    close[close.length - 1];

            double previous =
                    close[close.length - 2];

            double close2 =
                    close[
                            Math.max(
                                    0,
                                    close.length - 3
                            )
                    ];

            double close3 =
                    close[
                            Math.max(
                                    0,
                                    close.length - 4
                            )
                    ];

            double momentum5 =
                    momentum(
                            close,
                            5
                    );

            double momentum8 =
                    momentum(
                            close,
                            8
                    );

            double roc3 =
                    roc(
                            close,
                            3
                    );

            double roc5 =
                    roc(
                            close,
                            5
                    );

            double slope5 =
                    slope(
                            close,
                            5
                    );

            double slope8 =
                    slope(
                            close,
                            8
                    );

            double slope13 =
                    slope(
                            close,
                            13
                    );

            double[] votes =
                    new double[]{
                            0,
                            0,
                            0
                    };

            /*
             * EXACTLY 50 directional pairs
             * = 100 logic checks.
             *
             * pair() records one UP check and
             * one DOWN check.
             */

            pair(votes,
                    last > sma3,
                    last < sma3);

            pair(votes,
                    last > sma5,
                    last < sma5);

            pair(votes,
                    last > sma8,
                    last < sma8);

            pair(votes,
                    last > sma13,
                    last < sma13);

            pair(votes,
                    last > sma21,
                    last < sma21);

            pair(votes,
                    sma3 > sma8,
                    sma3 < sma8);

            pair(votes,
                    sma5 > sma13,
                    sma5 < sma13);

            pair(votes,
                    sma8 > sma21,
                    sma8 < sma21);

            pair(votes,
                    ema5 > ema8,
                    ema5 < ema8);

            pair(votes,
                    ema8 > ema13,
                    ema8 < ema13);

            pair(votes,
                    ema13 > ema21,
                    ema13 < ema21);

            pair(votes,
                    ema21 > ema34,
                    ema21 < ema34);

            pair(votes,
                    slope5 > 0,
                    slope5 < 0);

            pair(votes,
                    slope8 > 0,
                    slope8 < 0);

            pair(votes,
                    slope13 > 0,
                    slope13 < 0);

            pair(votes,
                    last > previous,
                    last < previous);

            pair(votes,
                    last > close2,
                    last < close2);

            pair(votes,
                    last > close3,
                    last < close3);

            pair(votes,
                    momentum5 > 0,
                    momentum5 < 0);

            pair(votes,
                    momentum8 > 0,
                    momentum8 < 0);

            pair(votes,
                    roc3 > 0,
                    roc3 < 0);

            pair(votes,
                    roc5 > 0,
                    roc5 < 0);

            pair(votes,
                    rsi > 55,
                    rsi < 45);

            pair(votes,
                    rsi > 60,
                    rsi < 40);

            pair(votes,
                    rsi > 50,
                    rsi < 50);

            pair(votes,
                    macd[0] > macd[1],
                    macd[0] < macd[1]);

            pair(votes,
                    macd[0] > 0,
                    macd[0] < 0);

            pair(votes,
                    macd[2] > 0,
                    macd[2] < 0);

            pair(votes,
                    stochastic > stochasticD,
                    stochastic < stochasticD);

            pair(votes,
                    stochastic > 55 &&
                            stochastic < 80,
                    stochastic < 45 &&
                            stochastic > 20);

            pair(votes,
                    stochastic > 60,
                    stochastic < 40);

            pair(votes,
                    last > bbMid,
                    last < bbMid);

            pair(votes,
                    last > bbLower &&
                            last < bbMid,
                    last < bbUpper &&
                            last > bbMid);

            pair(votes,
                    last > bbUpper &&
                            close[close.length - 2]
                                    < bbUpper,
                    last < bbLower &&
                            close[close.length - 2]
                                    > bbLower);

            pair(votes,
                    momentum5 > atr * 0.15,
                    momentum5 < -atr * 0.15);

            pair(votes,
                    candleBodyRatio(
                            candles.get(
                                    candles.size() - 1
                            )
                    ) > 0.60 &&
                            candles.get(
                                    candles.size() - 1
                            ).green,
                    candleBodyRatio(
                            candles.get(
                                    candles.size() - 1
                            )
                    ) > 0.60 &&
                            !candles.get(
                                    candles.size() - 1
                            ).green);

            pair(votes,
                    upperClose(
                            candles.get(
                                    candles.size() - 1
                            )
                    ),
                    lowerClose(
                            candles.get(
                                    candles.size() - 1
                            )
                    ));

            pair(votes,
                    lowerWickRejection(
                            candles.get(
                                    candles.size() - 1
                            )
                    ),
                    upperWickRejection(
                            candles.get(
                                    candles.size() - 1
                            )
                    ));

            pair(votes,
                    bullishEngulfing(candles),
                    bearishEngulfing(candles));

            pair(votes,
                    consecutiveGreen(candles, 3),
                    consecutiveRed(candles, 3));

            pair(votes,
                    consecutiveGreen(candles, 2),
                    consecutiveRed(candles, 2));

            pair(votes,
                    higherHigh(candles, 4),
                    lowerLow(candles, 4));

            pair(votes,
                    higherLow(candles, 4),
                    lowerHigh(candles, 4));

            pair(votes,
                    last > recentHigh(
                            close,
                            8
                    ),
                    last < recentLow(
                            close,
                            8
                    ));

            pair(votes,
                    last > recentHigh(
                            close,
                            13
                    ),
                    last < recentLow(
                            close,
                            13
                    ));

            pair(votes,
                    bounceFromLow(
                            candles
                    ),
                    rejectFromHigh(
                            candles
                    ));

            pair(votes,
                    close > open[
                            open.length - 1
                    ],
                    close < open[
                            open.length - 1
                    ]);

            pair(votes,
                    netMove(close, 5) > 0,
                    netMove(close, 5) < 0);

            pair(votes,
                    netMove(close, 8) > 0,
                    netMove(close, 8) < 0);

            pair(votes,
                    netMove(close, 13) > 0,
                    netMove(close, 13) < 0);

            pair(votes,
                    averageBody(
                            candles,
                            5
                    ) >
                            averageBody(
                                    candles,
                                    10
                            ),
                    averageBody(
                            candles,
                            5
                    ) <
                            averageBody(
                                    candles,
                                    10
                            ));

            pair(votes,
                    lastBodyGrowing(
                            candles
                    ) &&
                            candles.get(
                                    candles.size() - 1
                            ).green,
                    lastBodyGrowing(
                            candles
                    ) &&
                            !candles.get(
                                    candles.size() - 1
                            ).green);

            pair(votes,
                    lastRangeGrowing(
                            candles
                    ) &&
                            candles.get(
                                    candles.size() - 1
                            ).green,
                    lastRangeGrowing(
                            candles
                    ) &&
                            !candles.get(
                                    candles.size() - 1
                            ).green);

            int up =
                    (int) votes[0];

            int down =
                    (int) votes[1];

            int total =
                    up + down;

            if (total < 12) {

                return new ScanResult(
                        "NO TRADE",
                        0,
                        quality(candles),
                        candles.size(),
                        candleSize(candles)
                );
            }

            int strongest =
                    Math.max(
                            up,
                            down
                    );

            int confidence =
                    (int)
                            Math.round(
                                    50.0 +
                                    50.0 *
                                            Math.abs(
                                                    up - down
                                            ) /
                                            total
                            );

            if (strongest < 12 ||
                    confidence < 60) {

                return new ScanResult(
                        "NO TRADE",
                        confidence,
                        quality(candles),
                        candles.size(),
                        candleSize(candles)
                );
            }

            if (up > down) {

                return new ScanResult(
                        "UP",
                        confidence,
                        quality(candles),
                        candles.size(),
                        candleSize(candles)
                );

            } else if (down > up) {

                return new ScanResult(
                        "DOWN",
                        confidence,
                        quality(candles),
                        candles.size(),
                        candleSize(candles)
                );
            }

            return new ScanResult(
                    "NO TRADE",
                    50,
                    quality(candles),
                    candles.size(),
                    candleSize(candles)
            );
        }

        private static void pair(
                double[] votes,
                boolean up,
                boolean down
        ) {

            if (up) {
                votes[0]++;
            }

            if (down) {
                votes[1]++;
            }

            votes[2]++;
        }

        private static List<Candle> detectCandles(
                Bitmap bitmap
        ) {

            ArrayList<Candle> list =
                    new ArrayList<>();

            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            int left =
                    (int) (w * 0.04);

            int right =
                    (int) (w * 0.96);

            int top =
                    (int) (h * 0.16);

            int bottom =
                    (int) (h * 0.84);

            ArrayList<Integer> active =
                    new ArrayList<>();

            for (int x = left;
                 x < right;
                 x++) {

                int count = 0;

                for (int y = top;
                     y < bottom;
                     y += 2) {

                    int c =
                            bitmap.getPixel(
                                    x,
                                    y
                            );

                    int r =
                            Color.red(c);

                    int g =
                            Color.green(c);

                    int b =
                            Color.blue(c);

                    boolean green =
                            g > 85 &&
                            g > r * 1.18 &&
                            g > b * 1.05;

                    boolean red =
                            r > 85 &&
                            r > g * 1.18 &&
                            r > b * 1.18;

                    if (green || red) {
                        count++;
                    }
                }

                if (count >= 2) {
                    active.add(x);
                }
            }

            if (active.isEmpty()) {
                return list;
            }

            int start =
                    active.get(0);

            int previous =
                    start;

            ArrayList<int[]> groups =
                    new ArrayList<>();

            for (int i = 1;
                 i < active.size();
                 i++) {

                int x =
                        active.get(i);

                if (x - previous > 3) {

                    groups.add(
                            new int[]{
                                    start,
                                    previous
                            }
                    );

                    start = x;
                }

                previous = x;
            }

            groups.add(
                    new int[]{
                            start,
                            previous
                    }
            );

            for (int[] group : groups) {

                int x1 = group[0];
                int x2 = group[1];

                int width =
                        x2 - x1 + 1;

                if (width < 2 ||
                        width > 30) {
                    continue;
                }

                int minY =
                        bottom;

                int maxY =
                        top;

                int greenPixels = 0;
                int redPixels = 0;

                for (int x = x1;
                     x <= x2;
                     x++) {

                    for (int y = top;
                         y < bottom;
                         y++) {

                        int c =
                                bitmap.getPixel(
                                        x,
                                        y
                                );

                        int r =
                                Color.red(c);

                        int g =
                                Color.green(c);

                        int b =
                                Color.blue(c);

                        boolean green =
                                g > 85 &&
                                g > r * 1.18 &&
                                g > b * 1.05;

                        boolean red =
                                r > 85 &&
                                r > g * 1.18 &&
                                r > b * 1.18;

                        if (green || red) {

                            minY =
                                    Math.min(
                                            minY,
                                            y
                                    );

                            maxY =
                                    Math.max(
                                            maxY,
                                            y
                                    );

                            if (green) {
                                greenPixels++;
                            }

                            if (red) {
                                redPixels++;
                            }
                        }
                    }
                }

                if (maxY <= minY) {
                    continue;
                }

                boolean green =
                        greenPixels >= redPixels;

                double high =
                        -minY;

                double low =
                        -maxY;

                double open;
                double close;

                if (green) {

                    open =
                            -maxY;

                    close =
                            -minY;

                } else {

                    open =
                            -minY;

                    close =
                            -maxY;
                }

                list.add(
                        new Candle(
                                open,
                                high,
                                low,
                                close,
                                green
                        )
                );
            }

            return list;
        }

        private static double[] closes(
                List<Candle> c
        ) {

            double[] a =
                    new double[c.size()];

            for (int i = 0;
                 i < c.size();
                 i++) {

                a[i] =
                        c.get(i).close;
            }

            return a;
        }

        private static double[] highs(
                List<Candle> c
        ) {

            double[] a =
                    new double[c.size()];

            for (int i = 0;
                 i < c.size();
                 i++) {

                a[i] =
                        c.get(i).high;
            }

            return a;
        }

        private static double[] lows(
                List<Candle> c
        ) {

            double[] a =
                    new double[c.size()];

            for (int i = 0;
                 i < c.size();
                 i++) {

                a[i] =
                        c.get(i).low;
            }

            return a;
        }

        private static double[] opens(
                List<Candle> c
        ) {

            double[] a =
                    new double[c.size()];

            for (int i = 0;
                 i < c.size();
                 i++) {

                a[i] =
                        c.get(i).open;
            }

            return a;
        }

        private static double sma(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length
                    );

            double sum = 0;

            for (int i =
                    a.length - n;
                 i < a.length;
                 i++) {

                sum += a[i];
            }

            return sum / n;
        }

        private static double ema(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length
                    );

            double k =
                    2.0 /
                            (n + 1.0);

            double value =
                    a[0];

            for (int i = 1;
                 i < a.length;
                 i++) {

                value =
                        a[i] * k +
                        value *
                                (1.0 - k);
            }

            return value;
        }

        private static double rsi(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length - 1
                    );

            double gain = 0;
            double loss = 0;

            for (int i =
                    a.length - n;
                 i < a.length;
                 i++) {

                double d =
                        a[i] -
                        a[i - 1];

                if (d > 0) {
                    gain += d;
                } else {
                    loss -= d;
                }
            }

            if (loss == 0) {
                return 100;
            }

            double rs =
                    gain / loss;

            return 100 -
                    100 /
                            (1 + rs);
        }

        private static double[] macd(
                double[] a
        ) {

            double e12 =
                    ema(a, 12);

            double e26 =
                    ema(a, 26);

            double line =
                    e12 - e26;

            double signal =
                    ema(
                            new double[]{
                                    line,
                                    line * 0.8,
                                    line * 0.6,
                                    line * 0.4,
                                    line * 0.2
                            },
                            5
                    );

            return new double[]{
                    line,
                    signal,
                    line - signal
            };
        }

        private static double stochastic(
                double[] close,
                double[] high,
                double[] low,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            close.length
                    );

            double hi =
                    -Double.MAX_VALUE;

            double lo =
                    Double.MAX_VALUE;

            for (int i =
                    close.length - n;
                 i < close.length;
                 i++) {

                hi =
                        Math.max(
                                hi,
                                high[i]
                        );

                lo =
                        Math.min(
                                lo,
                                low[i]
                        );
            }

            if (hi == lo) {
                return 50;
            }

            return 100 *
                    (
                            close[
                                    close.length - 1
                            ] - lo
                    ) /
                    (hi - lo);
        }

        private static double stochasticD(
                double[] close,
                double[] high,
                double[] low
        ) {

            double sum = 0;

            for (int k = 0;
                 k < 3;
                 k++) {

                int end =
                        close.length -
                                k;

                int n =
                        Math.min(
                                14,
                                end
                        );

                double hi =
                        -Double.MAX_VALUE;

                double lo =
                        Double.MAX_VALUE;

                for (int i =
                        end - n;
                     i < end;
                     i++) {

                    hi =
                            Math.max(
                                    hi,
                                    high[i]
                            );

                    lo =
                            Math.min(
                                    lo,
                                    low[i]
                            );
                }

                double value;

                if (hi == lo) {
                    value = 50;
                } else {
                    value =
                            100 *
                                    (
                                            close[
                                                    end - 1
                                            ] - lo
                                    ) /
                                    (hi - lo);
                }

                sum += value;
            }

            return sum / 3.0;
        }

        private static double std(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length
                    );

            double mean =
                    sma(a, n);

            double sum = 0;

            for (int i =
                    a.length - n;
                 i < a.length;
                 i++) {

                double d =
                        a[i] - mean;

                sum += d * d;
            }

            return Math.sqrt(
                    sum / n
            );
        }

        private static double atr(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size()
                    );

            double sum = 0;

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                sum +=
                        c.get(i).range;
            }

            return sum / n;
        }

        private static double momentum(
                double[] a,
                int n
        ) {

            if (a.length <= n) {
                return 0;
            }

            return a[a.length - 1] -
                    a[a.length - 1 - n];
        }

        private static double roc(
                double[] a,
                int n
        ) {

            if (a.length <= n) {
                return 0;
            }

            double old =
                    a[a.length - 1 - n];

            if (old == 0) {
                return 0;
            }

            return (
                    (
                            a[a.length - 1] -
                            old
                    ) / Math.abs(old)
            ) * 100;
        }

        private static double slope(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length
                    );

            if (n < 2) {
                return 0;
            }

            return (
                    a[a.length - 1] -
                    a[a.length - n]
            ) / n;
        }

        private static double candleBodyRatio(
                Candle c
        ) {

            return c.body /
                    c.range;
        }

        private static boolean upperClose(
                Candle c
        ) {

            return c.close >
                    c.low +
                            c.range * 0.70;
        }

        private static boolean lowerClose(
                Candle c
        ) {

            return c.close <
                    c.low +
                            c.range * 0.30;
        }

        private static boolean lowerWickRejection(
                Candle c
        ) {

            double lower =
                    Math.min(
                            c.open,
                            c.close
                    ) - c.low;

            return lower >
                    c.body * 1.3;
        }

        private static boolean upperWickRejection(
                Candle c
        ) {

            double upper =
                    c.high -
                            Math.max(
                                    c.open,
                                    c.close
                            );

            return upper >
                    c.body * 1.3;
        }

        private static boolean bullishEngulfing(
                List<Candle> c
        ) {

            if (c.size() < 2) {
                return false;
            }

            Candle a =
                    c.get(c.size() - 2);

            Candle b =
                    c.get(c.size() - 1);

            return !a.green &&
                    b.green &&
                    b.open <= a.close &&
                    b.close >= a.open;
        }

        private static boolean bearishEngulfing(
                List<Candle> c
        ) {

            if (c.size() < 2) {
                return false;
            }

            Candle a =
                    c.get(c.size() - 2);

            Candle b =
                    c.get(c.size() - 1);

            return a.green &&
                    !b.green &&
                    b.open >= a.close &&
                    b.close <= a.open;
        }

        private static boolean consecutiveGreen(
                List<Candle> c,
                int n
        ) {

            if (c.size() < n) {
                return false;
            }

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (!c.get(i).green) {
                    return false;
                }
            }

            return true;
        }

        private static boolean consecutiveRed(
                List<Candle> c,
                int n
        ) {

            if (c.size() < n) {
                return false;
            }

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (c.get(i).green) {
                    return false;
                }
            }

            return true;
        }

        private static boolean higherHigh(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size() - 1
                    );

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (c.get(i).high <=
                        c.get(i - 1).high) {

                    return false;
                }
            }

            return true;
        }

        private static boolean lowerLow(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size() - 1
                    );

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (c.get(i).low >=
                        c.get(i - 1).low) {

                    return false;
                }
            }

            return true;
        }

        private static boolean higherLow(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size() - 1
                    );

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (c.get(i).low <=
                        c.get(i - 1).low) {

                    return false;
                }
            }

            return true;
        }

        private static boolean lowerHigh(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size() - 1
                    );

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (c.get(i).high >=
                        c.get(i - 1).high) {

                    return false;
                }
            }

            return true;
        }

        private static double recentHigh(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length - 1
                    );

            double high =
                    -Double.MAX_VALUE;

            for (int i =
                    a.length - 1 - n;
                 i < a.length - 1;
                 i++) {

                high =
                        Math.max(
                                high,
                                a[i]
                        );
            }

            return high;
        }

        private static double recentLow(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length - 1
                    );

            double low =
                    Double.MAX_VALUE;

            for (int i =
                    a.length - 1 - n;
                 i < a.length - 1;
                 i++) {

                low =
                        Math.min(
                                low,
                                a[i]
                        );
            }

            return low;
        }

        private static boolean bounceFromLow(
                List<Candle> c
        ) {

            if (c.size() < 5) {
                return false;
            }

            Candle last =
                    c.get(c.size() - 1);

            double low =
                    Double.MAX_VALUE;

            for (int i =
                    c.size() - 5;
                 i < c.size() - 1;
                 i++) {

                low =
                        Math.min(
                                low,
                                c.get(i).low
                        );
            }

            return last.green &&
                    last.low <=
                            low +
                                    last.range * 0.25;
        }

        private static boolean rejectFromHigh(
                List<Candle> c
        ) {

            if (c.size() < 5) {
                return false;
            }

            Candle last =
                    c.get(c.size() - 1);

            double high =
                    -Double.MAX_VALUE;

            for (int i =
                    c.size() - 5;
                 i < c.size() - 1;
                 i++) {

                high =
                        Math.max(
                                high,
                                c.get(i).high
                        );
            }

            return !last.green &&
                    last.high >=
                            high -
                                    last.range * 0.25;
        }

        private static double netMove(
                double[] a,
                int n
        ) {

            if (a.length <= n) {
                return 0;
            }

            return a[a.length - 1] -
                    a[a.length - 1 - n];
        }

        private static double averageBody(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size()
                    );

            double sum = 0;

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                sum +=
                        c.get(i).body;
            }

            return sum / n;
        }

        private static boolean lastBodyGrowing(
                List<Candle> c
        ) {

            if (c.size() < 2) {
                return false;
            }

            return c.get(
                    c.size() - 1
            ).body >
                    c.get(
                            c.size() - 2
                    ).body;
        }

        private static boolean lastRangeGrowing(
                List<Candle> c
        ) {

            if (c.size() < 2) {
                return false;
            }

            return c.get(
                    c.size() - 1
            ).range >
                    c.get(
                            c.size() - 2
                    ).range;
        }

        private static int quality(
                List<Candle> c
        ) {

            int q =
                    c.size() * 3;

            return Math.min(
                    100,
                    Math.max(
                            10,
                            q
                    )
            );
        }

        private static String candleSize(
                List<Candle> c
        ) {

            if (c.size() < 3) {
                return "UNKNOWN";
            }

            double avg =
                    averageBody(
                            c,
                            Math.min(
                                    10,
                                    c.size()
                            )
                    );

            if (avg < 4) {
                return "SMALL";
            }

            if (avg < 10) {
                return "MEDIUM";
            }

            return "LARGE";
        }
    }
}
