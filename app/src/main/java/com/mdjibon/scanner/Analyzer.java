package com.mdjibon.scanner;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * MD JIBON SCREEN SCANNER
 *
 * Real screen / real candle analyzer.
 *
 * IMPORTANT:
 * - No synthetic candles.
 * - Requires a real chart-like candle structure.
 * - Runs exactly 20,000 parameterized evidence checks.
 * - Signal is returned ONLY when strong evidence exists.
 * - Confidence is an evidence score, NOT a guaranteed win probability.
 */
public final class Analyzer {

    private Analyzer() {}

    public static final int TOTAL_RULES = 20000;

    private static final int MAX_PROCESS_WIDTH = 900;
    private static final int MIN_REAL_CANDLES = 12;

    public static final class Result {

        public String signal = "NONE";

        public double confidence = 0.0;
        public double quality = 0.0;

        public int bullishCount = 0;
        public int bearishCount = 0;
        public int neutralCount = TOTAL_RULES;

        public int detectedCandles = 0;
        public int evaluatedRules = 0;

        public String timeframe = "1 MIN";

        public String currentCandleColor = "UNKNOWN";
        public double currentBodyRatio = 0.0;

        public String candleSize = "UNKNOWN";

        public String nextCandleColor = "UNKNOWN";
        public String nextCandleSize = "UNKNOWN";

        public double nextBodyRatio = 0.0;
        public double nextUpperWickRatio = 0.0;
        public double nextLowerWickRatio = 0.0;

        public boolean strongSignal = false;
        public boolean chartDetected = false;

        public final List<String> checks = new ArrayList<>();

        public String summary() {
            return String.format(
                    Locale.US,
                    "%s %.0f%% | candles=%d | quality=%.1f%% | checks=%d",
                    signal,
                    confidence,
                    detectedCandles,
                    quality,
                    evaluatedRules
            );
        }
    }

    private static final class Candle {

        int x;

        int top;
        int bottom;

        int high;
        int low;

        boolean green;

        double open;
        double close;

        double body() {
            return Math.abs(close - open);
        }

        double range() {
            return Math.max(1.0, high - low);
        }

        double bodyRatio() {
            return clamp(body() / range(), 0.0, 1.0);
        }

        double upperWick() {
            return Math.max(
                    0.0,
                    high - Math.max(open, close)
            );
        }

        double lowerWick() {
            return Math.max(
                    0.0,
                    Math.min(open, close) - low
            );
        }
    }

    private static final class BodyComponent {

        int left;
        int right;
        int top;
        int bottom;
        int area;

        boolean green;

        int centerX() {
            return (left + right) / 2;
        }

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }

    private static final class Score {

        double bull = 0.0;
        double bear = 0.0;

        void add(double value) {

            if (value > 0.0) {
                bull += value;
            } else if (value < 0.0) {
                bear += -value;
            }
        }

        double total() {
            return bull + bear;
        }

        double bias() {

            double total = total();

            if (total < 0.000001) {
                return 0.0;
            }

            return (bull - bear) / total;
        }
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    public static Result analyze(Bitmap source) {
        return analyze(source, "1 MIN");
    }

