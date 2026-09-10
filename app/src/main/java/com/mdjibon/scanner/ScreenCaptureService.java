package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScreenCaptureService extends Service {

    public static final String ACTION_RESULT =
            "MDJIBON_SCAN_RESULT";

    public static final String ACTION_PROGRESS =
            "MDJIBON_SCAN_PROGRESS";

    private static final String CHANNEL =
            "MDJIBON_CAPTURE";

    private static MediaProjection projection;

    private VirtualDisplay virtualDisplay;

    private ImageReader imageReader;

    private Bitmap latestBitmap;

    private Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean scanning = false;

    private int screenWidth;

    private int screenHeight;

    private int screenDensity;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isCaptureRunning() {
        return projection != null;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        android.util.DisplayMetrics dm =
                getResources()
                        .getDisplayMetrics();

        screenWidth =
                dm.widthPixels;

        screenHeight =
                dm.heightPixels;

        screenDensity =
                dm.densityDpi;
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent == null)
            return START_NOT_STICKY;

        if ("SCAN_NOW".equals(intent.getAction())) {

            scanNow();

            return START_NOT_STICKY;
        }

        if (
                intent.hasExtra("code") &&
                intent.hasExtra("data")
        ) {

            int code =
                    intent.getIntExtra(
                            "code",
                            -1
                    );

            Intent data =
                    intent.getParcelableExtra(
                            "data"
                    );

            if (
                    code == -1 ||
                    data == null
            ) {

                return START_NOT_STICKY;
            }

            startCapture(
                    code,
                    data
            );
        }

        return START_STICKY;
    }

    private void startCapture(
            int resultCode,
            Intent data
    ) {

        if (projection != null)
            return;

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL
                )
                        .setContentTitle(
                                "MD JIBON Screen Scanner"
                        )
                        .setContentText(
                                "Screen Capture active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_view
                        )
                        .setOngoing(true)
                        .build();

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                    8001,
                    notification,
                    android.content.pm.ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );

        } else {

            startForeground(
                    8001,
                    notification
            );
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

        if (projection == null)
            return;

        projection.registerCallback(
                new MediaProjection.Callback() {

                    @Override
                    public void onStop() {

                        stopCapture();

                        super.onStop();
                    }
                },
                handler
        );

        createReader();
    }

    private void createReader() {

        imageReader =
                ImageReader.newInstance(
                        screenWidth,
                        screenHeight,
                        android.graphics.PixelFormat.RGBA_8888,
                        2
                );

        imageReader.setOnImageAvailableListener(
                reader -> {

                    Image image = null;

                    try {

                        image =
                                reader.acquireLatestImage();

                        if (image == null)
                            return;

                        Image.Plane[] planes =
                                image.getPlanes();

                        if (
                                planes == null ||
                                planes.length == 0
                        )
                            return;

                        ByteBuffer buffer =
                                planes[0].getBuffer();

                        int pixelStride =
                                planes[0]
                                        .getPixelStride();

                        int rowStride =
                                planes[0]
                                        .getRowStride();

                        int rowPadding =
                                rowStride -
                                        pixelStride *
                                                screenWidth;

                        int bitmapWidth =
                                screenWidth +
                                        rowPadding /
                                                pixelStride;

                        Bitmap bitmap =
                                Bitmap.createBitmap(
                                        bitmapWidth,
                                        screenHeight,
                                        Bitmap.Config
                                                .ARGB_8888
                                );

                        bitmap.copyPixelsFromBuffer(
                                buffer
                        );

                        if (latestBitmap != null) {

                            latestBitmap.recycle();
                        }

                        latestBitmap =
                                Bitmap.createBitmap(
                                        bitmap,
                                        0,
                                        0,
                                        screenWidth,
                                        screenHeight
                                );

                        bitmap.recycle();

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
                        screenWidth,
                        screenHeight,
                        screenDensity,
                        DisplayManager
                                .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        handler
                );
    }

    private void scanNow() {

        if (scanning)
            return;

        if (
                projection == null ||
                latestBitmap == null
        ) {

            sendResult(
                    "NO TRADE",
                    0,
                    0,
                    0
            );

            return;
        }

        scanning = true;

        new Thread(() -> {

            Bitmap frame = latestBitmap;

            if (frame == null) {

                scanning = false;

                sendResult(
                        "NO TRADE",
                        0,
                        0,
                        0
                );

                return;
            }

            for (int p = 0; p <= 100; p += 5) {

                final int progress = p;

                handler.post(
                        () -> sendProgress(
                                progress
                        )
                );

                try {
                    Thread.sleep(45);
                } catch (InterruptedException ignored) {
                }
            }

            AnalysisResult result =
                    analyze(frame);

            handler.post(() -> {

                scanning = false;

                sendResult(
                        result.signal,
                        result.confidence,
                        result.quality,
                        result.candles
                );
            });

        }).start();
    }

    private AnalysisResult analyze(
            Bitmap bitmap
    ) {

        int w = bitmap.getWidth();

        int h = bitmap.getHeight();

        int left =
                (int) (w * 0.05f);

        int right =
                (int) (w * 0.95f);

        int top =
                (int) (h * 0.20f);

        int bottom =
                (int) (h * 0.78f);

        List<Candle> candles =
                detectCandles(
                        bitmap,
                        left,
                        top,
                        right,
                        bottom
                );

        if (candles.size() < 8) {

            int q =
                    Math.min(
                            40,
                            candles.size() * 5
                    );

            return new AnalysisResult(
                    "NO TRADE",
                    0,
                    q,
                    candles.size()
            );
        }

        Collections.sort(
                candles,
                Comparator.comparingInt(
                        a -> a.x
                )
        );

        if (candles.size() > 80) {

            candles =
                    new ArrayList<>(
                            candles.subList(
                                    candles.size() - 80,
                                    candles.size()
                            )
                    );
        }

        int up = 0;

        int down = 0;

        for (int rule = 1; rule <= 100; rule++) {

            int vote =
                    ruleVote(
                            rule,
                            candles
                    );

            if (vote > 0)
                up++;

            if (vote < 0)
                down++;
        }

        int total =
                up + down;

        if (total < 15) {

            return new AnalysisResult(
                    "NO TRADE",
                    0,
                    frameQuality(candles),
                    candles.size()
            );
        }

        int confidence =
                Math.round(
                        100f *
                                Math.max(
                                        up,
                                        down
                                )
                                / 100f
                );

        String signal;

        if (
                up >= 58 &&
                up > down + 5
        ) {

            signal = "UP";

        } else if (
                down >= 58 &&
                down > up + 5
        ) {

            signal = "DOWN";

        } else {

            signal = "NO TRADE";
            confidence = 0;
        }

        return new AnalysisResult(
                signal,
                confidence,
                frameQuality(candles),
                candles.size()
        );
    }

    private List<Candle> detectCandles(
            Bitmap b,
            int left,
            int top,
            int right,
            int bottom
    ) {

        ArrayList<Candle> list =
                new ArrayList<>();

        int width =
                right - left;

        int[] red =
                new int[width];

        int[] green =
                new int[width];

        for (int x = left; x < right; x++) {

            int rCount = 0;
            int gCount = 0;

            for (
                    int y = top;
                    y < bottom;
                    y += 3
            ) {

                int c =
                        b.getPixel(x, y);

                int r =
                        Color.red(c);

                int g =
                        Color.green(c);

                int bl =
                        Color.blue(c);

                if (
                        g > r * 1.30f &&
                        g > bl * 1.20f &&
                        g > 70
                ) {

                    gCount++;

                } else if (
                        r > g * 1.25f &&
                        r > bl * 1.20f &&
                        r > 70
                ) {

                    rCount++;
                }
            }

            red[x - left] = rCount;
            green[x - left] = gCount;
        }

        int x = 0;

        while (x < width) {

            int score =
                    red[x] +
                            green[x];

            if (score < 4) {

                x++;
                continue;
            }

            int start = x;

            int end = x;

            int rTotal = 0;
            int gTotal = 0;

            while (
                    end < width &&
                    (
                            red[end] +
                                    green[end] >= 3 ||
                            end - start < 2
                    )
            ) {

                rTotal += red[end];
                gTotal += green[end];

                end++;

                if (end - start > 18)
                    break;
            }

            int center =
                    left +
                            (start + end) / 2;

            boolean isGreen =
                    gTotal >= rTotal;

            int topY = bottom;

            int bottomY = top;

            int bodyTop = bottom;

            int bodyBottom = top;

            int samples = 0;

            for (
                    int xx = Math.max(
                            left,
                            center - 8
                    );
                    xx < Math.min(
                            right,
                            center + 9
                    );
                    xx++
            ) {

                for (
                        int yy = top;
                        yy < bottom;
                        yy += 2
                ) {

                    int c =
                            b.getPixel(
                                    xx,
                                    yy
                            );

                    int r =
                            Color.red(c);

                    int g =
                            Color.green(c);

                    int bl =
                            Color.blue(c);

                    boolean good =
                            isGreen
                                    ? (
                                    g > r * 1.25f &&
                                    g > bl * 1.15f &&
                                    g > 60
                            )
                                    : (
                                    r > g * 1.20f &&
                                    r > bl * 1.15f &&
                                    r > 60
                            );

                    if (good) {

                        topY =
                                Math.min(
                                        topY,
                                        yy
                                );

                        bottomY =
                                Math.max(
                                        bottomY,
                                        yy
                                );

                        samples++;
                    }
                }
            }

            if (samples >= 5) {

                float high =
                        bottom - topY;

                float low =
                        bottom - bottomY;

                float open;
                float close;

                if (isGreen) {

                    open =
                            bottom - bottomY;

                    close =
                            bottom - topY;

                } else {

                    open =
                            bottom - topY;

                    close =
                            bottom - bottomY;
                }

                if (high > low) {

                    list.add(
                            new Candle(
                                    center,
                                    open,
                                    close,
                                    high,
                                    low,
                                    isGreen
                            )
                    );
                }
            }

            x =
                    Math.max(
                            end + 1,
                            x + 1
                    );
        }

        return list;
    }

    private int ruleVote(
            int rule,
            List<Candle> c
    ) {

        int n = c.size();

        Candle last =
                c.get(n - 1);

        Candle prev =
                c.get(n - 2);

        float close =
                last.close;

        float prevClose =
                prev.close;

        float change =
                close - prevClose;

        /*
         * 1-10: Recent price action
         */

        if (rule <= 10) {

            int k =
                    rule % 5 + 2;

            float sum = 0;

            for (
                    int i = n - k;
                    i < n;
                    i++
            ) {

                sum +=
                        c.get(i).close -
                                c.get(i).open;
            }

            if (sum > 0)
                return 1;

            if (sum < 0)
                return -1;

            return 0;
        }

        /*
         * 11-20: Moving averages
         */

        if (rule <= 20) {

            int fast =
                    3 +
                            (rule % 5);

            int slow =
                    8 +
                            (rule % 7);

            float f =
                    smaClose(
                            c,
                            fast
                    );

            float s =
                    smaClose(
                            c,
                            slow
                    );

            if (f > s && close > f)
                return 1;

            if (f < s && close < f)
                return -1;

            return 0;
        }

        /*
         * 21-30: RSI
         */

        if (rule <= 30) {

            int period =
                    5 +
                            rule % 6;

            float rsi =
                    rsi(
                            c,
                            period
                    );

            if (rsi < 35)
                return 1;

            if (rsi > 65)
                return -1;

            if (
                    rsi > 50 &&
                            close > prevClose
            )
                return 1;

            if (
                    rsi < 50 &&
                            close < prevClose
            )
                return -1;

            return 0;
        }

        /*
         * 31-40: MACD style momentum
         */

        if (rule <= 40) {

            int fast =
                    5 + rule % 4;

            int slow =
                    12 + rule % 5;

            int signal =
                    4 + rule % 3;

            float f =
                    emaClose(
                            c,
                            fast
                    );

            float s =
                    emaClose(
                            c,
                            slow
                    );

            float macd =
                    f - s;

            float previous =
                    emaAtDifference(
                            c,
                            slow,
                            signal
                    );

            if (
                    macd > previous &&
                            macd > 0
            )
                return 1;

            if (
                    macd < previous &&
                            macd < 0
            )
                return -1;

            return 0;
        }

        /*
         * 41-50: Bollinger
         */

        if (rule <= 50) {

            int p =
                    8 + rule % 6;

            float mean =
                    smaClose(c, p);

            float sd =
                    stdClose(c, p);

            float upper =
                    mean + 2f * sd;

            float lower =
                    mean - 2f * sd;

            if (close <= lower)
                return 1;

            if (close >= upper)
                return -1;

            if (close > mean)
                return 1;

            if (close < mean)
                return -1;

            return 0;
        }

        /*
         * 51-60: Stochastic
         */

        if (rule <= 60) {

            int p =
                    5 + rule % 6;

            float st =
                    stochastic(
                            c,
                            p
                    );

            if (st < 20)
                return 1;

            if (st > 80)
                return -1;

            if (st > 50)
                return 1;

            if (st < 50)
                return -1;

            return 0;
        }

        /*
         * 61-70: Candle body / wick logic
         */

        if (rule <= 70) {

            float body =
                    Math.abs(
                            last.close -
                                    last.open
                    );

            float range =
                    Math.max(
                            0.001f,
                            last.high -
                                    last.low
                    );

            float ratio =
                    body / range;

            if (
                    last.green &&
                            ratio > 0.55f
            )
                return 1;

            if (
                    !last.green &&
                            ratio > 0.55f
            )
                return -1;

            float upper =
                    last.high -
                            Math.max(
                                    last.open,
                                    last.close
                            );

            float lower =
                    Math.min(
                            last.open,
                            last.close
                    ) -
                            last.low;

            if (lower > upper * 1.5f)
                return 1;

            if (upper > lower * 1.5f)
                return -1;

            return 0;
        }

        /*
         * 71-80: Support / resistance
         */

        if (rule <= 80) {

            int p =
                    10 + rule % 10;

            float highest =
                    highest(
                            c,
                            p
                    );

            float lowest =
                    lowest(
                            c,
                            p
                    );

            float distanceHigh =
                    Math.abs(
                            highest - close
                    );

            float distanceLow =
                    Math.abs(
                            close - lowest
                    );

            if (
                    distanceLow <
                            distanceHigh * 0.65f
            )
                return 1;

            if (
                    distanceHigh <
                            distanceLow * 0.65f
            )
                return -1;

            return 0;
        }

        /*
         * 81-90: Volatility / ATR
         */

        if (rule <= 90) {

            int p =
                    5 + rule % 7;

            float atr =
                    atr(
                            c,
                            p
                    );

            float body =
                    Math.abs(
                            last.close -
                                    last.open
                    );

            if (
                    body > atr * 0.7f &&
                            change > 0
            )
                return 1;

            if (
                    body > atr * 0.7f &&
                            change < 0
            )
                return -1;

            return 0;
        }

        /*
         * 91-100: Multi-confirmation
         */

        float ma =
                smaClose(c, 10);

        float r =
                rsi(c, 10);

        float st =
                stochastic(c, 10);

        float e =
                emaClose(c, 12);

        int score = 0;

        if (close > ma)
            score++;

        else
            score--;

        if (r > 50)
            score++;

        else
            score--;

        if (st > 50)
            score++;

        else
            score--;

        if (close > e)
            score++;

        else
            score--;

        if (
                last.green &&
                        prev.green
        )
            score++;

        if (
                !last.green &&
                        !prev.green
        )
            score--;

        if (score >= 2)
            return 1;

        if (score <= -2)
            return -1;

        return 0;
    }

    private float smaClose(
            List<Candle> c,
            int period
    ) {

        int start =
                Math.max(
                        0,
                        c.size() - period
                );

        float sum = 0;

        int count = 0;

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            sum += c.get(i).close;

            count++;
        }

        return count == 0
                ? 0
                : sum / count;
    }

    private float emaClose(
            List<Candle> c,
            int period
    ) {

        if (c.isEmpty())
            return 0;

        float alpha =
                2f /
                        (period + 1f);

        float ema =
                c.get(0).close;

        for (
                int i = 1;
                i < c.size();
                i++
        ) {

            ema =
                    alpha *
                            c.get(i).close +
                            (1f - alpha) *
                                    ema;
        }

        return ema;
    }

    private float emaAtDifference(
            List<Candle> c,
            int slow,
            int signal
    ) {

        int start =
                Math.max(
                        0,
                        c.size() -
                                signal -
                                2
                );

        float sum = 0;

        int count = 0;

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            float fast =
                    emaCloseAt(
                            c,
                            i,
                            5
                    );

            float slowEma =
                    emaCloseAt(
                            c,
                            i,
                            slow
                    );

            sum +=
                    fast -
                            slowEma;

            count++;
        }

        return count == 0
                ? 0
                : sum / count;
    }

    private float emaCloseAt(
            List<Candle> c,
            int index,
            int period
    ) {

        int start =
                Math.max(
                        0,
                        index - period * 3
                );

        float alpha =
                2f /
                        (period + 1f);

        float ema =
                c.get(start).close;

        for (
                int i = start + 1;
                i <= index;
                i++
        ) {

            ema =
                    alpha *
                            c.get(i).close +
                            (1f - alpha) *
                                    ema;
        }

        return ema;
    }

    private float rsi(
            List<Candle> c,
            int period
    ) {

        if (c.size() < period + 1)
            return 50;

        float gain = 0;

        float loss = 0;

        int start =
                c.size() - period;

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            float d =
                    c.get(i).close -
                            c.get(i - 1).close;

            if (d > 0)
                gain += d;

            else
                loss -= d;
        }

        if (loss == 0)
            return 100;

        float rs =
                gain / loss;

        return 100f -
                100f /
                        (1f + rs);
    }

    private float stochastic(
            List<Candle> c,
            int period
    ) {

        float high =
                highest(
                        c,
                        period
                );

        float low =
                lowest(
                        c,
                        period
                );

        float close =
                c.get(
                        c.size() - 1
                ).close;

        if (high == low)
            return 50;

        return
                100f *
                        (close - low) /
                        (high - low);
    }

    private float highest(
            List<Candle> c,
            int period
    ) {

        int start =
                Math.max(
                        0,
                        c.size() - period
                );

        float v =
                Float.NEGATIVE_INFINITY;

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            v =
                    Math.max(
                            v,
                            c.get(i).high
                    );
        }

        return v;
    }

    private float lowest(
            List<Candle> c,
            int period
    ) {

        int start =
                Math.max(
                        0,
                        c.size() - period
                );

        float v =
                Float.POSITIVE_INFINITY;

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            v =
                    Math.min(
                            v,
                            c.get(i).low
                    );
        }

        return v;
    }

    private float stdClose(
            List<Candle> c,
            int period
    ) {

        float mean =
                smaClose(
                        c,
                        period
                );

        int start =
                Math.max(
                        0,
                        c.size() - period
                );

        float sum = 0;

        int count = 0;

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            float d =
                    c.get(i).close -
                            mean;

            sum += d * d;

            count++;
        }

        return count == 0
                ? 0
                : (float)
                        Math.sqrt(
                                sum / count
                        );
    }

    private float atr(
            List<Candle> c,
            int period
    ) {

        int start =
                Math.max(
                        1,
                        c.size() - period
                );

        float sum = 0;

        int count = 0;

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            Candle x =
                    c.get(i);

            Candle p =
                    c.get(i - 1);

            float tr =
                    Math.max(
                            x.high -
                                    x.low,
                            Math.max(
                                    Math.abs(
                                            x.high -
                                                    p.close
                                    ),
                                    Math.abs(
                                            x.low -
                                                    p.close
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

    private int frameQuality(
            List<Candle> candles
    ) {

        int q =
                candles.size() * 6;

        return Math.min(
                100,
                Math.max(
                        0,
                        q
                )
        );
    }

    private void sendProgress(
            int progress
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
                progress
        );

        sendBroadcast(i);
    }

    private void sendResult(
            String signal,
            int confidence,
            int quality,
            int candles
    ) {

        Intent result =
                new Intent(
                        ACTION_RESULT
                );

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
                100
        );

        result.putExtra(
                "detectedCandles",
                candles
        );

        sendBroadcast(result);
    }

    private void stopCapture() {

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

        if (latestBitmap != null) {

            try {
                latestBitmap.recycle();
            } catch (Exception ignored) {
            }

            latestBitmap = null;
        }

        projection = null;
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
                            "Screen Capture",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    @Override
    public void onDestroy() {

        stopCapture();

        super.onDestroy();
    }

    private static class Candle {

        int x;

        float open;

        float close;

        float high;

        float low;

        boolean green;

        Candle(
                int x,
                float open,
                float close,
                float high,
                float low,
                boolean green
        ) {

            this.x = x;
            this.open = open;
            this.close = close;
            this.high = high;
            this.low = low;
            this.green = green;
        }
    }

    private static class AnalysisResult {

        String signal;

        int confidence;

        int quality;

        int candles;

        AnalysisResult(
                String signal,
                int confidence,
                int quality,
                int candles
        ) {

            this.signal = signal;
            this.confidence = confidence;
            this.quality = quality;
            this.candles = candles;
        }
    }
}
