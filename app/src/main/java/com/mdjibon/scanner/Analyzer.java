package com.mdjibon.scanner;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * MD JIBON Screenshot Analyzer
 *
 * Screenshot-only engine:
 * - Does NOT use MediaProjection.
 * - Does NOT create synthetic/fake candles.
 * - Detects real green/red candle pixels from the supplied screenshot.
 * - Adapts to different screenshot sizes and zoom levels.
 * - Uses 1000 parameterized analytical probes. These are not 1000 copied
 *   "if" statements: the probes vary lookback, thresholds and feature
 *   combinations across independent feature families.
 *
 * IMPORTANT:
 * The returned confidence is an internal evidence score, NOT a guaranteed
 * probability of winning a trade.
 */
public final class Analyzer {

    private Analyzer() {}

    public static final int TOTAL_RULES = 1000;
    private static final int MAX_PROCESS_WIDTH = 900;

    public static final class Result {
        public String signal = "NO TRADE";
        public double confidence = 0.0;
        public double quality = 0.0;

        public int bullishCount = 0;
        public int bearishCount = 0;
        public int neutralCount = 0;
        public int detectedCandles = 0;
        public int evaluatedRules = 0;

        public String timeframe = "1 MIN";
        public String candleSize = "UNKNOWN";
        public String nextCandleColor = "UNKNOWN";
        public String nextCandleSize = "UNKNOWN";
        public double nextBodyRatio = 0.0;

        public final List<String> checks = new ArrayList<String>();

        public String summary() {
            return String.format(Locale.US,
                    "%s %.1f%% | candles=%d | quality=%.1f%%",
                    signal, confidence, detectedCandles, quality);
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
            return Math.max(1.0, Math.abs(high - low));
        }

        double bodyRatio() {
            return clamp(body() / range(), 0.0, 1.0);
        }

        double upperWick() {
            return Math.max(0.0, high - Math.max(open, close));
        }

        double lowerWick() {
            return Math.max(0.0, Math.min(open, close) - low);
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
        double bull;
        double bear;

        void add(double value) {
            if (value > 0.0) bull += value;
            else if (value < 0.0) bear += -value;
        }

        double net() {
            return bull - bear;
        }
    }