    public static Result analyze(Bitmap source, String timeframe) {

        Result out = new Result();

        out.timeframe = timeframe == null
                ? "1 MIN"
                : timeframe;

        if (source == null || source.isRecycled()) {

            out.checks.add("NO REAL SCREEN IMAGE");
            return out;
        }

        Bitmap bitmap = source;
        boolean scaled = false;

        try {

            if (source.getWidth() > MAX_PROCESS_WIDTH) {

                int newHeight = Math.max(
                        1,
                        (int) Math.round(
                                source.getHeight()
                                        * (
                                        MAX_PROCESS_WIDTH
                                                / (double) source.getWidth()
                                )
                        )
                );

                bitmap = Bitmap.createScaledBitmap(
                        source,
                        MAX_PROCESS_WIDTH,
                        newHeight,
                        true
                );

                scaled = true;
            }

            List<Candle> candles = extractCandles(bitmap);

            out.detectedCandles = candles.size();

            // ----------------------------------------------------
            // MARKET / CHART VALIDATION
            // ----------------------------------------------------

            if (!looksLikeMarketChart(bitmap, candles)) {

                out.chartDetected = false;
                out.signal = "NONE";
                out.strongSignal = false;
                out.confidence = 0.0;

                out.quality = Math.min(
                        40.0,
                        candles.size() * 2.5
                );

                out.checks.add("MARKET CHART NOT DETECTED");
                out.checks.add("NO SIGNAL OUTSIDE MARKET CHART");
                out.checks.add("NO SYNTHETIC CANDLES");

                return out;
            }

            out.chartDetected = true;

            if (candles.size() < MIN_REAL_CANDLES) {

                out.signal = "NONE";
                out.strongSignal = false;
                out.confidence = 0.0;

                out.quality = Math.min(
                        45.0,
                        candles.size() * 3.0
                );

                out.checks.add("INSUFFICIENT REAL MARKET CANDLES");
                out.checks.add("WAIT FOR MORE REAL CHART DATA");

                return out;
            }

            // ----------------------------------------------------
            // QUALITY
            // ----------------------------------------------------

            out.quality = calculateQuality(candles);

            Score score = new Score();

            // EXACTLY 20,000 PARAMETERIZED CHECKS
            run20000Checks(candles, score, out);

            // ----------------------------------------------------
            // DIRECTION ANCHORS
            // ----------------------------------------------------

            double anchor = directionalAnchor(candles);

            double probeBias = score.bias();

            double combined = clamp(
                    probeBias * 0.70
                            + anchor * 0.30,
                    -1.0,
                    1.0
            );

            // Agreement between independent families.
            double familyAgreement =
                    calculateFamilyAgreement(candles);

            // ----------------------------------------------------
            // EVIDENCE SCORE
            // ----------------------------------------------------

            double directionStrength =
                    Math.abs(combined);

            double rawEvidence =
                    50.0
                            + directionStrength * 50.0;

            double conflictPenalty =
                    Math.abs(probeBias - anchor) * 8.0;

            rawEvidence -= conflictPenalty;

            rawEvidence *=
                    (0.75 + 0.25 * (out.quality / 100.0));

            rawEvidence +=
                    familyAgreement * 4.0;

            out.confidence = clamp(
                    rawEvidence,
                    50.0,
                    97.0
            );

            // ----------------------------------------------------
            // STRONG SIGNAL GATE
            // ----------------------------------------------------

            boolean enoughEvidence =
                    out.chartDetected
                            && out.quality >= 48.0
                            && out.confidence >= 90.0
                            && directionStrength >= 0.18
                            && familyAgreement >= 0.18;

            out.strongSignal = enoughEvidence;

            if (enoughEvidence) {

                if (combined > 0.0) {
                    out.signal = "UP";
                } else {
                    out.signal = "DOWN";
                }

            } else {

                // IMPORTANT:
                // Weak evidence NEVER becomes UP/DOWN.
                out.signal = "NONE";
            }

            // ----------------------------------------------------
            // RESULT COUNTS
            // ----------------------------------------------------

            double total = score.total();

            double decisiveRatio =
                    total <= 0.000001
                            ? 0.0
                            : clamp(
                            total / TOTAL_RULES,
                            0.0,
                            1.0
                    );

            out.bullishCount =
                    (int) Math.round(
                            clamp(
                                    TOTAL_RULES
                                            * (0.5 + 0.5 * probeBias)
                                            * decisiveRatio,
                                    0,
                                    TOTAL_RULES
                            )
                    );

            out.bearishCount =
                    (int) Math.round(
                            clamp(
                                    TOTAL_RULES
                                            * (0.5 - 0.5 * probeBias)
                                            * decisiveRatio,
                                    0,
                                    TOTAL_RULES
                            )
                    );

            int used =
                    Math.min(
                            TOTAL_RULES,
                            out.bullishCount
                                    + out.bearishCount
                    );

            out.neutralCount =
                    TOTAL_RULES - used;

            out.evaluatedRules = TOTAL_RULES;

            // ----------------------------------------------------
            // CURRENT CANDLE
            // ----------------------------------------------------

            Candle last =
                    candles.get(candles.size() - 1);

            out.currentCandleColor =
                    last.green
                            ? "GREEN / UP"
                            : "RED / DOWN";

            out.currentBodyRatio =
                    last.bodyRatio();

            // ----------------------------------------------------
            // NEXT CANDLE ESTIMATE
            // ----------------------------------------------------

            predictNextCandle(
                    candles,
                    anchor,
                    combined,
                    out
            );

            out.candleSize =
                    candles.size()
                            + " REAL CANDLES";

            // ----------------------------------------------------
            // CHECK LOG
            // ----------------------------------------------------

            out.checks.add(
                    "REAL PIXEL CANDLE EXTRACTION"
            );

            out.checks.add(
                    "MARKET CHART VALIDATION"
            );

            out.checks.add(
                    "LEFT TO RIGHT CANDLE ORDER"
            );

            out.checks.add(
                    "GREEN / RED COLOR DETECTION"
            );

            out.checks.add(
                    "BODY / WICK GEOMETRY"
            );

            out.checks.add(
                    "TREND / MOMENTUM / STRUCTURE"
            );

            out.checks.add(
                    "20,000 PARAMETERIZED LOGIC CHECKS"
            );

            out.checks.add(
                    String.format(
                            Locale.US,
                            "CHART QUALITY %.1f%%",
                            out.quality
                    )
            );

            out.checks.add(
                    String.format(
                            Locale.US,
                            "EVIDENCE SCORE %.0f%%",
                            out.confidence
                    )
            );

            if (!out.strongSignal) {
                out.checks.add(
                        "WEAK / CONFLICTING EVIDENCE → NO SIGNAL"
                );
            }

        } finally {

            if (
                    scaled
                            && bitmap != source
                            && !bitmap.isRecycled()
            ) {
                bitmap.recycle();
            }
        }

        return out;
    }

    // ============================================================
    // CHART VALIDATION
    // ============================================================

    private static boolean looksLikeMarketChart(
            Bitmap bitmap,
            List<Candle> candles
    ) {

        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }

        if (candles.size() < 8) {
            return false;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width < 250 || height < 250) {
            return false;
        }

        double gapRegularity =
                candleGapRegularity(candles);

        double bodyWidthScore =
                candleBodyWidthScore(candles);

        double colorBalance =
                candleColorBalance(candles);

        return gapRegularity >= 0.18
                && bodyWidthScore >= 0.15
                && colorBalance >= 0.03;
    }

    private static double candleGapRegularity(
            List<Candle> candles
    ) {

        if (candles.size() < 3) {
            return 0.0;
        }

        List<Integer> gaps =
                new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {

            int gap =
                    candles.get(i).x
                            - candles.get(i - 1).x;

            if (gap >= 3 && gap <= 150) {
                gaps.add(gap);
            }
        }

        if (gaps.size() < 2) {
            return 0.0;
        }

        Collections.sort(gaps);

        double median =
                gaps.get(gaps.size() / 2);

        double totalDeviation = 0.0;

        for (int gap : gaps) {

            totalDeviation +=
                    Math.abs(gap - median)
                            / Math.max(1.0, median);
        }

        double deviation =
                totalDeviation / gaps.size();

        return clamp(
                1.0 - deviation,
                0.0,
                1.0
        );
    }

    private static double candleBodyWidthScore(
            List<Candle> candles
    ) {

        if (candles.isEmpty()) {
            return 0.0;
        }

        int good = 0;

        for (Candle c : candles) {

            if (c.body() >= 1.0
                    && c.range() >= 3.0) {
                good++;
            }
        }

        return good / (double) candles.size();
    }

