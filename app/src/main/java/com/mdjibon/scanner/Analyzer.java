package com.mdjibon.scanner;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic chart-image analyzer.
 *
 * IMPORTANT:
 * This analyzer does not use Math.random().
 * The 100 checks are calculated from the captured chart image.
 *
 * Because a screenshot does not contain the broker's real OHLC feed,
 * candles are reconstructed approximately from the visible chart pixels.
 */
public class Analyzer {

    public static class Result {
        public String signal = "NO TRADE";
        public int confidence = 0;
        public int quality = 0;
        public int bullish = 0;
        public int bearish = 0;
        public int neutral = 0;
        public int detectedCandles = 0;
        public int evaluatedRules = 0;
        public String timeframe = "1 MIN";
        public String candleSize = "UNKNOWN";
        public List<String> checks = new ArrayList<>();
    }

    private static class Candle {
        double open;
        double high;
        double low;
        double close;

        Candle(double open, double high, double low, double close) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }

        double body() {
            return Math.abs(close - open);
        }

        double range() {
            return Math.max(0.0001, high - low);
        }

        boolean bullish() {
            return close > open;
        }

        boolean bearish() {
            return close < open;
        }
    }

    private static class Score {
        int up;
        int down;
        int neutral;
    }

    public static Result analyze(Bitmap bitmap) {
        Result result = new Result();

        if (bitmap == null ||
                bitmap.isRecycled() ||
                bitmap.getWidth() < 200 ||
                bitmap.getHeight() < 150) {
            result.signal = "NO TRADE";
            result.quality = 0;
            result.evaluatedRules = 0;
            return result;
        }

        List<Candle> candles = extractCandles(bitmap);

        result.detectedCandles = candles.size();
        result.quality = calculateImageQuality(bitmap, candles);

        if (candles.size() < 10) {
            result.signal = "NO TRADE";
            result.confidence = 0;
            result.evaluatedRules = 0;
            return result;
        }

        double[] close = closes(candles);
        double[] open = opens(candles);
        double[] high = highs(candles);
        double[] low = lows(candles);

        /*
         * Exactly 100 checks.
         * Each check is evaluated from the reconstructed chart data.
         */
        for (int rule = 1; rule <= 100; rule++) {
            Score s = evaluateRule(
                    rule,
                    candles,
                    open,
                    high,
                    low,
                    close
            );

            result.bullish += s.up;
            result.bearish += s.down;
            result.neutral += s.neutral;
            result.evaluatedRules++;

            if (s.up > 0) {
                result.checks.add("Rule " + rule + ": UP");
            } else if (s.down > 0) {
                result.checks.add("Rule " + rule + ": DOWN");
            } else {
                result.checks.add("Rule " + rule + ": NEUTRAL");
            }
        }

        /*
         * Do not claim 100 unless the loop actually evaluated 100.
         */
        if (result.evaluatedRules != 100) {
            result.signal = "NO TRADE";
            result.confidence = 0;
            return result;
        }

        int directional = result.bullish + result.bearish;
        int difference = Math.abs(
                result.bullish - result.bearish
        );

        if (directional < 12) {
            result.signal = "NO TRADE";
            result.confidence = 0;
            return result;
        }

        int confidence = (difference * 100) /
                Math.max(1, directional);

        /*
         * A screenshot-based signal must be conservative.
         */
        if (confidence < 18) {
            result.signal = "NO TRADE";
            result.confidence = confidence;
        } else if (result.bullish > result.bearish) {
            result.signal = "UP";
            result.confidence = Math.min(100, confidence);
        } else if (result.bearish > result.bullish) {
            result.signal = "DOWN";
            result.confidence = Math.min(100, confidence);
        } else {
            result.signal = "NO TRADE";
            result.confidence = 0;
        }

        return result;
    }

    private static Score evaluateRule(
            int rule,
            List<Candle> c,
            double[] o,
            double[] h,
            double[] l,
            double[] close
    ) {
        Score s = new Score();

        switch (rule) {

            // -------------------------------------------------
            // 1-10: PRICE / MOVING AVERAGES
            // -------------------------------------------------
            case 1:
                vote(s, close[last(close)] > sma(close, 3));
                break;
            case 2:
                vote(s, close[last(close)] > sma(close, 5));
                break;
            case 3:
                vote(s, close[last(close)] > sma(close, 8));
                break;
            case 4:
                vote(s, close[last(close)] > sma(close, 13));
                break;
            case 5:
                vote(s, close[last(close)] > sma(close, 21));
                break;
            case 6:
                vote(s, sma(close, 3) > sma(close, 5));
                break;
            case 7:
                vote(s, sma(close, 5) > sma(close, 8));
                break;
            case 8:
                vote(s, sma(close, 8) > sma(close, 13));
                break;
            case 9:
                vote(s, ema(close, 9) > ema(close, 21));
                break;
            case 10:
                vote(s, ema(close, 21) > ema(close, 34));
                break;

            // -------------------------------------------------
            // 11-20: EMA / TREND
            // -------------------------------------------------
            case 11:
                vote(s, ema(close, 5) > ema(close, 9));
                break;
            case 12:
                vote(s, ema(close, 9) > ema(close, 13));
                break;
            case 13:
                vote(s, ema(close, 13) > ema(close, 21));
                break;
            case 14:
                vote(s, ema(close, 21) > sma(close, 21));
                break;
            case 15:
                vote(s, slope(close, 5) > 0);
                break;
            case 16:
                vote(s, slope(close, 8) > 0);
                break;
            case 17:
                vote(s, slope(close, 13) > 0);
                break;
            case 18:
                vote(s, slope(close, 21) > 0);
                break;
            case 19:
                vote(s, momentum(close, 3) > 0);
                break;
            case 20:
                vote(s, momentum(close, 5) > 0);
                break;

            // -------------------------------------------------
            // 21-30: MOMENTUM / ROC / RSI
            // -------------------------------------------------
            case 21:
                vote(s, momentum(close, 8) > 0);
                break;
            case 22:
                vote(s, momentum(close, 13) > 0);
                break;
            case 23:
                vote(s, roc(close, 3) > 0);
                break;
            case 24:
                vote(s, roc(close, 5) > 0);
                break;
            case 25:
                vote(s, roc(close, 8) > 0);
                break;
            case 26:
                voteRsi(s, rsi(close, 7));
                break;
            case 27:
                voteRsi(s, rsi(close, 14));
                break;
            case 28:
                voteRsi(s, rsi(close, 21));
                break;
            case 29:
                voteRsiMomentum(s, rsi(close, 14), rsiPrevious(close, 14));
                break;
            case 30:
                voteRsiZone(s, rsi(close, 14));
                break;

            // -------------------------------------------------
            // 31-40: MACD / STOCHASTIC
            // -------------------------------------------------
            case 31:
                vote(s, macd(close, 12, 26, 9) > 0);
                break;
            case 32:
                vote(s, macd(close, 5, 13, 5) > 0);
                break;
            case 33:
                vote(s, macd(close, 8, 21, 5) > 0);
                break;
            case 34:
                vote(s, macdMomentum(close));
                break;
            case 35:
                voteStochastic(s, stochastic(close, 9));
                break;
            case 36:
                voteStochastic(s, stochastic(close, 14));
                break;
            case 37:
                voteStochastic(s, stochastic(close, 21));
                break;
            case 38:
                voteStochasticMomentum(s, close);
                break;
            case 39:
                vote(s, stochastic(close, 14) > 50);
                break;
            case 40:
                vote(s, stochastic(close, 14) < 50);
                break;

            // -------------------------------------------------
            // 41-50: BOLLINGER / VOLATILITY
            // -------------------------------------------------
            case 41:
                vote(s, close[last(close)] > bbMiddle(close, 20));
                break;
            case 42:
                vote(s, close[last(close)] < bbMiddle(close, 20));
                break;
            case 43:
                vote(s, close[last(close)] > bbUpper(close, 20, 2.0));
                break;
            case 44:
                vote(s, close[last(close)] < bbLower(close, 20, 2.0));
                break;
            case 45:
                voteVolatility(s, volatility(close, 10));
                break;
            case 46:
                voteVolatility(s, volatility(close, 20));
                break;
            case 47:
                vote(s, volatility(close, 10) > volatilityPrevious(close, 10));
                break;
            case 48:
                vote(s, averageBody(c) > averageBodyPrevious(c));
                break;
            case 49:
                vote(s, range(c.get(c.size() - 1)) >
                        averageRange(c, 14));
                break;
            case 50:
                vote(s, range(c.get(c.size() - 1)) <
                        averageRange(c, 14) * 0.75);
                break;

            // -------------------------------------------------
            // 51-60: CANDLE DIRECTION / BODY
            // -------------------------------------------------
            case 51:
                vote(s, lastCandle(c).bullish());
                break;
            case 52:
                vote(s, lastCandle(c).bearish());
                break;
            case 53:
                vote(s, consecutiveBullish(c, 2));
                break;
            case 54:
                vote(s, consecutiveBearish(c, 2));
                break;
            case 55:
                vote(s, consecutiveBullish(c, 3));
                break;
            case 56:
                vote(s, consecutiveBearish(c, 3));
                break;
            case 57:
                vote(s, bodyRatio(lastCandle(c)) > 0.65);
                break;
            case 58:
                vote(s, bodyRatio(lastCandle(c)) < 0.30);
                break;
            case 59:
                vote(s, close[last(close)] >
                        close[Math.max(0, close.length - 2)]);
                break;
            case 60:
                vote(s, close[last(close)] <
                        close[Math.max(0, close.length - 2)]);
                break;

            // -------------------------------------------------
            // 61-70: WICK / REVERSAL PATTERNS
            // -------------------------------------------------
            case 61:
                vote(s, lowerWick(c) > upperWick(c) * 1.5);
                break;
            case 62:
                vote(s, upperWick(c) > lowerWick(c) * 1.5);
                break;
            case 63:
                vote(s, lowerWick(c) > lastCandle(c).body() * 2);
                break;
            case 64:
                vote(s, upperWick(c) > lastCandle(c).body() * 2);
                break;
            case 65:
                vote(s, bullishEngulfing(c));
                break;
            case 66:
                vote(s, bearishEngulfing(c));
                break;
            case 67:
                vote(s, hammer(c));
                break;
            case 68:
                vote(s, shootingStar(c));
                break;
            case 69:
                vote(s, doji(c));
                break;
            case 70:
                vote(s, insideBarBreak(c));
                break;

            // -------------------------------------------------
            // 71-80: MARKET STRUCTURE
            // -------------------------------------------------
            case 71:
                vote(s, higherHigh(c));
                break;
            case 72:
                vote(s, lowerHigh(c));
                break;
            case 73:
                vote(s, higherLow(c));
                break;
            case 74:
                vote(s, lowerLow(c));
                break;
            case 75:
                vote(s, close[last(close)] > highest(close, 10, 1));
                break;
            case 76:
                vote(s, close[last(close)] < lowest(close, 10, 1));
                break;
            case 77:
                vote(s, close[last(close)] > highest(close, 20, 1));
                break;
            case 78:
                vote(s, close[last(close)] < lowest(close, 20, 1));
                break;
            case 79:
                vote(s, trendStrength(close, 10) > 0);
                break;
            case 80:
                vote(s, trendStrength(close, 10) < 0);
                break;

            // -------------------------------------------------
            // 81-90: SUPPORT / RESISTANCE / ATR
            // -------------------------------------------------
            case 81:
                vote(s, close[last(close)] >
                        lowest(close, 20, 0));
                break;
            case 82:
                vote(s, close[last(close)] <
                        highest(close, 20, 0));
                break;
            case 83:
                vote(s, close[last(close)] >
                        averageRange(c, 14) + sma(close, 14));
                break;
            case 84:
                vote(s, close[last(close)] <
                        sma(close, 14) - averageRange(c, 14));
                break;
            case 85:
                vote(s, atr(c, 14) > atrPrevious(c, 14));
                break;
            case 86:
                vote(s, atr(c, 14) < atrPrevious(c, 14));
                break;
            case 87:
                vote(s, trueRange(c, last(c)) >
                        averageTrueRange(c, 14));
                break;
            case 88:
                vote(s, trueRange(c, last(c)) <
                        averageTrueRange(c, 14));
                break;
            case 89:
                vote(s, close[last(close)] >
                        sma(close, 34));
                break;
            case 90:
                vote(s, close[last(close)] <
                        sma(close, 34));
                break;

            // -------------------------------------------------
            // 91-100: FINAL CONFLUENCE
            // -------------------------------------------------
            case 91:
                vote(s, ema(close, 9) > ema(close, 21) &&
                        rsi(close, 14) > 50);
                break;
            case 92:
                vote(s, ema(close, 9) < ema(close, 21) &&
                        rsi(close, 14) < 50);
                break;
            case 93:
                vote(s, macd(close, 12, 26, 9) > 0 &&
                        stochastic(close, 14) > 50);
                break;
            case 94:
                vote(s, macd(close, 12, 26, 9) < 0 &&
                        stochastic(close, 14) < 50);
                break;
            case 95:
                vote(s, slope(close, 5) > 0 &&
                        slope(close, 13) > 0);
                break;
            case 96:
                vote(s, slope(close, 5) < 0 &&
                        slope(close, 13) < 0);
                break;
            case 97:
                vote(s, bullishEngulfing(c) ||
                        hammer(c) ||
                        higherLow(c));
                break;
            case 98:
                vote(s, bearishEngulfing(c) ||
                        shootingStar(c) ||
                        lowerHigh(c));
                break;
            case 99:
                vote(s, compositeBullish(c, close));
                break;
            case 100:
                vote(s, compositeBearish(c, close));
                break;
        }

        return s;
    }

    private static void vote(Score s, boolean up) {
        if (up) {
            s.up = 1;
        } else {
            s.down = 1;
        }
    }

    private static void voteRsi(Score s, double value) {
        if (value > 50) {
            s.up = 1;
        } else if (value < 50) {
            s.down = 1;
        } else {
            s.neutral = 1;
        }
    }

    private static void voteRsiMomentum(
            Score s,
            double current,
            double previous
    ) {
        if (current > previous) {
            s.up = 1;
        } else if (current < previous) {
            s.down = 1;
        } else {
            s.neutral = 1;
        }
    }

    private static void voteRsiZone(Score s, double value) {
        if (value < 35) {
            s.up = 1;
        } else if (value > 65) {
            s.down = 1;
        } else if (value > 50) {
            s.up = 1;
        } else if (value < 50) {
            s.down = 1;
        } else {
            s.neutral = 1;
        }
    }

    private static void voteStochastic(Score s, double value) {
        if (value < 20) {
            s.up = 1;
        } else if (value > 80) {
            s.down = 1;
        } else if (value > 50) {
            s.up = 1;
        } else if (value < 50) {
            s.down = 1;
        } else {
            s.neutral = 1;
        }
    }

    private static void voteStochasticMomentum(
            Score s,
            double[] close
    ) {
        double now = stochastic(close, 14);
        double prev = stochasticPrevious(close, 14);

        if (now > prev) {
            s.up = 1;
        } else if (now < prev) {
            s.down = 1;
        } else {
            s.neutral = 1;
        }
    }

    private static void voteVolatility(
            Score s,
            double value
    ) {
        if (value > 0) {
            s.up = 1;
        } else {
            s.neutral = 1;
        }
    }

    /*
     * ---------------------------------------------------------
     * IMAGE -> APPROXIMATE CANDLE EXTRACTION
     * ---------------------------------------------------------
     */
    private static List<Candle> extractCandles(Bitmap bmp) {

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        /*
         * Ignore the extreme edges where mobile trading apps
         * commonly have buttons, menus and status overlays.
         */
        int left = (int) (w * 0.08);
        int right = (int) (w * 0.94);
        int top = (int) (h * 0.12);
        int bottom = (int) (h * 0.88);

        int usableWidth = Math.max(1, right - left);

        /*
         * Try approximately 40 candle columns.
         */
        int candleWidth = Math.max(
                4,
                usableWidth / 42
        );

        List<Candle> result = new ArrayList<>();

        for (int x0 = left; x0 < right; x0 += candleWidth) {

            int x1 = Math.min(
                    right,
                    x0 + candleWidth
            );

            List<Integer> coloredY = new ArrayList<>();
            int green = 0;
            int red = 0;

            for (int x = x0; x < x1; x++) {

                for (int y = top; y < bottom; y += 2) {

                    int pixel = bmp.getPixel(x, y);

                    int r = (pixel >> 16) & 255;
                    int g = (pixel >> 8) & 255;
                    int b = pixel & 255;

                    boolean greenPixel =
                            g > r * 1.18 &&
                            g > b * 1.05 &&
                            g > 70;

                    boolean redPixel =
                            r > g * 1.18 &&
                            r > b * 1.10 &&
                            r > 70;

                    if (greenPixel || redPixel) {
                        coloredY.add(y);

                        if (greenPixel) {
                            green++;
                        }

                        if (redPixel) {
                            red++;
                        }
                    }
                }
            }

            if (coloredY.size() < 3) {
                continue;
            }

            Collections.sort(coloredY);

            int lowY = coloredY.get(0);
            int highY = coloredY.get(
                    coloredY.size() - 1
            );

            int minGreenRedY = lowY;
            int maxGreenRedY = highY;

            double highPrice =
                    bottom - minGreenRedY;

            double lowPrice =
                    bottom - maxGreenRedY;

            if (highPrice <= lowPrice) {
                continue;
            }

            /*
             * Determine approximate candle body.
             */
            int mid = coloredY.size() / 2;

            int bodyTop =
                    coloredY.get(
                            Math.max(
                                    0,
                                    mid - coloredY.size() / 5
                            )
                    );

            int bodyBottom =
                    coloredY.get(
                            Math.min(
                                    coloredY.size() - 1,
                                    mid + coloredY.size() / 5
                            )
                    );

            double close;
            double open;

            if (green >= red) {
                close = bottom - bodyTop;
                open = bottom - bodyBottom;
            } else {
                open = bottom - bodyTop;
                close = bottom - bodyBottom;
            }

            /*
             * Compress values into a stable chart-price scale.
             */
            double scale = 1000.0 / Math.max(1, h);

            result.add(
                    new Candle(
                            open * scale,
                            highPrice * scale,
                            lowPrice * scale,
                            close * scale
                    )
            );
        }

        /*
         * If too many tiny segments were detected, merge by
         * taking evenly spaced samples.
         */
        if (result.size() > 60) {
            List<Candle> reduced = new ArrayList<>();

            double step =
                    (double) result.size() / 50.0;

            for (int i = 0; i < 50; i++) {
                int index =
                        Math.min(
                                result.size() - 1,
                                (int) (i * step)
                        );

                reduced.add(result.get(index));
            }

            return reduced;
        }

        return result;
    }

    private static int calculateImageQuality(
            Bitmap bmp,
            List<Candle> candles
    ) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int samples = 0;
        int chartPixels = 0;

        int stepX = Math.max(1, w / 80);
        int stepY = Math.max(1, h / 80);

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {

                int pixel = bmp.getPixel(x, y);

                int r = (pixel >> 16) & 255;
                int g = (pixel >> 8) & 255;
                int b = pixel & 255;

                samples++;

                if ((g > r * 1.15 && g > 60) ||
                        (r > g * 1.15 && r > 60)) {
                    chartPixels++;
                }
            }
        }

        int colorScore =
                samples == 0
                        ? 0
                        : (chartPixels * 100) / samples;

        int candleScore =
                Math.min(
                        100,
                        candles.size() * 2
                );

        return Math.min(
                100,
                (colorScore + candleScore) / 2
        );
    }

    /*
     * ---------------------------------------------------------
     * ARRAY HELPERS
     * ---------------------------------------------------------
     */

    private static double[] closes(List<Candle> c) {
        double[] a = new double[c.size()];
        for (int i = 0; i < c.size(); i++) {
            a[i] = c.get(i).close;
        }
        return a;
    }

    private static double[] opens(List<Candle> c) {
        double[] a = new double[c.size()];
        for (int i = 0; i < c.size(); i++) {
            a[i] = c.get(i).open;
        }
        return a;
    }

    private static double[] highs(List<Candle> c) {
        double[] a = new double[c.size()];
        for (int i = 0; i < c.size(); i++) {
            a[i] = c.get(i).high;
        }
        return a;
    }

    private static double[] lows(List<Candle> c) {
        double[] a = new double[c.size()];
        for (int i = 0; i < c.size(); i++) {
            a[i] = c.get(i).low;
        }
        return a;
    }

    private static int last(double[] a) {
        return Math.max(0, a.length - 1);
    }

    private static Candle lastCandle(List<Candle> c) {
        return c.get(c.size() - 1);
    }

    private static double sma(
            double[] a,
            int n
    ) {
        n = Math.min(n, a.length);

        double sum = 0;

        for (int i = a.length - n; i < a.length; i++) {
            sum += a[i];
        }

        return sum / Math.max(1, n);
    }

    private static double ema(
            double[] a,
            int n
    ) {
        n = Math.max(2, Math.min(n, a.length));

        double alpha = 2.0 / (n + 1.0);
        double value = a[0];

        for (int i = 1; i < a.length; i++) {
            value =
                    alpha * a[i] +
                            (1.0 - alpha) * value;
        }

        return value;
    }

    private static double slope(
            double[] a,
            int n
    ) {
        n = Math.min(n, a.length);

        if (n < 2) {
            return 0;
        }

        return (
                a[a.length - 1] -
                        a[a.length - n]
        ) / n;
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

        double previous =
                a[a.length - 1 - n];

        if (Math.abs(previous) < 0.000001) {
            return 0;
        }

        return (
                (a[a.length - 1] - previous) /
                        Math.abs(previous)
        ) * 100.0;
    }

    private static double rsi(
            double[] a,
            int n
    ) {
        n = Math.min(n, a.length - 1);

        if (n < 2) {
            return 50;
        }

        double gain = 0;
        double loss = 0;

        int start = a.length - n;

        for (int i = start; i < a.length; i++) {
            double change =
                    a[i] - a[i - 1];

            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }

        if (loss == 0) {
            return 100;
        }

        double rs =
                gain / loss;

        return 100.0 -
                (100.0 / (1.0 + rs));
    }

    private static double rsiPrevious(
            double[] a,
            int n
    ) {
        if (a.length < n + 3) {
            return rsi(a, n);
        }

        double[] shortened =
                new double[a.length - 1];

        System.arraycopy(
                a,
                0,
                shortened,
                0,
                shortened.length
        );

        return rsi(shortened, n);
    }

    private static double macd(
            double[] a,
            int fast,
            int slow,
            int signal
    ) {
        return ema(a, fast) -
                ema(a, slow);
    }

    private static boolean macdMomentum(
            double[] a
    ) {
        if (a.length < 4) {
            return false;
        }

        double now =
                macd(a, 12, 26, 9);

        double[] old =
                new double[a.length - 2];

        System.arraycopy(
                a,
                0,
                old,
                0,
                old.length
        );

        double previous =
                macd(old, 12, 26, 9);

        return now > previous;
    }

    private static double stochastic(
            double[] a,
            int n
    ) {
        n = Math.min(n, a.length);

        double highest =
                highest(a, n, 0);

        double lowest =
                lowest(a, n, 0);

        if (highest == lowest) {
            return 50;
        }

        return (
                (a[a.length - 1] - lowest) /
                        (highest - lowest)
        ) * 100.0;
    }

    private static double stochasticPrevious(
            double[] a,
            int n
    ) {
        if (a.length < n + 2) {
            return stochastic(a, n);
        }

        double[] old =
                new double[a.length - 1];

        System.arraycopy(
                a,
                0,
                old,
                0,
                old.length
        );

        return stochastic(old, n);
    }

    private static double bbMiddle(
            double[] a,
            int n
    ) {
        return sma(a, n);
    }

    private static double bbDeviation(
            double[] a,
            int n
    ) {
        n = Math.min(n, a.length);

        double mean =
                sma(a, n);

        double sum = 0;

        for (int i = a.length - n; i < a.length; i++) {
            double d =
                    a[i] - mean;
            sum += d * d;
        }

        return Math.sqrt(
                sum / Math.max(1, n)
        );
    }

    private static double bbUpper(
            double[] a,
            int n,
            double multiplier
    ) {
        return bbMiddle(a, n) +
                multiplier *
                        bbDeviation(a, n);
    }

    private static double bbLower(
            double[] a,
            int n,
            double multiplier
    ) {
        return bbMiddle(a, n) -
                multiplier *
                        bbDeviation(a, n);
    }

    private static double volatility(
            double[] a,
            int n
    ) {
        return bbDeviation(a, n);
    }

    private static double volatilityPrevious(
            double[] a,
            int n
    ) {
        if (a.length < n + 2) {
            return volatility(a, n);
        }

        double[] old =
                new double[a.length - 1];

        System.arraycopy(
                a,
                0,
                old,
                0,
                old.length
        );

        return volatility(old, n);
    }

    /*
     * ---------------------------------------------------------
     * CANDLE HELPERS
     * ---------------------------------------------------------
     */

    private static double range(Candle c) {
        return c.high - c.low;
    }

    private static double bodyRatio(Candle c) {
        return c.body() /
                Math.max(0.0001, c.range());
    }

    private static double upperWick(
            List<Candle> c
    ) {
        Candle x = lastCandle(c);

        return x.high -
                Math.max(x.open, x.close);
    }

    private static double lowerWick(
            List<Candle> c
    ) {
        Candle x = lastCandle(c);

        return Math.min(x.open, x.close) -
                x.low;
    }

    private static boolean consecutiveBullish(
            List<Candle> c,
            int n
    ) {
        if (c.size() < n) {
            return false;
        }

        for (int i = c.size() - n; i < c.size(); i++) {
            if (!c.get(i).bullish()) {
                return false;
            }
        }

        return true;
    }

    private static boolean consecutiveBearish(
            List<Candle> c,
            int n
    ) {
        if (c.size() < n) {
            return false;
        }

        for (int i = c.size() - n; i < c.size(); i++) {
            if (!c.get(i).bearish()) {
                return false;
            }
        }

        return true;
    }

    private static boolean bullishEngulfing(
            List<Candle> c
    ) {
        if (c.size() < 2) {
            return false;
        }

        Candle p = c.get(c.size() - 2);
        Candle n = c.get(c.size() - 1);

        return p.bearish() &&
                n.bullish() &&
                n.open <= p.close &&
                n.close >= p.open;
    }

    private static boolean bearishEngulfing(
            List<Candle> c
    ) {
        if (c.size() < 2) {
            return false;
        }

        Candle p = c.get(c.size() - 2);
        Candle n = c.get(c.size() - 1);

        return p.bullish() &&
                n.bearish() &&
                n.open >= p.close &&
                n.close <= p.open;
    }

    private static boolean hammer(
            List<Candle> c
    ) {
        Candle x = lastCandle(c);

        return lowerWick(c) >
                x.body() * 2.0 &&
                upperWick(c) <
                        x.body() * 0.8;
    }

    private static boolean shootingStar(
            List<Candle> c
    ) {
        Candle x = lastCandle(c);

        return upperWick(c) >
                x.body() * 2.0 &&
                lowerWick(c) <
                        x.body() * 0.8;
    }

    private static boolean doji(
            List<Candle> c
    ) {
        Candle x = lastCandle(c);

        return bodyRatio(x) < 0.12;
    }

    private static boolean insideBarBreak(
            List<Candle> c
    ) {
        if (c.size() < 3) {
            return false;
        }

        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle n = c.get(c.size() - 1);

        boolean inside =
                b.high <= a.high &&
                        b.low >= a.low;

        return inside &&
                (n.close > a.high ||
                        n.close < a.low);
    }

    private static boolean higherHigh(
            List<Candle> c
    ) {
        if (c.size() < 4) {
            return false;
        }

        return lastCandle(c).high >
                c.get(c.size() - 3).high;
    }

    private static boolean lowerHigh(
            List<Candle> c
    ) {
        if (c.size() < 4) {
            return false;
        }

        return lastCandle(c).high <
                c.get(c.size() - 3).high;
    }

    private static boolean higherLow(
            List<Candle> c
    ) {
        if (c.size() < 4) {
            return false;
        }

        return lastCandle(c).low >
                c.get(c.size() - 3).low;
    }

    private static boolean lowerLow(
            List<Candle> c
    ) {
        if (c.size() < 4) {
            return false;
        }

        return lastCandle(c).low <
                c.get(c.size() - 3).low;
    }

    private static double highest(
            double[] a,
            int n,
            int excludeLast
    ) {
        int end =
                a.length - excludeLast;

        int start =
                Math.max(0, end - n);

        double value =
                -Double.MAX_VALUE;

        for (int i = start; i < end; i++) {
            value =
                    Math.max(value, a[i]);
        }

        return value;
    }

    private static double lowest(
            double[] a,
            int n,
            int excludeLast
    ) {
        int end =
                a.length - excludeLast;

        int start =
                Math.max(0, end - n);

        double value =
                Double.MAX_VALUE;

        for (int i = start; i < end; i++) {
            value =
                    Math.min(value, a[i]);
        }

        return value;
    }

    private static double trendStrength(
            double[] a,
            int n
    ) {
        return slope(a, n);
    }

    private static double averageRange(
            List<Candle> c,
            int n
    ) {
        n = Math.min(n, c.size());

        double sum = 0;

        for (int i = c.size() - n; i < c.size(); i++) {
            sum += c.get(i).range();
        }

        return sum / Math.max(1, n);
    }

    private static double averageTrueRange(
            List<Candle> c,
            int n
    ) {
        return averageRange(c, n);
    }

    private static double atr(
            List<Candle> c,
            int n
    ) {
        return averageTrueRange(c, n);
    }

    private static double atrPrevious(
            List<Candle> c,
            int n
    ) {
        if (c.size() < n + 2) {
            return atr(c, n);
        }

        double sum = 0;
        int end = c.size() - 1;
        int start = Math.max(0, end - n);

        for (int i = start; i < end; i++) {
            sum += c.get(i).range();
        }

        return sum / Math.max(1, end - start);
    }

    private static double trueRange(
            List<Candle> c,
            Candle current
    ) {
        if (c.size() < 2) {
            return current.range();
        }

        Candle previous =
                c.get(c.size() - 2);

        double a =
                current.high - current.low;

        double b =
                Math.abs(
                        current.high -
                                previous.close
                );

        double d =
                Math.abs(
                        current.low -
                                previous.close
                );

        return Math.max(
                a,
                Math.max(b, d)
        );
    }

    private static double averageBody(
            List<Candle> c
    ) {
        int n =
                Math.min(10, c.size());

        double sum = 0;

        for (int i = c.size() - n; i < c.size(); i++) {
            sum += c.get(i).body();
        }

        return sum / Math.max(1, n);
    }

    private static double averageBodyPrevious(
            List<Candle> c
    ) {
        if (c.size() < 12) {
            return averageBody(c);
        }

        int end = c.size() - 1;
        int start = Math.max(0, end - 10);

        double sum = 0;

        for (int i = start; i < end; i++) {
            sum += c.get(i).body();
        }

        return sum / Math.max(1, end - start);
    }

    private static boolean compositeBullish(
            List<Candle> c,
            double[] close
    ) {
        int score = 0;

        if (ema(close, 9) > ema(close, 21)) score++;
        if (rsi(close, 14) > 50) score++;
        if (macd(close, 12, 26, 9) > 0) score++;
        if (stochastic(close, 14) > 50) score++;
        if (slope(close, 13) > 0) score++;
        if (higherLow(c)) score++;

        return score >= 4;
    }

    private static boolean compositeBearish(
            List<Candle> c,
            double[] close
    ) {
        int score = 0;

        if (ema(close, 9) < ema(close, 21)) score++;
        if (rsi(close, 14) < 50) score++;
        if (macd(close, 12, 26, 9) < 0) score++;
        if (stochastic(close, 14) < 50) score++;
        if (slope(close, 13) < 0) score++;
        if (lowerHigh(c)) score++;

        return score >= 4;
    }
}