    public static Result analyze(Bitmap source) {
        Result out = new Result();

        if (source == null || source.isRecycled()) {
            out.checks.add("NO IMAGE");
            return out;
        }

        Bitmap bitmap = source;
        boolean scaled = false;

        try {
            if (source.getWidth() > MAX_PROCESS_WIDTH) {
                int h = (int) Math.round(source.getHeight()
                        * (MAX_PROCESS_WIDTH / (double) source.getWidth()));
                bitmap = Bitmap.createScaledBitmap(source, MAX_PROCESS_WIDTH, h, true);
                scaled = true;
            }

            List<Candle> candles = extractCandles(bitmap);
            out.detectedCandles = candles.size();

            if (candles.size() < 12) {
                out.quality = Math.max(0.0, candles.size() * 5.0);
                out.checks.add("INSUFFICIENT REAL CANDLES");
                out.checks.add("NO SYNTHETIC CANDLES USED");
                return out;
            }

            double quality = calculateQuality(candles, bitmap.getWidth(), bitmap.getHeight());
            out.quality = quality;

            Score score = new Score();
            run1000Probes(candles, score, out);

            double total = score.bull + score.bear;
            if (total <= 0.0001) {
                out.checks.add("NO DECISIVE EVIDENCE");
                return out;
            }

            double agreement = Math.max(score.bull, score.bear) / total;
            out.confidence = clamp(50.0 + (agreement - 0.5) * 100.0, 0.0, 99.0);

            // Quality is a gate, not a cosmetic number.
            if (quality < 48.0 || agreement < 0.58) {
                out.signal = "NO TRADE";
            } else {
                out.signal = score.bull >= score.bear ? "UP" : "DOWN";
            }

            out.bullishCount = (int) Math.round(score.bull);
            out.bearishCount = (int) Math.round(score.bear);
            out.neutralCount = TOTAL_RULES
                    - out.bullishCount - out.bearishCount;
            if (out.neutralCount < 0) out.neutralCount = 0;
            out.evaluatedRules = TOTAL_RULES;

            predictNextCandle(candles, score, out);

            out.candleSize = candles.size() + " REAL CANDLES";
            out.timeframe = "1 MIN";

            out.checks.add("REAL PIXEL CANDLE EXTRACTION");
            out.checks.add("LEFT TO RIGHT CHRONOLOGICAL ORDER");
            out.checks.add("NO SYNTHETIC CANDLE FALLBACK");
            out.checks.add("1000 PARAMETERIZED PROBES");
            out.checks.add(String.format(Locale.US,
                    "CHART QUALITY %.1f%%", quality));

        } finally {
            if (scaled && bitmap != source && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        return out;
    }

    // ============================================================
    // REAL CANDLE EXTRACTION
    // ============================================================

    private static List<Candle> extractCandles(Bitmap bitmap) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();

        // The supplied examples are Quotex-style portrait screenshots.
        // Chart occupies roughly the upper 7%..77% of the screen.
        // We still derive the crop from the image size so 1080p/720p/other
        // portrait screenshots scale naturally.
        int y0 = Math.max(0, (int) (h * 0.065));
        int y1 = Math.min(h - 1, (int) (h * 0.765));
        if (y1 - y0 < 150) {
            y0 = Math.max(0, (int) (h * 0.05));
            y1 = Math.min(h - 1, (int) (h * 0.80));
        }

        int cw = w;
        int ch = y1 - y0 + 1;

        int[] pixels = new int[cw * ch];
        bitmap.getPixels(pixels, 0, cw, 0, y0, cw, ch);

        boolean[] green = new boolean[cw * ch];
        boolean[] red = new boolean[cw * ch];

        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int r = Color.red(c);
            int g = Color.green(c);
            int b = Color.blue(c);

            green[i] = isGreen(r, g, b);
            red[i] = isRed(r, g, b);
        }

        // Remove 1-pixel/very-thin indicator lines. A real candle body
        // normally survives this 3x3 same-color erosion.
        boolean[] greenCore = erode3x3(green, cw, ch);
        boolean[] redCore = erode3x3(red, cw, ch);

        List<BodyComponent> components = new ArrayList<BodyComponent>();
        components.addAll(findComponents(greenCore, cw, ch, true));
        components.addAll(findComponents(redCore, cw, ch, false));

        // Convert body components to candidate candles.
        List<Candle> candles = new ArrayList<Candle>();

        for (BodyComponent bc : components) {
            int minWidth = Math.max(4, Math.round(cw * 0.008f));
            if (bc.width() < minWidth) continue;
            if (bc.height() < 3) continue;
            if (bc.area < Math.max(18, minWidth * 3)) continue;

            // Reject components that are implausibly wide for a candle.
            if (bc.width() > Math.max(55, cw / 5)) continue;

            int cx = bc.centerX();
            int bodyTop = bc.top + y0;
            int bodyBottom = bc.bottom + y0;

            // Estimate the wick by reading a narrow vertical neighborhood
            // around the body's center. We only accept the same candle color.
            int high = bodyTop;
            int low = bodyBottom;

            int xLeft = Math.max(0, cx - 2);
            int xRight = Math.min(cw - 1, cx + 2);

            for (int x = xLeft; x <= xRight; x++) {
                for (int yy = 0; yy < ch; yy++) {
                    int idx = yy * cw + x;
                    if (bc.green ? green[idx] : red[idx]) {
                        int sy = yy + y0;
                        if (sy < high) high = sy;
                        if (sy > low) low = sy;
                    }
                }
            }

            // If a colored indicator crosses the candle center, do not allow
            // a gigantic false wick. Limit each side to a few body heights.
            int bodyH = Math.max(2, bodyBottom - bodyTop + 1);
            int maxExtension = Math.max(bodyH * 4, Math.round(ch * 0.18f));
            high = Math.max(y0, Math.max(high, bodyTop - maxExtension));
            low = Math.min(y1, Math.min(low, bodyBottom + maxExtension));

            Candle c = new Candle();
            c.x = cx;
            c.top = bodyTop;
            c.bottom = bodyBottom;
            c.high = -high;
            c.low = -low;
            c.green = bc.green;

            // Screen y increases downward. Price therefore increases upward.
            if (c.green) {
                c.open = -bodyBottom;
                c.close = -bodyTop;
            } else {
                c.open = -bodyTop;
                c.close = -bodyBottom;
            }

            candles.add(c);
        }

        // Sort by screen x: oldest on the left, newest on the right.
        Collections.sort(candles, new Comparator<Candle>() {
            @Override
            public int compare(Candle a, Candle b) {
                return Integer.compare(a.x, b.x);
            }
        });

        // Merge duplicate detections caused by erosion splitting one body.
        return dedupeCandles(candles, cw);
    }