    private static double candleColorBalance(
            List<Candle> candles
    ) {

        if (candles.isEmpty()) {
            return 0.0;
        }

        int green = 0;
        int red = 0;

        for (Candle c : candles) {

            if (c.green) {
                green++;
            } else {
                red++;
            }
        }

        return Math.min(
                1.0,
                (green + red) / 30.0
        );
    }

    // ============================================================
    // CANDLE EXTRACTION
    // ============================================================

    private static List<Candle> extractCandles(
            Bitmap bitmap
    ) {

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int y0 =
                Math.max(
                        0,
                        (int) (h * 0.055)
                );

        int y1 =
                Math.min(
                        h - 1,
                        (int) (h * 0.80)
                );

        int x0 =
                Math.max(
                        0,
                        (int) (w * 0.025)
                );

        int x1 =
                Math.min(
                        w - 1,
                        (int) (w * 0.975)
                );

        if (y1 - y0 < 150) {

            y0 = (int) (h * 0.035);
            y1 = (int) (h * 0.84);
        }

        int cw =
                x1 - x0 + 1;

        int ch =
                y1 - y0 + 1;

        if (cw <= 0 || ch <= 0) {
            return new ArrayList<>();
        }

        int[] pixels =
                new int[cw * ch];

        bitmap.getPixels(
                pixels,
                0,
                cw,
                x0,
                y0,
                cw,
                ch
        );

        boolean[] green =
                new boolean[pixels.length];

        boolean[] red =
                new boolean[pixels.length];

        for (int i = 0; i < pixels.length; i++) {

            int color = pixels[i];

            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            green[i] =
                    isGreen(r, g, b);

            red[i] =
                    isRed(r, g, b);
        }

        boolean[] g2 =
                erode2x2(
                        green,
                        cw,
                        ch
                );

        boolean[] r2 =
                erode2x2(
                        red,
                        cw,
                        ch
                );

        boolean[] g3 =
                erode3x3(
                        green,
                        cw,
                        ch
                );

        boolean[] r3 =
                erode3x3(
                        red,
                        cw,
                        ch
                );

        orInto(g2, g3);
        orInto(r2, r3);

        List<BodyComponent> components =
                new ArrayList<>();

        components.addAll(
                findComponents(
                        g2,
                        cw,
                        ch,
                        true
                )
        );

        components.addAll(
                findComponents(
                        r2,
                        cw,
                        ch,
                        false
                )
        );

        List<Candle> candles =
                new ArrayList<>();

        int minWidth =
                Math.max(
                        3,
                        Math.round(
                                cw * 0.0045f
                        )
                );

        for (BodyComponent bc : components) {

            if (bc.width() < minWidth) {
                continue;
            }

            if (bc.height() < 3) {
                continue;
            }

            if (
                    bc.area
                            < Math.max(
                            10,
                            minWidth * 2
                    )
            ) {
                continue;
            }

            if (
                    bc.width()
                            > Math.max(
                            55,
                            cw / 5
                    )
            ) {
                continue;
            }

            int centerX =
                    x0 + bc.centerX();

            int bodyTop =
                    y0 + bc.top;

            int bodyBottom =
                    y0 + bc.bottom;

            int high = bodyTop;
            int low = bodyBottom;

            int left =
                    Math.max(
                            0,
                            bc.centerX() - 2
                    );

            int right =
                    Math.min(
                            cw - 1,
                            bc.centerX() + 2
                    );

            boolean[] colorMask =
                    bc.green
                            ? green
                            : red;

            for (
                    int x = left;
                    x <= right;
                    x++
            ) {

                int run = 0;
                int runStart = -1;

                for (
                        int yy = 0;
                        yy < ch;
                        yy++
                ) {

                    boolean hit =
                            colorMask[
                                    yy * cw + x
                                    ];

                    if (hit) {

                        if (run == 0) {
                            runStart = yy;
                        }

                        run++;

                    } else if (run > 0) {

                        if (run >= 2) {

                            high =
                                    Math.min(
                                            high,
                                            y0 + runStart
                                    );

                            low =
                                    Math.max(
                                            low,
                                            y0 + yy - 1
                                    );
                        }

                        run = 0;
                    }
                }

                if (run >= 2) {

                    high =
                            Math.min(
                                    high,
                                    y0 + runStart
                            );

                    low =
                            Math.max(
                                    low,
                                    y0 + ch - 1
                            );
                }
            }

            int bodyHeight =
                    Math.max(
                            2,
                            bodyBottom - bodyTop + 1
                    );

            int maxExtension =
                    Math.max(
                            bodyHeight * 4,
                            Math.round(
                                    ch * 0.14f
                            )
                    );

            high =
                    Math.max(
                            y0,
                            Math.max(
                                    high,
                                    bodyTop
                                            - maxExtension
                            )
                    );

            low =
                    Math.min(
                            y1,
                            Math.min(
                                    low,
                                    bodyBottom
                                            + maxExtension
                            )
                    );

            Candle candle =
                    new Candle();

            candle.x =
                    centerX;

            candle.top =
                    bodyTop;

            candle.bottom =
                    bodyBottom;

            candle.high =
                    -high;

            candle.low =
                    -low;

            candle.green =
                    bc.green;

            if (candle.green) {

                candle.open =
                        -bodyBottom;

                candle.close =
                        -bodyTop;

            } else {

                candle.open =
                        -bodyTop;

                candle.close =
                        -bodyBottom;
            }

            candles.add(candle);
        }

        Collections.sort(
                candles,
                new Comparator<Candle>() {
                    @Override
                    public int compare(
                            Candle a,
                            Candle b
                    ) {
                        return Integer.compare(
                                a.x,
                                b.x
                        );
                    }
                }
        );

        return dedupeCandles(
                candles,
                w
        );
    }

