package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Display;
import android.view.WindowManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScreenCaptureService extends Service {

    public static final String ACTION_RESULT =
            "MDJIBON_SCAN_RESULT";

    public static final String ACTION_UI =
            "MDJIBON_SCAN_UI";

    private static final String CHANNEL_ID =
            "mdjibon_scanner_channel";

    private static final int NOTIFICATION_ID =
            9911;

    private static boolean captureActive =
            false;

    private MediaProjection projection;

    private VirtualDisplay virtualDisplay;

    private ImageReader imageReader;

    private Bitmap latestFrame;

    private final Handler handler =
            new Handler();

    private final Object frameLock =
            new Object();

    public static boolean isCaptureActive() {
        return captureActive;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        Notification notification =
                buildNotification();

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

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "MD JIBON Screen Scanner",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Screen scanner is running"
            );

            NotificationManager nm =
                    (NotificationManager)
                            getSystemService(
                                    NOTIFICATION_SERVICE
                            );

            nm.createNotificationChannel(
                    channel
            );
        }
    }

    private Notification buildNotification() {

        if (Build.VERSION.SDK_INT >= 26) {

            return new Notification.Builder(
                    this,
                    CHANNEL_ID
            )
                    .setContentTitle(
                            "MD JIBON Scanner"
                    )
                    .setContentText(
                            "Screen scanner active"
                    )
                    .setSmallIcon(
                            android.R.drawable.ic_menu_search
                    )
                    .setOngoing(true)
                    .build();

        } else {

            return new Notification.Builder(this)
                    .setContentTitle(
                            "MD JIBON Scanner"
                    )
                    .setContentText(
                            "Screen scanner active"
                    )
                    .setSmallIcon(
                            android.R.drawable.ic_menu_search
                    )
                    .setOngoing(true)
                    .build();
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        if ("SCAN_NOW".equals(
                intent.getAction()
        )) {

            scanNow();

            return START_NOT_STICKY;
        }

        int code =
                intent.getIntExtra(
                        "code",
                        -1
                );

        Intent data =
                intent.getParcelableExtra(
                        "data"
                );

        if (code != -1 && data != null) {

            startProjection(
                    code,
                    data
            );
        }

        return START_NOT_STICKY;
    }

    private void startProjection(
            int resultCode,
            Intent data
    ) {

        if (captureActive) {
            return;
        }

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
                    "MediaProjection পাওয়া যায়নি"
            );
            return;
        }

        projection.registerCallback(
                new MediaProjection.Callback() {

                    @Override
                    public void onStop() {

                        captureActive = false;

                        synchronized (frameLock) {

                            if (latestFrame != null) {

                                latestFrame.recycle();
                                latestFrame = null;
                            }
                        }

                        releaseDisplay();
                    }
                },
                handler
        );

        WindowManager wm =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        Display display =
                wm.getDefaultDisplay();

        android.util.DisplayMetrics metrics =
                new android.util.DisplayMetrics();

        display.getRealMetrics(metrics);

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
                        2
                );

        imageReader.setOnImageAvailableListener(
                reader -> {

                    Image image =
                            null;

                    try {

                        image =
                                reader.acquireLatestImage();

                        if (image == null) {
                            return;
                        }

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

                        Bitmap bitmap =
                                Bitmap.createBitmap(
                                        width +
                                                rowPadding /
                                                        pixelStride,
                                        height,
                                        Bitmap.Config.ARGB_8888
                                );

                        buffer.rewind();

                        bitmap.copyPixelsFromBuffer(
                                buffer
                        );

                        Bitmap cropped =
                                Bitmap.createBitmap(
                                        bitmap,
                                        0,
                                        0,
                                        width,
                                        height
                                );

                        bitmap.recycle();

                        synchronized (frameLock) {

                            if (latestFrame != null) {
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
                        "MDJIBON_SCANNER",
                        width,
                        height,
                        density,
                        DisplayManager
                                .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        handler
                );

        captureActive = true;

        sendUI(
                "READY",
                100
        );
    }

    private void scanNow() {

        if (!captureActive) {

            sendError(
                    "আগে Screen Capture চালু করুন"
            );

            return;
        }

        Bitmap frame;

        synchronized (frameLock) {

            if (latestFrame == null) {

                sendError(
                        "এখনও screen frame পাওয়া যায়নি"
                );

                return;
            }

            frame =
                    latestFrame.copy(
                            Bitmap.Config.ARGB_8888,
                            false
                    );
        }

        sendUI(
                "SCANNING",
                0
        );

        new Thread(
                () -> {

                    final long start =
                            System.currentTimeMillis();

                    ChartResult chart =
                            ChartDetector.detect(
                                    frame
                            );

                    AnalysisResult result =
                            RuleEngine.analyze(
                                    chart.candles
                            );

                    frame.recycle();

                    long elapsed =
                            System.currentTimeMillis()
                                    - start;

                    long minimum =
                            1800;

                    long delay =
                            Math.max(
                                    0,
                                    minimum - elapsed
                            );

                    handler.postDelayed(
                            () -> {

                                sendUI(
                                        "RESULT",
                                        100,
                                        result.signal,
                                        result.confidence
                                );

                                sendResult(
                                        result.signal,
                                        result.confidence,
                                        chart.quality,
                                        result.ruleCount,
                                        chart.candles.size()
                                );

                            },
                            delay
                    );

                }
        ).start();

        animateProgress();
    }

    private void animateProgress() {

        final long start =
                System.currentTimeMillis();

        Runnable r =
                new Runnable() {

                    @Override
                    public void run() {

                        if (!captureActive) {
                            return;
                        }

                        long elapsed =
                                System.currentTimeMillis()
                                        - start;

                        int progress =
                                (int)
                                        Math.min(
                                                99,
                                                elapsed *
                                                        100 /
                                                        1800
                                        );

                        sendUI(
                                "SCANNING",
                                progress
                        );

                        if (progress < 99) {

                            handler.postDelayed(
                                    this,
                                    45
                            );
                        }
                    }
                };

        handler.post(r);
    }

    private void sendUI(
            String state,
            int progress
    ) {

        Intent i =
                new Intent(ACTION_UI);

        i.setPackage(
                getPackageName()
        );

        i.putExtra(
                "state",
                state
        );

        i.putExtra(
                "progress",
                progress
        );

        sendBroadcast(i);
    }

    private void sendUI(
            String state,
            int progress,
            String signal,
            int confidence
    ) {

        Intent i =
                new Intent(ACTION_UI);

        i.setPackage(
                getPackageName()
        );

        i.putExtra(
                "state",
                state
        );

        i.putExtra(
                "progress",
                progress
        );

        i.putExtra(
                "signal",
                signal
        );

        i.putExtra(
                "confidence",
                confidence
        );

        sendBroadcast(i);
    }

    private void sendError(
            String message
    ) {

        Intent i =
                new Intent(ACTION_UI);

        i.setPackage(
                getPackageName()
        );

        i.putExtra(
                "state",
                "ERROR"
        );

        i.putExtra(
                "message",
                message
        );

        sendBroadcast(i);
    }

    private void sendResult(
            String signal,
            int confidence,
            int quality,
            int rules,
            int candles
    ) {

        Intent result =
                new Intent(ACTION_RESULT);

        result.setPackage(
                getPackageName()
        );

        result.putExtra(
                "signal",
                signal
        );

        result.putExtra(
                "confidence",
                confidence
        );

        result.putExtra(
                "quality",
                quality
        );

        result.putExtra(
                "ruleCount",
                rules
        );

        result.putExtra(
                "detectedCandles",
                candles
        );

        sendBroadcast(result);
    }

    private void releaseDisplay() {

        if (virtualDisplay != null) {

            try {
                virtualDisplay.release();
            } catch (Exception ignored) {
            }

            virtualDisplay = null;
        }

        if (imageReader != null) {

            try {
                imageReader.close();
            } catch (Exception ignored) {
            }

            imageReader = null;
        }
    }

    @Override
    public void onDestroy() {

        captureActive = false;

        releaseDisplay();

        if (projection != null) {

            try {
                projection.stop();
            } catch (Exception ignored) {
            }

            projection = null;
        }

        synchronized (frameLock) {

            if (latestFrame != null) {

                latestFrame.recycle();
                latestFrame = null;
            }
        }

        super.onDestroy();
    }

    public static class Candle {

        public double open;
        public double high;
        public double low;
        public double close;

        public Candle(
                double o,
                double h,
                double l,
                double c
        ) {

            open = o;
            high = h;
            low = l;
            close = c;
        }

        public boolean bullish() {
            return close > open;
        }

        public boolean bearish() {
            return close < open;
        }

        public double range() {
            return Math.max(
                    0.000001,
                    high - low
            );
        }

        public double body() {
            return Math.abs(
                    close - open
            );
        }
    }

    public static class ChartResult {

        public final List<Candle> candles;
        public final int quality;

        public ChartResult(
                List<Candle> c,
                int q
        ) {

            candles = c;
            quality = q;
        }
    }

    public static class ChartDetector {

        public static ChartResult detect(
                Bitmap bitmap
        ) {

            List<Candle> list =
                    new ArrayList<>();

            if (bitmap == null) {
                return new ChartResult(
                        list,
                        0
                );
            }

            int w =
                    bitmap.getWidth();

            int h =
                    bitmap.getHeight();

            int left =
                    (int) (w * 0.03);

            int right =
                    (int) (w * 0.97);

            int top =
                    (int) (h * 0.12);

            int bottom =
                    (int) (h * 0.78);

            int chartH =
                    bottom - top;

            ArrayList<Integer> columns =
                    new ArrayList<>();

            int threshold =
                    Math.max(
                            2,
                            chartH / 180
                    );

            for (int x = left;
                 x < right;
                 x++) {

                int count = 0;

                for (int y = top;
                     y < bottom;
                     y += 2) {

                    int color =
                            bitmap.getPixel(
                                    x,
                                    y
                            );

                    if (isGreen(color) ||
                            isRed(color)) {

                        count++;
                    }
                }

                if (count >= threshold) {
                    columns.add(x);
                }
            }

            if (columns.isEmpty()) {

                return new ChartResult(
                        list,
                        5
                );
            }

            ArrayList<int[]> groups =
                    new ArrayList<>();

            int start =
                    columns.get(0);

            int previous =
                    start;

            for (int i = 1;
                 i < columns.size();
                 i++) {

                int x =
                        columns.get(i);

                if (x - previous > 8) {

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

            double priceTop =
                    1.0;

            double priceBottom =
                    0.0;

            for (int[] g : groups) {

                int x1 = g[0];
                int x2 = g[1];

                int width =
                        x2 - x1 + 1;

                if (width < 2 ||
                        width > 45) {
                    continue;
                }

                int highY = bottom;
                int lowY = top;

                int green = 0;
                int red = 0;

                for (int x = x1;
                     x <= x2;
                     x++) {

                    for (int y = top;
                         y < bottom;
                         y += 2) {

                        int color =
                                bitmap.getPixel(
                                        x,
                                        y
                                );

                        if (isGreen(color)) {

                            green++;

                            highY =
                                    Math.min(
                                            highY,
                                            y
                                    );

                            lowY =
                                    Math.max(
                                            lowY,
                                            y
                                    );

                        } else if (isRed(color)) {

                            red++;

                            highY =
                                    Math.min(
                                            highY,
                                            y
                                    );

                            lowY =
                                    Math.max(
                                            lowY,
                                            y
                                    );
                        }
                    }
                }

                if (green + red < 5) {
                    continue;
                }

                boolean isBull =
                        green >= red;

                double high =
                        priceTop +
                        (bottom - highY) /
                                (double) chartH;

                double low =
                        priceBottom +
                        (bottom - lowY) /
                                (double) chartH;

                if (high <= low) {
                    continue;
                }

                double open;
                double close;

                if (isBull) {

                    close =
                            high -
                            (high - low) *
                                    0.25;

                    open =
                            high -
                            (high - low) *
                                    0.75;

                } else {

                    open =
                            high -
                            (high - low) *
                                    0.25;

                    close =
                            high -
                            (high - low) *
                                    0.75;
                }

                list.add(
                        new Candle(
                                open,
                                high,
                                low,
                                close
                        )
                );
            }

            if (list.size() > 80) {

                list =
                        new ArrayList<>(
                                list.subList(
                                        list.size() - 80,
                                        list.size()
                                )
                        );
            }

            int quality =
                    Math.min(
                            100,
                            list.size() * 3
                    );

            if (list.size() >= 20) {
                quality += 20;
            }

            quality =
                    Math.min(
                            100,
                            quality
                    );

            return new ChartResult(
                    list,
                    quality
            );
        }

        private static boolean isGreen(
                int c
        ) {

            int r =
                    Color.red(c);

            int g =
                    Color.green(c);

            int b =
                    Color.blue(c);

            return g > 95 &&
                    g > r * 1.25 &&
                    g > b * 1.05;
        }

        private static boolean isRed(
                int c
        ) {

            int r =
                    Color.red(c);

            int g =
                    Color.green(c);

            int b =
                    Color.blue(c);

            return r > 100 &&
                    r > g * 1.25 &&
                    r > b * 1.10;
        }
    }

    public static class AnalysisResult {

        String signal;
        int confidence;
        int ruleCount;

        AnalysisResult(
                String s,
                int c,
                int r
        ) {

            signal = s;
            confidence = c;
            ruleCount = r;
        }
    }

    public static class RuleEngine {

        public static AnalysisResult analyze(
                List<Candle> c
        ) {

            if (c == null ||
                    c.size() < 12) {

                return new AnalysisResult(
                        "NO TRADE",
                        0,
                        100
                );
            }

            double[] close =
                    closes(c);

            double[] high =
                    highs(c);

            double[] low =
                    lows(c);

            double[] open =
                    opens(c);

            double sma5 =
                    sma(close, 5);

            double sma10 =
                    sma(close, 10);

            double sma20 =
                    sma(close, Math.min(
                            20,
                            close.length
                    ));

            double sma50 =
                    sma(close, Math.min(
                            50,
                            close.length
                    ));

            double ema5 =
                    ema(close, 5);

            double ema10 =
                    ema(close, 10);

            double ema20 =
                    ema(close, 20);

            double ema50 =
                    ema(close, 50);

            double rsi =
                    rsi(close, 14);

            double rsiPrev =
                    rsiPrev(close, 14);

            double[] macd =
                    macd(close);

            double[] stoch =
                    stochastic(
                            high,
                            low,
                            close,
                            14
                    );

            double roc =
                    roc(close, 5);

            double momentum =
                    momentum(
                            close,
                            5
                    );

            double atr =
                    atr(
                            high,
                            low,
                            close,
                            14
                    );

            double atrPrev =
                    atr(
                            high,
                            low,
                            close,
                            Math.min(
                                    10,
                                    close.length
                            )
                    );

            double avgRange =
                    averageRange(c, 10);

            double avgBody =
                    averageBody(c, 10);

            Candle last =
                    c.get(c.size() - 1);

            Candle prev =
                    c.get(c.size() - 2);

            Candle p3 =
                    c.get(c.size() - 3);

            boolean[] bull =
                    new boolean[100];

            boolean[] bear =
                    new boolean[100];

            /*
             * 1-20 TREND
             */

            bull[0] =
                    last.close > sma5;

            bear[0] =
                    last.close < sma5;

            bull[1] =
                    last.close > sma10;

            bear[1] =
                    last.close < sma10;

            bull[2] =
                    last.close > sma20;

            bear[2] =
                    last.close < sma20;

            bull[3] =
                    last.close > sma50;

            bear[3] =
                    last.close < sma50;

            bull[4] =
                    ema5 > ema10;

            bear[4] =
                    ema5 < ema10;

            bull[5] =
                    ema10 > ema20;

            bear[5] =
                    ema10 < ema20;

            bull[6] =
                    ema20 > ema50;

            bear[6] =
                    ema20 < ema50;

            bull[7] =
                    sma5 > sma10;

            bear[7] =
                    sma5 < sma10;

            bull[8] =
                    sma10 > sma20;

            bear[8] =
                    sma10 < sma20;

            bull[9] =
                    sma20 > sma50;

            bear[9] =
                    sma20 < sma50;

            bull[10] =
                    last.close >
                            c.get(
                                    Math.max(
                                            0,
                                            c.size() - 4
                                    )
                            ).close;

            bear[10] =
                    last.close <
                            c.get(
                                    Math.max(
                                            0,
                                            c.size() - 4
                                    )
                            ).close;

            bull[11] =
                    higherLows(c);

            bear[11] =
                    lowerLows(c);

            bull[12] =
                    higherHighs(c);

            bear[12] =
                    lowerHighs(c);

            bull[13] =
                    ema5 >
                            ema(
                                    close,
                                    5,
                                    close.length - 2
                            );

            bear[13] =
                    ema5 <
                            ema(
                                    close,
                                    5,
                                    close.length - 2
                            );

            bull[14] =
                    ema10 >
                            ema(
                                    close,
                                    10,
                                    close.length - 2
                            );

            bear[14] =
                    ema10 <
                            ema(
                                    close,
                                    10,
                                    close.length - 2
                            );

            bull[15] =
                    ema20 >
                            ema(
                                    close,
                                    20,
                                    close.length - 2
                            );

            bear[15] =
                    ema20 <
                            ema(
                                    close,
                                    20,
                                    close.length - 2
                            );

            bull[16] =
                    smaSlope(
                            close,
                            20
                    ) > 0;

            bear[16] =
                    smaSlope(
                            close,
                            20
                    ) < 0;

            bull[17] =
                    trendConsistency(
                            close
                    ) > 0;

            bear[17] =
                    trendConsistency(
                            close
                    ) < 0;

            bull[18] =
                    last.close >
                            average(
                                    close
                            );

            bear[18] =
                    last.close <
                            average(
                                    close
                            );

            bull[19] =
                    last.close >
                            (sma10 + ema20) / 2;

            bear[19] =
                    last.close <
                            (sma10 + ema20) / 2;

            /*
             * 21-40 CANDLE
             */

            bull[20] =
                    last.bullish();

            bear[20] =
                    last.bearish();

            bull[21] =
                    prev.bullish();

            bear[21] =
                    prev.bearish();

            bull[22] =
                    bullishEngulfing(
                            prev,
                            last
                    );

            bear[22] =
                    bearishEngulfing(
                            prev,
                            last
                    );

            bull[23] =
                    hammer(last);

            bear[23] =
                    shootingStar(last);

            bull[24] =
                    doji(last);

            bear[24] =
                    doji(last);

            bull[25] =
                    last.bullish() &&
                    last.body() >
                            avgBody * 1.25;

            bear[25] =
                    last.bearish() &&
                    last.body() >
                            avgBody * 1.25;

            bull[26] =
                    closeNearHigh(last);

            bear[26] =
                    closeNearLow(last);

            bull[27] =
                    bullishCount(c, 3) == 3;

            bear[27] =
                    bearishCount(c, 3) == 3;

            bull[28] =
                    bullishStreak(c) >= 3;

            bear[28] =
                    bearishStreak(c) >= 3;

            bull[29] =
                    last.bullish() &&
                    last.body() >
                            prev.body();

            bear[29] =
                    last.bearish() &&
                    last.body() >
                            prev.body();

            bull[30] =
                    last.bullish() &&
                    prev.bearish() &&
                    p3.bearish();

            bear[30] =
                    last.bearish() &&
                    prev.bullish() &&
                    p3.bullish();

            bull[31] =
                    last.bullish() &&
                    last.range() >
                            avgRange;

            bear[31] =
                    last.bearish() &&
                    last.range() >
                            avgRange;

            bull[32] =
                    last.close >
                            last.open;

            bear[32] =
                    last.close <
                            last.open;

            bull[33] =
                    lowerWick(last) >
                            last.body() * 1.2;

            bear[33] =
                    upperWick(last) >
                            last.body() * 1.2;

            bull[34] =
                    closeNearHigh(prev);

            bear[34] =
                    closeNearLow(prev);

            bull[35] =
                    last.bullish() &&
                    prev.bullish();

            bear[35] =
                    last.bearish() &&
                    prev.bearish();

            bull[36] =
                    last.bullish() &&
                    p3.bullish();

            bear[36] =
                    last.bearish() &&
                    p3.bearish();

            bull[37] =
                    last.close >
                            prev.close &&
                    prev.close >
                            p3.close;

            bear[37] =
                    last.close <
                            prev.close &&
                    prev.close <
                            p3.close;

            bull[38] =
                    lowerWick(last) >
                            upperWick(last);

            bear[38] =
                    upperWick(last) >
                            lowerWick(last);

            bull[39] =
                    last.bullish() &&
                    last.close >
                            (last.high +
                                    last.low) / 2;

            bear[39] =
                    last.bearish() &&
                    last.close <
                            (last.high +
                                    last.low) / 2;

            /*
             * 41-60 MOMENTUM
             */

            bull[40] = rsi > 50;
            bear[40] = rsi < 50;

            bull[41] = rsi > 55;
            bear[41] = rsi < 45;

            bull[42] = rsi < 30;
            bear[42] = rsi > 70;

            bull[43] = rsi > rsiPrev;
            bear[43] = rsi < rsiPrev;

            bull[44] = macd[2] > 0;
            bear[44] = macd[2] < 0;

            bull[45] = macd[0] > macd[1];
            bear[45] = macd[0] < macd[1];

            bull[46] =
                    macd[0] > macd[1] &&
                    macd[3] <= 0;

            bear[46] =
                    macd[0] < macd[1] &&
                    macd[3] >= 0;

            bull[47] = stoch[0] > stoch[1];
            bear[47] = stoch[0] < stoch[1];

            bull[48] = stoch[0] < 20;
            bear[48] = stoch[0] > 80;

            bull[49] = roc > 0;
            bear[49] = roc < 0;

            bull[50] = momentum > 0;
            bear[50] = momentum < 0;

            bull[51] = rsi > 50 && macd[2] > 0;
            bear[51] = rsi < 50 && macd[2] < 0;

            bull[52] =
                    stoch[0] > 50 &&
                    rsi > 50;

            bear[52] =
                    stoch[0] < 50 &&
                    rsi < 50;

            bull[53] =
                    roc > 0 &&
                    momentum > 0;

            bear[53] =
                    roc < 0 &&
                    momentum < 0;

            bull[54] =
                    last.close > ema5 &&
                    rsi > 50;

            bear[54] =
                    last.close < ema5 &&
                    rsi < 50;

            bull[55] =
                    last.close > ema10 &&
                    macd[2] > 0;

            bear[55] =
                    last.close < ema10 &&
                    macd[2] < 0;

            bull[56] =
                    ema5 > ema10 &&
                    rsi > 50;

            bear[56] =
                    ema5 < ema10 &&
                    rsi < 50;

            bull[57] =
                    ema10 > ema20 &&
                    macd[2] > 0;

            bear[57] =
                    ema10 < ema20 &&
                    macd[2] < 0;

            bull[58] =
                    rsi > 55 &&
                    stoch[0] > 50;

            bear[58] =
                    rsi < 45 &&
                    stoch[0] < 50;

            bull[59] =
                    rsi > 50 &&
                    roc > 0;

            bear[59] =
                    rsi < 50 &&
                    roc < 0;

            /*
             * 61-80 VOLATILITY
             */

            bull[60] = atr > atrPrev;
            bear[60] = atr < atrPrev;

            bull[61] = atr < atrPrev;
            bear[61] = atr > atrPrev;

            bull[62] =
                    last.range() >
                            avgRange;

            bear[62] =
                    last.range() >
                            avgRange;

            bull[63] =
                    last.body() >
                            avgBody;

            bear[63] =
                    last.body() >
                            avgBody;

            bull[64] =
                    last.close >
                            recentHigh(
                                    high,
                                    10
                            );

            bear[64] =
                    last.close <
                            recentLow(
                                    low,
                                    10
                            );

            bull[65] =
                    last.close >
                            prev.high;

            bear[65] =
                    last.close <
                            prev.low;

            bull[66] =
                    last.close >
                            average(
                                    close
                            );

            bear[66] =
                    last.close <
                            average(
                                    close
                            );

            bull[67] =
                    last.bullish() &&
                    atr > atrPrev;

            bear[67] =
                    last.bearish() &&
                    atr > atrPrev;

            bull[68] =
                    last.close >
                            prev.close &&
                    last.range() >
                            prev.range();

            bear[68] =
                    last.close <
                            prev.close &&
                    last.range() >
                            prev.range();

            bull[69] =
                    last.bullish() &&
                    lowerWick(last) >
                            upperWick(last);

            bear[69] =
                    last.bearish() &&
                    upperWick(last) >
                            lowerWick(last);

            bull[70] =
                    last.close >
                            last.low +
                                    last.range() *
                                            .65;

            bear[70] =
                    last.close <
                            last.low +
                                    last.range() *
                                            .35;

            bull[71] =
                    closePosition(
                            last
                    ) > .60;

            bear[71] =
                    closePosition(
                            last
                    ) < .40;

            bull[72] =
                    last.close >
                            prev.high;

            bear[72] =
                    last.close <
                            prev.low;

            bull[73] =
                    last.high >
                            prev.high;

            bear[73] =
                    last.low <
                            prev.low;

            bull[74] =
                    last.close >
                            ema20 &&
                    atr > 0;

            bear[74] =
                    last.close <
                            ema20 &&
                    atr > 0;

            bull[75] =
                    last.close >
                            sma20 &&
                    atr > 0;

            bear[75] =
                    last.close <
                            sma20 &&
                    atr > 0;

            bull[76] =
                    avgRange > 0 &&
                    last.range() >
                            avgRange * 1.15;

            bear[76] =
                    avgRange > 0 &&
                    last.range() >
                            avgRange * 1.15;

            bull[77] =
                    last.bullish() &&
                    last.range() >
                            prev.range();

            bear[77] =
                    last.bearish() &&
                    last.range() >
                            prev.range();

            bull[78] =
                    last.bullish() &&
                    last.body() /
                            last.range() >
                            .55;

            bear[78] =
                    last.bearish() &&
                    last.body() /
                            last.range() >
                            .55;

            bull[79] =
                    last.close >
                            (last.high +
                                    last.low +
                                    prev.close) /
                                    3;

            bear[79] =
                    last.close <
                            (last.high +
                                    last.low +
                                    prev.close) /
                                    3;

            /*
             * 81-100 LEVELS
             */

            double resistance =
                    recentHigh(
                            high,
                            15
                    );

            double support =
                    recentLow(
                            low,
                            15
                    );

            double pivot =
                    (last.high +
                            last.low +
                            last.close) /
                            3;

            double midpoint =
                    (resistance +
                            support) / 2;

            bull[80] =
                    last.close >
                            resistance;

            bear[80] =
                    last.close <
                            support;

            bull[81] =
                    last.close >
                            pivot;

            bear[81] =
                    last.close <
                            pivot;

            bull[82] =
                    distance(
                            last.close,
                            support
                    ) <
                    distance(
                            last.close,
                            resistance
                    );

            bear[82] =
                    distance(
                            last.close,
                            resistance
                    ) <
                    distance(
                            last.close,
                            support
                    );

            bull[83] =
                    last.close >
                            midpoint;

            bear[83] =
                    last.close <
                            midpoint;

            bull[84] =
                    last.low <=
                            support * 1.01 &&
                    last.close > support;

            bear[84] =
                    last.high >=
                            resistance * .99 &&
                    last.close < resistance;

            bull[85] =
                    last.close >
                            recentHigh(
                                    high,
                                    Math.min(
                                            10,
                                            high.length
                                    )
                            );

            bear[85] =
                    last.close <
                            recentLow(
                                    low,
                                    Math.min(
                                            10,
                                            low.length
                                    )
                            );

            bull[86] =
                    last.low <
                            prev.low &&
                    last.close >
                            prev.close;

            bear[86] =
                    last.high >
                            prev.high &&
                    last.close <
                            prev.close;

            bull[87] =
                    higherHighs(c) &&
                    higherLows(c);

            bear[87] =
                    lowerHighs(c) &&
                    lowerLows(c);

            bull[88] =
                    last.close >
                            average(
                                    close
                            );

            bear[88] =
                    last.close <
                            average(
                                    close
                            );

            bull[89] =
                    last.close >
                            recentHigh(
                                    high,
                                    20
                            );

            bear[89] =
                    last.close <
                            recentLow(
                                    low,
                                    20
                            );

            bull[90] =
                    last.close >
                            resistance &&
                    last.bullish();

            bear[90] =
                    last.close <
                            support &&
                    last.bearish();

            bull[91] =
                    last.close >
                            pivot &&
                    rsi > 50;

            bear[91] =
                    last.close <
                            pivot &&
                    rsi < 50;

            bull[92] =
                    last.close >
                            sma20 &&
                    ema20 > ema50;

            bear[92] =
                    last.close <
                            sma20 &&
                    ema20 < ema50;

            bull[93] =
                    ema5 > ema10 &&
                    ema10 > ema20;

            bear[93] =
                    ema5 < ema10 &&
                    ema10 < ema20;

            bull[94] =
                    last.close >
                            ema20 &&
                    last.close >
                            sma20;

            bear[94] =
                    last.close <
                            ema20 &&
                    last.close <
                            sma20;

            bull[95] =
                    last.bullish() &&
                    closeNearHigh(last) &&
                    rsi > 50;

            bear[95] =
                    last.bearish() &&
                    closeNearLow(last) &&
                    rsi < 50;

            bull[96] =
                    higherLows(c) &&
                    rsi > 45;

            bear[96] =
                    lowerHighs(c) &&
                    rsi < 55;

            bull[97] =
                    macd[2] > 0 &&
                    last.close > pivot;

            bear[97] =
                    macd[2] < 0 &&
                    last.close < pivot;

            bull[98] =
                    stoch[0] > stoch[1] &&
                    last.close > pivot;

            bear[98] =
                    stoch[0] < stoch[1] &&
                    last.close < pivot;

            bull[99] =
                    bullAgreement(
                            bull
                    ) >
                    bearAgreement(
                            bear
                    );

            bear[99] =
                    bearAgreement(
                            bear
                    ) >
                    bullAgreement(
                            bull
                    );

            int bullVotes = 0;
            int bearVotes = 0;

            for (int i = 0;
                 i < 100;
                 i++) {

                if (bull[i] && !bear[i]) {
                    bullVotes++;
                }

                if (bear[i] && !bull[i]) {
                    bearVotes++;
                }
            }

            int strongest =
                    Math.max(
                            bullVotes,
                            bearVotes
                    );

            int weakest =
                    Math.min(
                            bullVotes,
                            bearVotes
                    );

            int confidence =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    strongest -
                                            weakest
                            )
                    );

            String signal;

            if (bullVotes >= 65 &&
                    bullVotes - bearVotes >= 15) {

                signal = "UP";

            } else if (bearVotes >= 65 &&
                    bearVotes - bullVotes >= 15) {

                signal = "DOWN";

            } else {

                signal = "NO TRADE";
                confidence = 0;
            }

            return new AnalysisResult(
                    signal,
                    confidence,
                    100
            );
        }

        private static int bullAgreement(
                boolean[] a
        ) {

            int n = 0;

            for (boolean b : a) {
                if (b) n++;
            }

            return n;
        }

        private static int bearAgreement(
                boolean[] a
        ) {

            int n = 0;

            for (boolean b : a) {
                if (b) n++;
            }

            return n;
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
                int period
        ) {

            return ema(
                    a,
                    period,
                    a.length
            );
        }

        private static double ema(
                double[] a,
                int period,
                int count
        ) {

            count =
                    Math.max(
                            1,
                            Math.min(
                                    count,
                                    a.length
                            )
                    );

            double k =
                    2.0 /
                            (period + 1);

            double e = a[0];

            for (int i = 1;
                 i < count;
                 i++) {

                e =
                        a[i] * k +
                        e * (1 - k);
            }

            return e;
        }

        private static double rsi(
                double[] a,
                int period
        ) {

            if (a.length < 3) {
                return 50;
            }

            int start =
                    Math.max(
                            1,
                            a.length - period
                    );

            double gain = 0;
            double loss = 0;

            for (int i = start;
                 i < a.length;
                 i++) {

                double d =
                        a[i] - a[i - 1];

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

        private static double rsiPrev(
                double[] a,
                int period
        ) {

            if (a.length < 5) {
                return 50;
            }

            double[] x =
                    new double[
                            a.length - 1
                    ];

            System.arraycopy(
                    a,
                    0,
                    x,
                    0,
                    x.length
            );

            return rsi(
                    x,
                    period
            );
        }

        private static double[] macd(
                double[] a
        ) {

            double e12 =
                    ema(a, 12);

            double e26 =
                    ema(a, 26);

            double macd =
                    e12 - e26;

            double[] temp =
                    new double[
                            a.length
                    ];

            for (int i = 0;
                 i < a.length;
                 i++) {

                double e12i =
                        ema(
                                a,
                                12,
                                i + 1
                        );

                double e26i =
                        ema(
                                a,
                                26,
                                i + 1
                        );

                temp[i] =
                        e12i - e26i;
            }

            double signal =
                    ema(
                            temp,
                            9
                    );

            double histogram =
                    macd - signal;

            double previous =
                    temp.length > 1
                            ? temp[
                                temp.length - 2
                            ]
                            : 0;

            return new double[]{
                    macd,
                    signal,
                    histogram,
                    previous
            };
        }

        private static double[] stochastic(
                double[] high,
                double[] low,
                double[] close,
                int period
        ) {

            int n =
                    Math.min(
                            period,
                            close.length
                    );

            double hi =
                    recentHigh(
                            high,
                            n
                    );

            double lo =
                    recentLow(
                            low,
                            n
                    );

            double k =
                    hi == lo
                            ? 50
                            : (
                                (close[
                                    close.length - 1
                                ] - lo)
                                /
                                (hi - lo)
                            ) * 100;

            double previousClose =
                    close.length > 1
                            ? close[
                                close.length - 2
                            ]
                            : close[
                                close.length - 1
                            ];

            double previousK =
                    hi == lo
                            ? 50
                            : (
                                (previousClose - lo)
                                /
                                (hi - lo)
                            ) * 100;

            return new double[]{
                    k,
                    previousK
            };
        }

        private static double roc(
                double[] a,
                int n
        ) {

            if (a.length <= n) {
                return 0;
            }

            double old =
                    a[
                        a.length - 1 - n
                    ];

            if (old == 0) {
                return 0;
            }

            return (
                    a[a.length - 1] -
                    old
            ) / old * 100;
        }

        private static double momentum(
                double[] a,
                int n
        ) {

            if (a.length <= n) {
                return 0;
            }

            return a[
                    a.length - 1
            ] -
                    a[
                            a.length - 1 - n
                    ];
        }

        private static double atr(
                double[] high,
                double[] low,
                double[] close,
                int period
        ) {

            int start =
                    Math.max(
                            1,
                            close.length -
                                    period
                    );

            double sum = 0;
            int count = 0;

            for (int i = start;
                 i < close.length;
                 i++) {

                double tr =
                        Math.max(
                                high[i] - low[i],
                                Math.max(
                                        Math.abs(
                                                high[i] -
                                                        close[i - 1]
                                        ),
                                        Math.abs(
                                                low[i] -
                                                        close[i - 1]
                                        )
                                )
                        );

                sum += tr;
                count++;
            }

            return count == 0
                    ? 0
                    : sum / count;
        }

        private static double averageRange(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size()
                    );

            double s = 0;

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                s += c.get(i).range();
            }

            return s / n;
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

            double s = 0;

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                s += c.get(i).body();
            }

            return s / n;
        }

        private static double recentHigh(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length
                    );

            double x =
                    -Double.MAX_VALUE;

            for (int i =
                    a.length - n;
                 i < a.length;
                 i++) {

                x =
                        Math.max(
                                x,
                                a[i]
                        );
            }

            return x;
        }

        private static double recentLow(
                double[] a,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            a.length
                    );

            double x =
                    Double.MAX_VALUE;

            for (int i =
                    a.length - n;
                 i < a.length;
                 i++) {

                x =
                        Math.min(
                                x,
                                a[i]
                        );
            }

            return x;
        }

        private static double average(
                double[] a
        ) {

            double s = 0;

            for (double x : a) {
                s += x;
            }

            return s / a.length;
        }

        private static boolean higherHighs(
                List<Candle> c
        ) {

            if (c.size() < 4) {
                return false;
            }

            int n = c.size();

            return c.get(n - 1).high >
                    c.get(n - 3).high &&
                    c.get(n - 2).high >
                            c.get(n - 4).high;
        }

        private static boolean lowerHighs(
                List<Candle> c
        ) {

            if (c.size() < 4) {
                return false;
            }

            int n = c.size();

            return c.get(n - 1).high <
                    c.get(n - 3).high &&
                    c.get(n - 2).high <
                            c.get(n - 4).high;
        }

        private static boolean higherLows(
                List<Candle> c
        ) {

            if (c.size() < 4) {
                return false;
            }

            int n = c.size();

            return c.get(n - 1).low >
                    c.get(n - 3).low &&
                    c.get(n - 2).low >
                            c.get(n - 4).low;
        }

        private static boolean lowerLows(
                List<Candle> c
        ) {

            if (c.size() < 4) {
                return false;
            }

            int n = c.size();

            return c.get(n - 1).low <
                    c.get(n - 3).low &&
                    c.get(n - 2).low <
                            c.get(n - 4).low;
        }

        private static boolean bullishEngulfing(
                Candle a,
                Candle b
        ) {

            return a.bearish() &&
                    b.bullish() &&
                    b.open <= a.close &&
                    b.close >= a.open;
        }

        private static boolean bearishEngulfing(
                Candle a,
                Candle b
        ) {

            return a.bullish() &&
                    b.bearish() &&
                    b.open >= a.close &&
                    b.close <= a.open;
        }

        private static boolean hammer(
                Candle c
        ) {

            return lowerWick(c) >
                    c.body() * 2 &&
                    upperWick(c) <
                            c.body();
        }

        private static boolean shootingStar(
                Candle c
        ) {

            return upperWick(c) >
                    c.body() * 2 &&
                    lowerWick(c) <
                            c.body();
        }

        private static boolean doji(
                Candle c
        ) {

            return c.body() <=
                    c.range() * .10;
        }

        private static double lowerWick(
                Candle c
        ) {

            return Math.min(
                    c.open,
                    c.close
            ) - c.low;
        }

        private static double upperWick(
                Candle c
        ) {

            return c.high -
                    Math.max(
                            c.open,
                            c.close
                    );
        }

        private static boolean closeNearHigh(
                Candle c
        ) {

            return closePosition(c) > .75;
        }

        private static boolean closeNearLow(
                Candle c
        ) {

            return closePosition(c) < .25;
        }

        private static double closePosition(
                Candle c
        ) {

            return (
                    c.close - c.low
            ) /
                    c.range();
        }

        private static int bullishCount(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size()
                    );

            int count = 0;

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (c.get(i).bullish()) {
                    count++;
                }
            }

            return count;
        }

        private static int bearishCount(
                List<Candle> c,
                int n
        ) {

            n =
                    Math.min(
                            n,
                            c.size()
                    );

            int count = 0;

            for (int i =
                    c.size() - n;
                 i < c.size();
                 i++) {

                if (c.get(i).bearish()) {
                    count++;
                }
            }

            return count;
        }

        private static int bullishStreak(
                List<Candle> c
        ) {

            int n = 0;

            for (int i =
                    c.size() - 1;
                 i >= 0;
                 i--) {

                if (!c.get(i).bullish()) {
                    break;
                }

                n++;
            }

            return n;
        }

        private static int bearishStreak(
                List<Candle> c
        ) {

            int n = 0;

            for (int i =
                    c.size() - 1;
                 i >= 0;
                 i--) {

                if (!c.get(i).bearish()) {
                    break;
                }

                n++;
            }

            return n;
        }

        private static double smaSlope(
                double[] a,
                int period
        ) {

            if (a.length < 4) {
                return 0;
            }

            int n =
                    Math.min(
                            period,
                            a.length
                    );

            double current =
                    sma(a, n);

            double[] previous =
                    new double[
                            a.length - 2
                    ];

            System.arraycopy(
                    a,
                    0,
                    previous,
                    0,
                    previous.length
            );

            double old =
                    sma(
                            previous,
                            Math.min(
                                    n,
                                    previous.length
                            )
                    );

            return current - old;
        }

        private static double trendConsistency(
                double[] a
        ) {

            int bull = 0;
            int bear = 0;

            int n =
                    Math.min(
                            10,
                            a.length - 1
                    );

            for (int i =
                    a.length - n;
                 i < a.length;
                 i++) {

                if (a[i] > a[i - 1]) {
                    bull++;
                } else if (a[i] < a[i - 1]) {
                    bear++;
                }
            }

            return bull - bear;
        }

        private static double distance(
                double a,
                double b
        ) {

            return Math.abs(a - b);
        }
    }
}