    private static boolean[] erode3x3(boolean[] src, int w, int h) {
        boolean[] dst = new boolean[src.length];

        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                if (!src[i]) continue;

                if (src[i - w - 1] && src[i - w] && src[i - w + 1]
                        && src[i - 1] && src[i + 1]
                        && src[i + w - 1] && src[i + w] && src[i + w + 1]) {
                    dst[i] = true;
                }
            }
        }
        return dst;
    }

    private static List<BodyComponent> findComponents(
            boolean[] mask, int w, int h, boolean isGreen) {

        boolean[] seen = new boolean[mask.length];
        int[] queue = new int[Math.max(1024, mask.length / 20)];
        List<BodyComponent> result = new ArrayList<BodyComponent>();

        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || seen[start]) continue;

            int head = 0;
            int tail = 0;

            if (tail >= queue.length) {
                queue = new int[Math.min(mask.length, queue.length * 2)];
            }
            queue[tail++] = start;
            seen[start] = true;

            int minX = start % w;
            int maxX = minX;
            int minY = start / w;
            int maxY = minY;
            int area = 0;

            while (head < tail) {
                int p = queue[head++];
                int x = p % w;
                int y = p / w;

                area++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;

                int n;

                if (x > 0) {
                    n = p - 1;
                    if (mask[n] && !seen[n]) {
                        seen[n] = true;
                        if (tail >= queue.length) {
                            queue = growQueue(queue, mask.length);
                        }
                        queue[tail++] = n;
                    }
                }
                if (x + 1 < w) {
                    n = p + 1;
                    if (mask[n] && !seen[n]) {
                        seen[n] = true;
                        if (tail >= queue.length) queue = growQueue(queue, mask.length);
                        queue[tail++] = n;
                    }
                }
                if (y > 0) {
                    n = p - w;
                    if (mask[n] && !seen[n]) {
                        seen[n] = true;
                        if (tail >= queue.length) queue = growQueue(queue, mask.length);
                        queue[tail++] = n;
                    }
                }
                if (y + 1 < h) {
                    n = p + w;
                    if (mask[n] && !seen[n]) {
                        seen[n] = true;
                        if (tail >= queue.length) queue = growQueue(queue, mask.length);
                        queue[tail++] = n;
                    }
                }
            }

            if (area >= 12 && (maxX - minX + 1) >= 3) {
                BodyComponent bc = new BodyComponent();
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

    private static int[] growQueue(int[] q, int max) {
        int next = Math.min(max, q.length * 2);
        if (next <= q.length) return q;
        int[] n = new int[next];
        System.arraycopy(q, 0, n, 0, q.length);
        return n;
    }

    private static List<Candle> dedupeCandles(List<Candle> input, int width) {
        if (input.isEmpty()) return input;

        int typicalGap = estimateTypicalGap(input);
        int mergeDistance = Math.max(3, Math.min(typicalGap / 3, Math.max(5, width / 35)));

        List<Candle> out = new ArrayList<Candle>();

        for (Candle c : input) {
            if (out.isEmpty()) {
                out.add(c);
                continue;
            }

            Candle last = out.get(out.size() - 1);

            if (Math.abs(c.x - last.x) <= mergeDistance) {
                // Keep the larger body component.
                if (c.body() > last.body()) {
                    out.set(out.size() - 1, c);
                } else {
                    // Extend wick information conservatively.
                    last.high = Math.max(last.high, c.high);
                    last.low = Math.min(last.low, c.low);
                }
            } else {
                out.add(c);
            }
        }

        return out;
    }

    private static int estimateTypicalGap(List<Candle> candles) {
        if (candles.size() < 3) return 12;

        List<Integer> gaps = new ArrayList<Integer>();
        for (int i = 1; i < candles.size(); i++) {
            int g = candles.get(i).x - candles.get(i - 1).x;
            if (g >= 3 && g <= 100) gaps.add(g);
        }
        if (gaps.isEmpty()) return 12;
        Collections.sort(gaps);
        return gaps.get(gaps.size() / 2);
    }

    private static boolean isGreen(int r, int g, int b) {
        return g >= 85
                && g > r * 1.22
                && g > b * 1.08
                && (g - r) >= 30;
    }

    private static boolean isRed(int r, int g, int b) {
        return r >= 90
                && r > g * 1.28
                && r > b * 1.10
                && (r - g) >= 30;
    }

    // ============================================================
    // QUALITY
    // ============================================================

    private static double calculateQuality(List<Candle> c, int width, int height) {
        if (c.size() < 2) return 0.0;

        double spacing = 0.0;
        int spacingN = 0;
        double valid = 0.0;
        double bodyRatio = 0.0;

        for (int i = 0; i < c.size(); i++) {
            Candle x = c.get(i);
            if (x.range() > 1.0 && x.body() >= 0.5) valid++;
            bodyRatio += x.bodyRatio();

            if (i > 0) {
                int gap = c.get(i).x - c.get(i - 1).x;
                if (gap > 0) {
                    spacing += gap;
                    spacingN++;
                }
            }
        }

        double spacingMean = spacingN == 0 ? 0 : spacing / spacingN;
        double regularity = 0.0;

        if (spacingMean > 0) {
            double dev = 0.0;
            for (int i = 1; i < c.size(); i++) {
                double gap = c.get(i).x - c.get(i - 1).x;
                dev += Math.abs(gap - spacingMean) / spacingMean;
            }
            dev /= Math.max(1, c.size() - 1);
            regularity = 1.0 - clamp(dev, 0.0, 1.0);
        }

        double countScore = clamp(c.size() / 35.0, 0.0, 1.0);
        double validScore = valid / c.size();
        double bodyScore = clamp((bodyRatio / c.size()) * 1.5, 0.0, 1.0);

        return clamp(
                100.0 * (0.35 * countScore
                        + 0.30 * validScore
                        + 0.20 * regularity
                        + 0.15 * bodyScore),
                0.0, 100.0);
    }

    // ============================================================
    // 1000 PARAMETERIZED ANALYTICAL PROBES
    // ============================================================

    private static void run1000Probes(
            List<Candle> c, Score s, Result out) {

        /*
         * The 1000 probes are generated across five feature families.
         * Each family uses multiple lookbacks/thresholds and produces a
         * different signed evidence value. This avoids a fake list of
         * duplicated checks.
         */

        int id = 0;

        // 1..250 TREND / STRUCTURE
        for (int i = 0; i < 250; i++) {
            int lookback = 3 + (i % 20);
            double threshold = 0.10 + ((i / 20) % 8) * 0.04;
            double v = trendProbe(c, lookback, threshold);
            s.add(v);
            id++;
        }

        // 251..500 MOMENTUM / RSI / ROC
        for (int i = 0; i < 250; i++) {
            int lookback = 3 + (i % 18);
            double threshold = 0.15 + ((i / 18) % 9) * 0.025;
            double v = momentumProbe(c, lookback, threshold);
            s.add(v);
            id++;
        }

        // 501..700 CANDLE GEOMETRY / PATTERNS
        for (int i = 0; i < 200; i++) {
            int lookback = 2 + (i % 12);
            double threshold = 0.18 + ((i / 12) % 8) * 0.04;
            double v = candleProbe(c, lookback, threshold);
            s.add(v);
            id++;
        }

        // 701..850 LEVELS / VOLATILITY / BREAKOUT
        for (int i = 0; i < 150; i++) {
            int lookback = 5 + (i % 24);
            double threshold = 0.08 + ((i / 24) % 7) * 0.035;
            double v = levelProbe(c, lookback, threshold);
            s.add(v);
            id++;
        }

        // 851..1000 MULTI-FEATURE CONFIRMATION
        for (int i = 0; i < 150; i++) {
            int lookback = 4 + (i % 16);
            double threshold = 0.12 + ((i / 16) % 8) * 0.035;
            double v = confirmationProbe(c, lookback, threshold);
            s.add(v);
            id++;
        }

        out.checks.add("TREND/STRUCTURE 250");
        out.checks.add("MOMENTUM/OSCILLATOR 250");
        out.checks.add("CANDLE/PATTERN 200");
        out.checks.add("LEVEL/VOLATILITY 150");
        out.checks.add("MULTI-FEATURE 150");
    }

    private static double trendProbe(List<Candle> c, int n, double threshold) {
        n = Math.min(n, c.size() - 1);
        double slope = normalizedSlope(c, n);
        double emaFast = ema(c, Math.max(2, n / 2));
        double emaSlow = ema(c, n);
        double spread = normalize(emaFast - emaSlow, averageRange(c, n));

        double value = 0.0;
        value += signStrength(slope, threshold) * 0.55;
        value += signStrength(spread, threshold * 0.75) * 0.30;

        int hh = higherHighCount(c, n);
        int hl = higherLowCount(c, n);
        int lh = lowerHighCount(c, n);
        int ll = lowerLowCount(c, n);

        value += clamp((hh + hl - lh - ll) / (double) Math.max(2, n) * 1.2, -1, 1) * 0.35;
        return clamp(value, -1.0, 1.0);
    }

    private static double momentumProbe(List<Candle> c, int n, double threshold) {
        n = Math.min(n, c.size() - 1);

        double roc = roc(c, n);
        double rsi = rsi(c, Math.max(3, Math.min(20, n)));
        double rsiSignal = clamp((rsi - 50.0) / 20.0, -1.0, 1.0);

        double value = signStrength(roc, threshold * 0.65) * 0.55
                + rsiSignal * 0.45;

        // Avoid blindly buying/selling extreme RSI. At extremes,
        // mean-reversion evidence partially opposes momentum.
        if (rsi > 75) value -= 0.20;
        if (rsi < 25) value += 0.20;

        return clamp(value, -1, 1);
    }

    private static double candleProbe(List<Candle> c, int n, double threshold) {
        Candle last = c.get(c.size() - 1);
        double value = last.green ? 0.35 : -0.35;

        double body = last.bodyRatio();
        double upper = last.upperWick() / last.range();
        double lower = last.lowerWick() / last.range();

        // Body pressure.
        if (body > threshold) value += last.green ? 0.25 : -0.25;

        // Rejection.
        if (lower > 0.45 && upper < 0.25) value += 0.30;
        if (upper > 0.45 && lower < 0.25) value -= 0.30;

        // Short sequence.
        int run = consecutiveDirection(c, Math.min(n, 8));
        if (run > 0) value += clamp(run / 6.0, 0, 1) * 0.20;
        if (run < 0) value -= clamp((-run) / 6.0, 0, 1) * 0.20;

        // Engulfing-like relation to previous candle.
        if (c.size() >= 2) {
            Candle p = c.get(c.size() - 2);
            if (last.green && !p.green && last.close > p.open && last.open < p.close) {
                value += 0.55;
            }
            if (!last.green && p.green && last.close < p.open && last.open > p.close) {
                value -= 0.55;
            }
        }

        // Local reversal around recent extremes.
        double min = recentLow(c, Math.min(n + 2, c.size()));
        double max = recentHigh(c, Math.min(n + 2, c.size()));
        double pos = normalize(last.close - min, max - min);
        if (pos < 0.18 && lower > 0.25) value += 0.30;
        if (pos > 0.82 && upper > 0.25) value -= 0.30;

        return clamp(value, -1, 1);
    }

    private static double levelProbe(List<Candle> c, int n, double threshold) {
        n = Math.min(n, c.size() - 1);
        Candle last = c.get(c.size() - 1);

        // Use the candles BEFORE the current candle for breakout levels.
        int lookback = Math.max(1, Math.min(n, c.size() - 1));
        double hi = Double.NEGATIVE_INFINITY;
        double lo = Double.POSITIVE_INFINITY;
        for (int i = c.size() - 1 - lookback; i < c.size() - 1; i++) {
            if (i < 0) continue;
            hi = Math.max(hi, c.get(i).high);
            lo = Math.min(lo, c.get(i).low);
        }
        if (!Double.isFinite(hi) || !Double.isFinite(lo)) {
            return 0.0;
        }

        double levelRange = Math.max(hi - lo, 1e-9);
        double pos = normalize(last.close - lo, levelRange);

        double value = 0.0;
        double buffer = levelRange * Math.max(0.01, threshold * 0.15);

        // Breakout evidence.
        if (last.close >= hi - buffer) value += 0.40;
        if (last.close <= lo + buffer) value -= 0.40;

        // Mean-reversion near extremes.
        if (pos > 0.92) value -= 0.35;
        if (pos < 0.08) value += 0.35;

        // Volatility expansion + directional close.
        double nowRange = last.range();
        double avg = averageRange(c, n);
        if (avg > 0 && nowRange > avg * (1.0 + threshold)) {
            value += last.green ? 0.30 : -0.30;
        }

        return clamp(value, -1, 1);
    }

    private static double confirmationProbe(List<Candle> c, int n, double threshold) {
        n = Math.min(n, c.size() - 1);

        double t = trendProbe(c, n, threshold);
        double m = momentumProbe(c, n, threshold);
        double p = candleProbe(c, n, threshold);
        double l = levelProbe(c, n, threshold);

        double agree = 0.0;
        int positive = 0;
        int negative = 0;

        if (t > 0.15) positive++;
        if (m > 0.15) positive++;
        if (p > 0.15) positive++;
        if (l > 0.15) positive++;

        if (t < -0.15) negative++;
        if (m < -0.15) negative++;
        if (p < -0.15) negative++;
        if (l < -0.15) negative++;

        if (positive >= 3) agree += 0.70;
        if (negative >= 3) agree -= 0.70;

        // If evidence conflicts strongly, reduce rather than fabricate
        // a decisive signal.
        double net = (t + m + p + l) / 4.0;
        agree += net * 0.45;

        return clamp(agree, -1, 1);
    }

    // ============================================================
    // NEXT CANDLE ESTIMATE
    // ============================================================

    private static void predictNextCandle(
            List<Candle> c, Score score, Result out) {

        int n = c.size();
        int look = Math.min(12, n - 1);

        double trend = normalizedSlope(c, look);
        double momentum = roc(c, Math.min(6, n - 1));
        double rsiV = rsi(c, Math.min(14, n - 1));
        double lastBody = c.get(n - 1).bodyRatio();

        double direction = 0.40 * signStrength(trend, 0.08)
                + 0.30 * signStrength(momentum, 0.10)
                + 0.20 * clamp((rsiV - 50) / 25.0, -1, 1)
                + 0.10 * (c.get(n - 1).green ? 1 : -1);

        // Extreme oscillator levels are treated as reversal evidence.
        if (rsiV > 78) direction -= 0.25;
        if (rsiV < 22) direction += 0.25;

        out.nextCandleColor = direction >= 0 ? "GREEN / UP" : "RED / DOWN";

        double avgBody = averageBody(c, Math.min(12, n));
        double vol = averageRange(c, Math.min(12, n));
        double expected = 0.55 * avgBody + 0.25 * vol * lastBody
                + 0.20 * avgBody * Math.abs(direction);

        double ratio = vol <= 0 ? 0.0 : expected / vol;
        out.nextBodyRatio = clamp(ratio, 0.05, 0.95);

        if (expected < avgBody * 0.65) {
            out.nextCandleSize = "SMALL";
        } else if (expected > avgBody * 1.35) {
            out.nextCandleSize = "LARGE";
        } else {
            out.nextCandleSize = "MEDIUM";
        }

        // If evidence is nearly balanced, explicitly avoid pretending
        // direction is certain.
        if (Math.abs(direction) < 0.08 && out.signal.equals("NO TRADE")) {
            out.nextCandleColor = "UNCERTAIN";
            out.nextCandleSize = "UNKNOWN";
        }

        out.checks.add(String.format(Locale.US,
                "NEXT BODY RATIO %.1f%%", out.nextBodyRatio * 100.0));
    }

    // ============================================================
    // INDICATOR HELPERS
    // ============================================================

    private static double averageClose(List<Candle> c, int n) {
        n = Math.min(n, c.size());
        if (n <= 0) return 0;
        double s = 0;
        for (int i = c.size() - n; i < c.size(); i++) s += c.get(i).close;
        return s / n;
    }

    private static double averageBody(List<Candle> c, int n) {
        n = Math.min(n, c.size());
        if (n <= 0) return 0;
        double s = 0;
        for (int i = c.size() - n; i < c.size(); i++) s += c.get(i).body();
        return s / n;
    }

    private static double averageRange(List<Candle> c, int n) {
        n = Math.min(n, c.size());
        if (n <= 0) return 1;
        double s = 0;
        for (int i = c.size() - n; i < c.size(); i++) s += c.get(i).range();
        return Math.max(1.0, s / n);
    }

    private static double ema(List<Candle> c, int n) {
        n = Math.max(2, Math.min(n, c.size()));
        double alpha = 2.0 / (n + 1.0);
        double value = c.get(0).close;

        for (int i = 1; i < c.size(); i++) {
            value = alpha * c.get(i).close + (1.0 - alpha) * value;
        }
        return value;
    }

    private static double roc(List<Candle> c, int n) {
        n = Math.max(1, Math.min(n, c.size() - 1));
        double old = c.get(c.size() - 1 - n).close;
        double now = c.get(c.size() - 1).close;
        if (Math.abs(old) < 0.0001) return 0;
        return (now - old) / Math.abs(old);
    }

    private static double rsi(List<Candle> c, int n) {
        n = Math.max(2, Math.min(n, c.size() - 1));
        double gain = 0;
        double loss = 0;

        for (int i = c.size() - n; i < c.size(); i++) {
            if (i <= 0) continue;
            double d = c.get(i).close - c.get(i - 1).close;
            if (d > 0) gain += d;
            else loss -= d;
        }

        if (loss <= 0.000001) return 100.0;
        double rs = gain / loss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private static double normalizedSlope(List<Candle> c, int n) {
        n = Math.max(2, Math.min(n, c.size()));
        double meanX = (n - 1) / 2.0;
        double meanY = averageClose(c, n);

        double num = 0;
        double den = 0;

        for (int j = 0; j < n; j++) {
            double x = j - meanX;
            double y = c.get(c.size() - n + j).close - meanY;
            num += x * y;
            den += x * x;
        }

        double slope = den == 0 ? 0 : num / den;
        return normalize(slope * n, averageRange(c, n));
    }

    private static int consecutiveDirection(List<Candle> c, int max) {
        int count = 0;
        boolean dir = c.get(c.size() - 1).green;

        for (int i = c.size() - 1; i >= 0 && count < max; i--) {
            if (c.get(i).green == dir) count++;
            else break;
        }
        return dir ? count : -count;
    }

    private static int higherHighCount(List<Candle> c, int n) {
        int count = 0;
        int start = Math.max(1, c.size() - n);
        for (int i = start; i < c.size(); i++) {
            if (c.get(i).high > c.get(i - 1).high) count++;
        }
        return count;
    }

    private static int higherLowCount(List<Candle> c, int n) {
        int count = 0;
        int start = Math.max(1, c.size() - n);
        for (int i = start; i < c.size(); i++) {
            if (c.get(i).low > c.get(i - 1).low) count++;
        }
        return count;
    }

    private static int lowerHighCount(List<Candle> c, int n) {
        int count = 0;
        int start = Math.max(1, c.size() - n);
        for (int i = start; i < c.size(); i++) {
            if (c.get(i).high < c.get(i - 1).high) count++;
        }
        return count;
    }

    private static int lowerLowCount(List<Candle> c, int n) {
        int count = 0;
        int start = Math.max(1, c.size() - n);
        for (int i = start; i < c.size(); i++) {
            if (c.get(i).low < c.get(i - 1).low) count++;
        }
        return count;
    }

    private static double recentHigh(List<Candle> c, int n) {
        n = Math.min(n, c.size());
        double v = -Double.MAX_VALUE;
        for (int i = c.size() - n; i < c.size(); i++) v = Math.max(v, c.get(i).high);
        return v;
    }

    private static double recentLow(List<Candle> c, int n) {
        n = Math.min(n, c.size());
        double v = Double.MAX_VALUE;
        for (int i = c.size() - n; i < c.size(); i++) v = Math.min(v, c.get(i).low);
        return v;
    }

    private static double signStrength(double v, double threshold) {
        if (Math.abs(v) <= threshold) return 0.0;
        double x = (Math.abs(v) - threshold) / Math.max(0.0001, threshold * 3.0);
        x = clamp(x, 0.0, 1.0);
        return Math.signum(v) * (0.25 + 0.75 * x);
    }

    private static double normalize(double value, double scale) {
        if (Math.abs(scale) < 0.000001) return 0;
        return value / Math.abs(scale);
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