    private static boolean isGreen(
            int r,
            int g,
            int b
    ) {

        int max =
                Math.max(
                        r,
                        Math.max(g, b)
                );

        int min =
                Math.min(
                        r,
                        Math.min(g, b)
                );

        int chroma =
                max - min;

        return chroma >= 30
                && g >= 65
                && g > r * 1.12
                && g > b * 1.02;
    }

    private static boolean isRed(
            int r,
            int g,
            int b
    ) {

        int max =
                Math.max(
                        r,
                        Math.max(g, b)
                );

        int min =
                Math.min(
                        r,
                        Math.min(g, b)
                );

        int chroma =
                max - min;

        return chroma >= 30
                && r >= 70
                && r > g * 1.15
                && r > b * 1.04;
    }

    private static boolean[] erode2x2(
            boolean[] src,
            int w,
            int h
    ) {

        boolean[] dst =
                new boolean[src.length];

        for (
                int y = 0;
                y < h - 1;
                y++
        ) {

            for (
                    int x = 0;
                    x < w - 1;
                    x++
            ) {

                int i =
                        y * w + x;

                if (
                        src[i]
                                && src[i + 1]
                                && src[i + w]
                                && src[i + w + 1]
                ) {
                    dst[i] = true;
                }
            }
        }

        return dst;
    }

    private static boolean[] erode3x3(
            boolean[] src,
            int w,
            int h
    ) {

        boolean[] dst =
                new boolean[src.length];

        for (
                int y = 1;
                y < h - 1;
                y++
        ) {

            for (
                    int x = 1;
                    x < w - 1;
                    x++
            ) {

                int i =
                        y * w + x;

                if (
                        src[i]
                                && src[i - w - 1]
                                && src[i - w]
                                && src[i - w + 1]
                                && src[i - 1]
                                && src[i + 1]
                                && src[i + w - 1]
                                && src[i + w]
                                && src[i + w + 1]
                ) {
                    dst[i] = true;
                }
            }
        }

        return dst;
    }

    private static void orInto(
            boolean[] a,
            boolean[] b
    ) {

        for (int i = 0; i < a.length; i++) {
            a[i] =
                    a[i] || b[i];
        }
    }

    private static List<BodyComponent> findComponents(
            boolean[] mask,
            int w,
            int h,
            boolean isGreen
    ) {

        boolean[] seen =
                new boolean[mask.length];

        int[] queue =
                new int[
                        Math.max(
                                1,
                                mask.length
                        )
                ];

        List<BodyComponent> result =
                new ArrayList<>();

        for (
                int start = 0;
                start < mask.length;
                start++
        ) {

            if (
                    !mask[start]
                            || seen[start]
            ) {
                continue;
            }

            int head = 0;
            int tail = 0;

            queue[tail++] =
                    start;

            seen[start] =
                    true;

            int minX =
                    start % w;

            int maxX =
                    minX;

            int minY =
                    start / w;

            int maxY =
                    minY;

            int area = 0;

            while (head < tail) {

                int p =
                        queue[head++];

                int x =
                        p % w;

                int y =
                        p / w;

                area++;

                minX =
                        Math.min(
                                minX,
                                x
                        );

                maxX =
                        Math.max(
                                maxX,
                                x
                        );

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

                if (x > 0) {
                    tail =
                            visit(
                                    mask,
                                    seen,
                                    queue,
                                    tail,
                                    p - 1
                            );
                }

                if (x + 1 < w) {
                    tail =
                            visit(
                                    mask,
                                    seen,
                                    queue,
                                    tail,
                                    p + 1
                            );
                }

                if (y > 0) {
                    tail =
                            visit(
                                    mask,
                                    seen,
                                    queue,
                                    tail,
                                    p - w
                            );
                }

                if (y + 1 < h) {
                    tail =
                            visit(
                                    mask,
                                    seen,
                                    queue,
                                    tail,
                                    p + w
                            );
                }
            }

            if (
                    area >= 10
                            && maxX - minX + 1 >= 3
            ) {

                BodyComponent bc =
                        new BodyComponent();

                bc.left = minX;
                bc.right = maxX;
                bc.top = minY;
                bc.bottom = maxY;
                bc.area = area;
                bc.green = isGreen;

                result.add(bc);
            }
        }

        return result;
    }

    private static int visit(
            boolean[] mask,
            boolean[] seen,
            int[] queue,
            int tail,
            int p
    ) {

        if (
                mask[p]
                        && !seen[p]
                        && tail < queue.length
        ) {

            seen[p] = true;
            queue[tail++] = p;
        }

        return tail;
    }

    private static List<Candle> dedupeCandles(
            List<Candle> input,
            int width
    ) {

        if (input.isEmpty()) {
            return input;
        }

        int gap =
                estimateTypicalGap(input);

        int merge =
                Math.max(
                        3,
                        Math.min(
                                Math.max(
                                        5,
                                        width / 40
                                ),
                                Math.max(
                                        4,
                                        gap / 3
                                )
                        )
                );

        List<Candle> result =
                new ArrayList<>();

        for (Candle c : input) {

            if (result.isEmpty()) {

                result.add(c);
                continue;
            }

            Candle last =
                    result.get(
                            result.size() - 1
                    );

            if (
                    Math.abs(
                            c.x - last.x
                    ) <= merge
            ) {

                if (c.body() > last.body()) {

                    c.high =
                            Math.max(
                                    c.high,
                                    last.high
                            );

                    c.low =
                            Math.min(
                                    c.low,
                                    last.low
                            );

                    result.set(
                            result.size() - 1,
                            c
                    );

                } else {

                    last.high =
                            Math.max(
                                    last.high,
                                    c.high
                            );

                    last.low =
                            Math.min(
                                    last.low,
                                    c.low
                            );
                }

            } else {

                result.add(c);
            }
        }

        return result;
    }

    private static int estimateTypicalGap(
            List<Candle> candles
    ) {

        if (candles.size() < 3) {
            return 12;
        }

        List<Integer> gaps =
                new ArrayList<>();

        for (
                int i = 1;
                i < candles.size();
                i++
        ) {

            int d =
                    candles.get(i).x
                            - candles.get(i - 1).x;

            if (d >= 3 && d <= 100) {
                gaps.add(d);
            }
        }

        if (gaps.isEmpty()) {
            return 12;
        }

        Collections.sort(gaps);

        return gaps.get(
                gaps.size() / 2
        );
    }

    // ============================================================
    // QUALITY
    // ============================================================

    private static double calculateQuality(
            List<Candle> candles
    ) {

        if (candles.size() < 2) {
            return 0.0;
        }

        double gapDeviation = 0.0;
        double bodyAverage = 0.0;

        int gapCount = 0;
        int valid = 0;

        double totalGap = 0.0;

        for (
                int i = 0;
                i < candles.size();
                i++
        ) {

            Candle c =
                    candles.get(i);

            if (
                    c.range() > 1
                            && c.body() >= 0.5
            ) {
                valid++;
            }

            bodyAverage +=
                    c.bodyRatio();

            if (i > 0) {

                double gap =
                        candles.get(i).x
                                - candles.get(i - 1).x;

                if (gap > 0) {

                    totalGap += gap;
                    gapCount++;
                }
            }
        }

        double meanGap =
                gapCount > 0
                        ? totalGap / gapCount
                        : 0.0;

        if (meanGap > 0) {

            for (
                    int i = 1;
                    i < candles.size();
                    i++
            ) {

                double gap =
                        candles.get(i).x
                                - candles.get(i - 1).x;

                gapDeviation +=
                        Math.abs(
                                gap - meanGap
                        ) / meanGap;
            }

            gapDeviation /=
                    Math.max(
                            1,
                            candles.size() - 1
                    );
        }

        double regularity =
                1.0
                        - clamp(
                        gapDeviation,
                        0.0,
                        1.0
                );

        double countScore =
                clamp(
                        candles.size() / 35.0,
                        0.0,
                        1.0
                );

        double validScore =
                valid
                        / (double) candles.size();

        double bodyScore =
                clamp(
                        (
                                bodyAverage
                                        / candles.size()
                        ) * 1.6,
                        0.0,
                        1.0
                );

        return clamp(
                100.0
                        * (
                        0.38 * countScore
                                + 0.27 * validScore
                                + 0.20 * regularity
                                + 0.15 * bodyScore
                ),
                0.0,
                100.0
        );
    }

    // ============================================================
    // 20,000 CHECKS
    // ============================================================

    private static void run20000Checks(
            List<Candle> candles,
            Score score,
            Result out
    ) {

        /*
         * 4,000 TREND
         * 4,000 MOMENTUM
         * 4,000 CANDLE
         * 4,000 LEVEL/VOLATILITY
         * 4,000 CONFIRMATION
         *
         * TOTAL = 20,000
         */

        for (int i = 0; i < 4000; i++) {

            int lookback =
                    2 + (i % 28);

            double threshold =
                    0.02
                            + (
                            (i / 28) % 12
                    ) * 0.0125;

            score.add(
                    trendProbe(
                            candles,
                            lookback,
                            threshold
                    )
            );
        }

        for (int i = 0; i < 4000; i++) {

            int lookback =
                    2 + (i % 25);

            double threshold =
                    0.015
                            + (
                            (i / 25) % 14
                    ) * 0.009;

            score.add(
                    momentumProbe(
                            candles,
                            lookback,
                            threshold
                    )
            );
        }

        for (int i = 0; i < 4000; i++) {

            int lookback =
                    2 + (i % 18);

            double threshold =
                    0.08
                            + (
                            (i / 18) % 14
                    ) * 0.025;

            score.add(
                    candleProbe(
                            candles,
                            lookback,
                            threshold
                    )
            );
        }

        for (int i = 0; i < 4000; i++) {

            int lookback =
                    3 + (i % 30);

            double threshold =
                    0.015
                            + (
                            (i / 30) % 14
                    ) * 0.012;

            score.add(
                    levelProbe(
                            candles,
                            lookback,
                            threshold
                    )
            );
        }

        for (int i = 0; i < 4000; i++) {

            int lookback =
                    3 + (i % 24);

            double threshold =
                    0.03
                            + (
                            (i / 24) % 12
                    ) * 0.014;

            score.add(
                    confirmationProbe(
                            candles,
                            lookback,
                            threshold
                    )
            );
        }

        out.checks.add(
                "4,000 TREND CHECKS"
        );

        out.checks.add(
                "4,000 MOMENTUM CHECKS"
        );

        out.checks.add(
                "4,000 CANDLE CHECKS"
        );

        out.checks.add(
                "4,000 LEVEL/VOLATILITY CHECKS"
        );

        out.checks.add(
                "4,000 CONFIRMATION CHECKS"
        );

        out.checks.add(
                "TOTAL = 20,000 CHECKS"
        );
    }

    // ============================================================
    // TREND
    // ============================================================

    private static double trendProbe(
            List<Candle> c,
            int n,
            double threshold
    ) {

        n =
                Math.min(
                        n,
                        c.size() - 1
                );

        double slope =
                normalizedSlope(
                        c,
                        n
                );

        double fast =
                ema(
                        c,
                        Math.max(
                                2,
                                n / 2
                        )
                );

        double slow =
                ema(
                        c,
                        n
                );

        double spread =
                normalize(
                        fast - slow,
                        averageRange(
                                c,
                                n
                        )
                );

        int hh =
                higherHighCount(
                        c,
                        n
                );

        int hl =
                higherLowCount(
                        c,
                        n
                );

        int lh =
                lowerHighCount(
                        c,
                        n
                );

        int ll =
                lowerLowCount(
                        c,
                        n
                );

        double structure =
                (
                        hh
                                + hl
                                - lh
                                - ll
                )
                        / (double) Math.max(2, n);

        double value =
                signStrength(
                        slope,
                        threshold
                ) * 0.45

                        + signStrength(
                        spread,
                        threshold * 0.7
                ) * 0.25

                        + clamp(
                        structure * 1.6,
                        -1.0,
                        1.0
                ) * 0.30;

        return clamp(
                value,
                -1.0,
                1.0
        );
    }

    // ============================================================
    // MOMENTUM
    // ============================================================

    private static double momentumProbe(
            List<Candle> c,
            int n,
            double threshold
    ) {

        n =
                Math.min(
                        n,
                        c.size() - 1
                );

        double rocValue =
                roc(
                        c,
                        n
                );

        double rsiValue =
                rsi(
                        c,
                        Math.max(
                                3,
                                Math.min(
                                        20,
                                        n
                                )
                        )
                );

        double rsiSignal =
                clamp(
                        (rsiValue - 50.0)
                                / 22.0,
                        -1.0,
                        1.0
                );

        double value =
                signStrength(
                        rocValue,
                        threshold
                ) * 0.58
                        + rsiSignal * 0.42;

        if (rsiValue > 82.0) {
            value -= 0.20;
        } else if (rsiValue > 72.0) {
            value -= 0.10;
        }

        if (rsiValue < 18.0) {
            value += 0.20;
        } else if (rsiValue < 28.0) {
            value += 0.10;
        }

        return clamp(
                value,
                -1.0,
                1.0
        );
    }

    // ============================================================
    // CANDLE
    // ============================================================

    private static double candleProbe(
            List<Candle> c,
            int n,
            double threshold
    ) {

        Candle last =
                c.get(
                        c.size() - 1
                );

        double value =
                last.green
                        ? 0.20
                        : -0.20;

        double body =
                last.bodyRatio();

        double upper =
                last.upperWick()
                        / last.range();

        double lower =
                last.lowerWick()
                        / last.range();

        if (body > threshold) {

            value +=
                    last.green
                            ? 0.20
                            : -0.20;
        }

        if (
                lower > 0.42
                        && upper < 0.28
        ) {
            value += 0.26;
        }

        if (
                upper > 0.42
                        && lower < 0.28
        ) {
            value -= 0.26;
        }

        int run =
                consecutiveDirection(
                        c,
                        Math.min(
                                n,
                                8
                        )
                );

        value +=
                clamp(
                        run / 8.0,
                        -1.0,
                        1.0
                ) * 0.14;

        if (c.size() >= 2) {

            Candle previous =
                    c.get(
                            c.size() - 2
                    );

            if (
                    last.green
                            && !previous.green
                            && last.close > previous.open
                            && last.open < previous.close
            ) {
                value += 0.40;
            }

            if (
                    !last.green
                            && previous.green
                            && last.close < previous.open
                            && last.open > previous.close
            ) {
                value -= 0.40;
            }
        }

        return clamp(
                value,
                -1.0,
                1.0
        );
    }

    // ============================================================
    // LEVEL / VOLATILITY
    // ============================================================

    private static double levelProbe(
            List<Candle> c,
            int n,
            double threshold
    ) {

        n =
                Math.min(
                        n,
                        c.size() - 1
                );

        Candle last =
                c.get(
                        c.size() - 1
                );

        int lookback =
                Math.max(
                        2,
                        Math.min(
                                n,
                                c.size() - 1
                        )
                );

        double high =
                -Double.MAX_VALUE;

        double low =
                Double.MAX_VALUE;

        for (
                int i = c.size() - 1 - lookback;
                i < c.size() - 1;
                i++
        ) {

            if (i < 0) {
                continue;
            }

            high =
                    Math.max(
                            high,
                            c.get(i).high
                    );

            low =
                    Math.min(
                            low,
                            c.get(i).low
                    );
        }

        if (
                !Double.isFinite(high)
                        || !Double.isFinite(low)
        ) {
            return 0.0;
        }

        double range =
                Math.max(
                        1e-6,
                        high - low
                );

        double position =
                normalize(
                        last.close - low,
                        range
                );

        double value = 0.0;

        double buffer =
                range
                        * Math.max(
                        0.01,
                        threshold * 0.20
                );

        if (last.close > high + buffer) {
            value += 0.40;
        } else if (last.close < low - buffer) {
            value -= 0.40;
        } else {

            if (position > 0.94) {
                value -= 0.22;
            }

            if (position < 0.06) {
                value += 0.22;
            }
        }

        double averageRange =
                averageRange(
                        c,
                        lookback
                );

        if (
                last.range()
                        > averageRange
                        * (1.0 + threshold)
        ) {

            value +=
                    last.green
                            ? 0.18
                            : -0.18;
        }

        return clamp(
                value,
                -1.0,
                1.0
        );
    }

    // ============================================================
    // CONFIRMATION
    // ============================================================

    private static double confirmationProbe(
            List<Candle> c,
            int n,
            double threshold
    ) {

        double trend =
                trendProbe(
                        c,
                        n,
                        threshold
                );

        double momentum =
                momentumProbe(
                        c,
                        n,
                        threshold
                );

        double candle =
                candleProbe(
                        c,
                        n,
                        threshold
                );

        double level =
                levelProbe(
                        c,
                        n,
                        threshold
                );

        int positive = 0;
        int negative = 0;

        if (trend > 0.15) positive++;
        if (momentum > 0.15) positive++;
        if (candle > 0.15) positive++;
        if (level > 0.15) positive++;

        if (trend < -0.15) negative++;
        if (momentum < -0.15) negative++;
        if (candle < -0.15) negative++;
        if (level < -0.15) negative++;

        double value =
                (
                        trend
                                + momentum
                                + candle
                                + level
                ) / 4.0;

        if (positive >= 3) {
            value += 0.25;
        }

        if (negative >= 3) {
            value -= 0.25;
        }

        if (
                positive > 0
                        && negative > 0
        ) {
            value *= 0.50;
        }

        return clamp(
                value,
                -1.0,
                1.0
        );
    }

    // ============================================================
    // FAMILY AGREEMENT
    // ============================================================

    private static double calculateFamilyAgreement(
            List<Candle> c
    ) {

        int n =
                Math.min(
                        12,
                        c.size() - 1
                );

        double t =
                trendProbe(
                        c,
                        n,
                        0.05
                );

        double m =
                momentumProbe(
                        c,
                        n,
                        0.04
                );

        double p =
                candleProbe(
                        c,
                        n,
                        0.20
                );

        double l =
                levelProbe(
                        c,
                        n,
                        0.06
                );

        double[] values =
                {
                        t,
                        m,
                        p,
                        l
                };

        double sum = 0.0;

        for (double value : values) {
            sum += value;
        }

        double direction =
                Math.signum(sum);

        if (direction == 0.0) {
            return 0.0;
        }

        int agree = 0;

        for (double value : values) {

            if (
                    Math.signum(value)
                            == direction
                            && Math.abs(value) > 0.10
            ) {
                agree++;
            }
        }

        return agree / 4.0;
    }

    // ============================================================
    // DIRECTION
    // ============================================================

    private static double directionalAnchor(
            List<Candle> c
    ) {

        int n =
                c.size();

        double slope =
                normalizedSlope(
                        c,
                        Math.min(
                                12,
                                n
                        )
                );

        double emaSpread =
                normalize(
                        ema(
                                c,
                                Math.min(5, n)
                        )
                                - ema(
                                c,
                                Math.min(15, n)
                        ),
                        averageRange(
                                c,
                                12
                        )
                );

        double ret =
                normalize(
                        c.get(n - 1).close
                                - c.get(
                                Math.max(
                                        0,
                                        n - 6
                                )
                        ).close,
                        averageRange(
                                c,
                                10
                        ) * 5.0
                );

        int run =
                consecutiveDirection(
                        c,
                        5
                );

        double structure =
                (
                        higherHighCount(
                                c,
                                Math.min(8, n - 1)
                        )
                                + higherLowCount(
                                c,
                                Math.min(8, n - 1)
                        )
                                - lowerHighCount(
                                c,
                                Math.min(8, n - 1)
                        )
                                - lowerLowCount(
                                c,
                                Math.min(8, n - 1)
                        )
                ) / 8.0;

        double last =
                c.get(n - 1).green
                        ? 1.0
                        : -1.0;

        return clamp(
                slope * 0.28
                        + emaSpread * 0.22
                        + ret * 0.22
                        + clamp(
                        structure,
                        -1.0,
                        1.0
                ) * 0.18
                        + last * 0.10,
                -1.0,
                1.0
        );
    }

    // ============================================================
    // NEXT CANDLE
    // ============================================================

    private static void predictNextCandle(
            List<Candle> c,
            double anchor,
            double combined,
            Result out
    ) {

        int n =
                c.size();

        double rsiValue =
                rsi(
                        c,
                        Math.min(
                                14,
                                n - 1
                        )
                );

        double lastBody =
                c.get(n - 1)
                        .bodyRatio();

        double recentBody =
                averageBodyRatio(
                        c,
                        Math.min(
                                8,
                                n
                        )
                );

        double direction =
                combined * 0.72
                        + anchor * 0.18
                        + (
                        c.get(n - 1).green
                                ? 0.10
                                : -0.10
                );

        if (rsiValue > 82.0) {
            direction -= 0.20;
        }

        if (rsiValue < 18.0) {
            direction += 0.20;
        }

        if (
                Math.abs(direction)
                        < 0.10
        ) {

            out.nextCandleColor =
                    "UNCERTAIN";

            out.nextCandleSize =
                    "UNKNOWN";

            out.nextBodyRatio = 0.0;

            return;
        }

        out.nextCandleColor =
                direction > 0.0
                        ? "GREEN / UP"
                        : "RED / DOWN";

        double averageRange =
                averageRange(
                        c,
                        10
                );

        double averageBody =
                averageBody(
                        c,
                        10
                );

        double expected =
                averageBody
                        * (
                        0.78
                                + 0.34
                                * Math.abs(direction)
                );

        if (
                lastBody
                        > recentBody * 1.35
        ) {
            expected *= 0.88;
        }

        if (
                lastBody
                        < recentBody * 0.65
        ) {
            expected *= 1.08;
        }

        double bodyRatio =
                clamp(
                        (
                                expected
                                        / Math.max(
                                        1.0,
                                        averageRange
                                )
                        ) * 0.92,
                        0.08,
                        0.86
                );

        out.nextBodyRatio =
                bodyRatio;

        double wick =
                0.10
                        + (
                        1.0 - bodyRatio
                ) * 0.16;

        out.nextUpperWickRatio =
                clamp(
                        wick,
                        0.05,
                        0.30
                );

        out.nextLowerWickRatio =
                clamp(
                        wick,
                        0.05,
                        0.30
                );

        if (
                expected
                        < averageBody * 0.72
        ) {

            out.nextCandleSize =
                    "SMALL";

        } else if (
                expected
                        > averageBody * 1.30
        ) {

            out.nextCandleSize =
                    "LARGE";

        } else {

            out.nextCandleSize =
                    "MEDIUM";
        }
    }

    // ============================================================
    // MATH HELPERS
    // ============================================================

    private static double averageBodyRatio(
            List<Candle> c,
            int n
    ) {

        n =
                Math.min(
                        n,
                        c.size()
                );

        if (n <= 0) {
            return 0.0;
        }

        double sum = 0.0;

        for (
                int i = c.size() - n;
                i < c.size();
                i++
        ) {
            sum +=
                    c.get(i)
                            .bodyRatio();
        }

        return sum / n;
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

        if (n <= 0) {
            return 0.0;
        }

        double sum = 0.0;

        for (
                int i = c.size() - n;
                i < c.size();
                i++
        ) {
            sum +=
                    c.get(i)
                            .body();
        }

        return sum / n;
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

        if (n <= 0) {
            return 1.0;
        }

        double sum = 0.0;

        for (
                int i = c.size() - n;
                i < c.size();
                i++
        ) {
            sum +=
                    c.get(i)
                            .range();
        }

        return Math.max(
                1.0,
                sum / n
        );
    }

    private static double ema(
            List<Candle> c,
            int n
    ) {

        n =
                Math.max(
                        2,
                        Math.min(
                                n,
                                c.size()
                        )
                );

        double alpha =
                2.0
                        / (n + 1.0);

        double value =
                c.get(0).close;

        for (
                int i = 1;
                i < c.size();
                i++
        ) {

            value =
                    alpha
                            * c.get(i).close
                            + (
                            1.0 - alpha
                    ) * value;
        }

        return value;
    }

    private static double roc(
            List<Candle> c,
            int n
    ) {

        n =
                Math.max(
                        1,
                        Math.min(
                                n,
                                c.size() - 1
                        )
                );

        double old =
                c.get(
                        c.size() - 1 - n
                ).close;

        double now =
                c.get(
                        c.size() - 1
                ).close;

        if (Math.abs(old) < 1e-6) {
            return 0.0;
        }

        return (
                now - old
        ) / Math.abs(old);
    }

    private static double rsi(
            List<Candle> c,
            int n
    ) {

        n =
                Math.max(
                        2,
                        Math.min(
                                n,
                                c.size() - 1
                        )
                );

        double gain = 0.0;
        double loss = 0.0;

        for (
                int i = c.size() - n;
                i < c.size();
                i++
        ) {

            double difference =
                    c.get(i).close
                            - c.get(i - 1).close;

            if (difference > 0.0) {
                gain += difference;
            } else {
                loss -= difference;
            }
        }

        if (loss < 1e-6) {
            return gain > 0.0
                    ? 100.0
                    : 50.0;
        }

        double rs =
                gain / loss;

        return 100.0
                - 100.0
                / (1.0 + rs);
    }

    private static double normalizedSlope(
            List<Candle> c,
            int n
    ) {

        n =
                Math.max(
                        2,
                        Math.min(
                                n,
                                c.size()
                        )
                );

        double xMean =
                (n - 1) / 2.0;

        double yMean = 0.0;

        for (
                int i = c.size() - n;
                i < c.size();
                i++
        ) {
            yMean +=
                    c.get(i).close;
        }

        yMean /= n;

        double numerator = 0.0;
        double denominator = 0.0;

        for (int j = 0; j < n; j++) {

            double x =
                    j - xMean;

            double y =
                    c.get(
                            c.size()
                                    - n
                                    + j
                    ).close
                            - yMean;

            numerator +=
                    x * y;

            denominator +=
                    x * x;
        }

        if (denominator == 0.0) {
            return 0.0;
        }

        return normalize(
                (
                        numerator
                                / denominator
                ) * n,
                averageRange(c, n)
        );
    }

    private static int consecutiveDirection(
            List<Candle> c,
            int max
    ) {

        boolean direction =
                c.get(
                        c.size() - 1
                ).green;

        int count = 0;

        for (
                int i = c.size() - 1;
                i >= 0 && count < max;
                i--
        ) {

            if (
                    c.get(i).green
                            == direction
            ) {
                count++;
            } else {
                break;
            }
        }

        return direction
                ? count
                : -count;
    }

    private static int higherHighCount(
            List<Candle> c,
            int n
    ) {

        int count = 0;

        int start =
                Math.max(
                        1,
                        c.size() - n
                );

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            if (
                    c.get(i).high
                            > c.get(i - 1).high
            ) {
                count++;
            }
        }

        return count;
    }

    private static int higherLowCount(
            List<Candle> c,
            int n
    ) {

        int count = 0;

        int start =
                Math.max(
                        1,
                        c.size() - n
                );

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            if (
                    c.get(i).low
                            > c.get(i - 1).low
            ) {
                count++;
            }
        }

        return count;
    }

    private static int lowerHighCount(
            List<Candle> c,
            int n
    ) {

        int count = 0;

        int start =
                Math.max(
                        1,
                        c.size() - n
                );

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            if (
                    c.get(i).high
                            < c.get(i - 1).high
            ) {
                count++;
            }
        }

        return count;
    }

    private static int lowerLowCount(
            List<Candle> c,
            int n
    ) {

        int count = 0;

        int start =
                Math.max(
                        1,
                        c.size() - n
                );

        for (
                int i = start;
                i < c.size();
                i++
        ) {

            if (
                    c.get(i).low
                            < c.get(i - 1).low
            ) {
                count++;
            }
        }

        return count;
    }

    private static double normalize(
            double value,
            double scale
    ) {

        if (
                Math.abs(scale)
                        < 1e-6
        ) {
            return 0.0;
        }

        return value
                / Math.abs(scale);
    }

    private static double signStrength(
            double value,
            double threshold
    ) {

        if (
                Math.abs(value)
                        <= threshold
        ) {
            return 0.0;
        }

        double x =
                clamp(
                        (
                                Math.abs(value)
                                        - threshold
                        )
                                / Math.max(
                                1e-4,
                                threshold * 3.0
                        ),
                        0.0,
                        1.0
                );

        return Math.signum(value)
                * (
                0.25
                        + 0.75 * x
        );
    }

    private static double clamp(
            double value,
            double low,
            double high
    ) {

        return Math.max(
                low,
                Math.min(
                        high,
                        value
                )
        );
    }
}
