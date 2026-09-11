package com.mdjibon.scanner;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MD JIBON 99% Screen Scanner
 *
 * Deterministic screenshot-based technical/price-action analyzer.
 *
 * IMPORTANT:
 * This analyzer does NOT use broker API/OHLC data.
 * It reconstructs approximate candles from the visible chart pixels
 * and evaluates 100 deterministic trading rules.
 *
 * No Math.random() is used.
 */
public final class Analyzer {

    private Analyzer() {
    }

    // ============================================================
    // RESULT
    // ============================================================

    public static final class Result {

        public String signal = "NO TRADE";
        public double confidence = 0.0;
        public double quality = 0.0;

        public int bullish = 0;
        public int bearish = 0;
        public int neutral = 0;

        public int detectedCandles = 0;
        public int evaluatedRules = 0;

        public String timeframe = "SCREEN";
        public String candleSize = "UNKNOWN";

        public final List<String> checks = new ArrayList<>();
    }

    // ============================================================
    // CANDLE
    // ============================================================

    private static final class Candle {

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
            return Math.max(0.000001, high - low);
        }

        double upperWick() {
            return Math.max(0.0, high - Math.max(open, close));
        }

        double lowerWick() {
            return Math.max(0.0, Math.min(open, close) - low);
        }

        boolean bullish() {
            return close > open;
        }

        boolean bearish() {
            return close < open;
        }

        boolean doji() {
            return body() <= range() * 0.12;
        }

        double bodyRatio() {
            return body() / range();
        }

        double closeLocation() {
            return (close - low) / range();
        }
    }

    // ============================================================
    // SCORE
    // ============================================================

    private static final class Score {
        int up;
        int down;
        int neutral;
    }

    // ============================================================
    // PUBLIC ENTRY
    // ============================================================

    public static Result analyze(Bitmap bitmap) {

        Result result = new Result();

        if (bitmap == null || bitmap.isRecycled()) {
            result.signal = "NO TRADE";
            result.quality = 0;
            return result;
        }

        List<Candle> candles = extractCandles(bitmap);

        result.detectedCandles = candles.size();

        if (candles.size() < 10) {
            result.signal = "NO TRADE";
            result.quality = 0;
            result.evaluatedRules = 0;
            return result;
        }

        result.quality = calculateChartQuality(bitmap, candles);

        if (result.quality < 20.0) {
            result.signal = "NO TRADE";
            result.evaluatedRules = 0;
            return result;
        }

        Score score = new Score();

        /*
         * EXACTLY 100 RULES
         */
        for (int rule = 1; rule <= 100; rule++) {

            int vote = evaluateRule(rule, candles);

            if (vote > 0) {
                score.up++;
                result.bullish++;
                result.checks.add("R" + rule + ": UP");
            } else if (vote < 0) {
                score.down++;
                result.bearish++;
                result.checks.add("R" + rule + ": DOWN");
            } else {
                score.neutral++;
                result.neutral++;
                result.checks.add("R" + rule + ": NEUTRAL");
            }

            result.evaluatedRules++;
        }

        /*
         * Never return a signal if all 100 rules were not evaluated.
         */
        if (result.evaluatedRules != 100) {
            result.signal = "NO TRADE";
            result.confidence = 0;
            return result;
        }

        /*
         * Confidence is based on directional agreement.
         * This is NOT a guaranteed win percentage.
         */
        int directional = score.up + score.down;

        if (directional <= 0) {
            result.signal = "NO TRADE";
            result.confidence = 0;
            return result;
        }

        int difference = Math.abs(score.up - score.down);

        result.confidence =
                (difference * 100.0) / directional;

        /*
         * Require a meaningful majority.
         * Otherwise psychology is considered mixed.
         */
        if (score.up >= 60 && score.up > score.down + 8) {
            result.signal = "UP";
        } else if (score.down >= 60 && score.down > score.up + 8) {
            result.signal = "DOWN";
        } else {
            result.signal = "NO TRADE";
        }

        result.timeframe = "SCREEN";
        result.candleSize = candles.size() + " candles";

        return result;
    }

    // ============================================================
    // 100 REAL DETERMINISTIC RULES
    // ============================================================

    private static int evaluateRule(int rule, List<Candle> c) {

        Candle last = lastCandle(c);
        Candle prev = c.get(c.size() - 2);

        switch (rule) {

            // ----------------------------------------------------
            // 1 - 10 : BASIC PRICE ACTION / CANDLE PSYCHOLOGY
            // ----------------------------------------------------

            case 1:
                return direction(last);

            case 2:
                return bodyDominance(c);

            case 3:
                return closeNearHighLow(last);

            case 4:
                return wickPressure(last);

            case 5:
                return consecutiveDirection(c, 3);

            case 6:
                return consecutiveDirection(c, 5);

            case 7:
                return recentMomentum(c, 3);

            case 8:
                return recentMomentum(c, 5);

            case 9:
                return candleContinuation(last, prev);

            case 10:
                return candleReversal(last, prev);

            // ----------------------------------------------------
            // 11 - 20 : WICK / REJECTION PSYCHOLOGY
            // ----------------------------------------------------

            case 11:
                return bullishRejection(last);

            case 12:
                return bearishRejection(last);

            case 13:
                return lowerWickPressure(c);

            case 14:
                return upperWickPressure(c);

            case 15:
                return strongBullClose(last);

            case 16:
                return strongBearClose(last);

            case 17:
                return wickAgainstTrend(c);

            case 18:
                return wickContinuation(c);

            case 19:
                return rejectionAfterImpulse(c);

            case 20:
                return exhaustionWick(c);

            // ----------------------------------------------------
            // 21 - 30 : TREND
            // ----------------------------------------------------

            case 21:
                return slopeRule(c, 5);

            case 22:
                return slopeRule(c, 8);

            case 23:
                return slopeRule(c, 12);

            case 24:
                return emaDirection(c, 5);

            case 25:
                return emaDirection(c, 9);

            case 26:
                return emaDirection(c, 20);

            case 27:
                return priceVsSma(c, 5);

            case 28:
                return priceVsSma(c, 10);

            case 29:
                return priceVsSma(c, 20);

            case 30:
                return trendAlignment(c);

            // ----------------------------------------------------
            // 31 - 40 : MOMENTUM
            // ----------------------------------------------------

            case 31:
                return momentumRule(c, 3);

            case 32:
                return momentumRule(c, 5);

            case 33:
                return momentumRule(c, 8);

            case 34:
                return rocRule(c, 3);

            case 35:
                return rocRule(c, 5);

            case 36:
                return rocRule(c, 8);

            case 37:
                return accelerationRule(c);

            case 38:
                return momentumDivergence(c);

            case 39:
                return impulseStrength(c);

            case 40:
                return momentumExhaustion(c);

            // ----------------------------------------------------
            // 41 - 50 : RSI / OSCILLATOR
            // ----------------------------------------------------

            case 41:
                return rsiRule(c, 7);

            case 42:
                return rsiRule(c, 14);

            case 43:
                return rsiOversoldReversal(c);

            case 44:
                return rsiOverboughtReversal(c);

            case 45:
                return rsiTrendConfirmation(c);

            case 46:
                return stochasticRule(c);

            case 47:
                return stochasticReversal(c);

            case 48:
                return oscillatorAgreement(c);

            case 49:
                return oscillatorMomentum(c);

            case 50:
                return oscillatorExhaustion(c);

            // ----------------------------------------------------
            // 51 - 60 : MACD / MOVING AVERAGE
            // ----------------------------------------------------

            case 51:
                return macdDirection(c);

            case 52:
                return macdMomentum(c);

            case 53:
                return macdCross(c);

            case 54:
                return macdHistogram(c);

            case 55:
                return emaCross(c, 5, 9);

            case 56:
                return emaCross(c, 9, 20);

            case 57:
                return emaCross(c, 20, 50);

            case 58:
                return movingAverageStack(c);

            case 59:
                return movingAverageSlope(c);

            case 60:
                return movingAverageCompression(c);

            // ----------------------------------------------------
            // 61 - 70 : SUPPORT / RESISTANCE / STRUCTURE
            // ----------------------------------------------------

            case 61:
                return supportBounce(c);

            case 62:
                return resistanceReject(c);

            case 63:
                return higherHighs(c);

            case 64:
                return lowerLows(c);

            case 65:
                return higherLows(c);

            case 66:
                return lowerHighs(c);

            case 67:
                return structureBreakUp(c);

            case 68:
                return structureBreakDown(c);

            case 69:
                return rangePosition(c);

            case 70:
                return structureStrength(c);

            // ----------------------------------------------------
            // 71 - 80 : BOLLINGER / VOLATILITY / BREAKOUT
            // ----------------------------------------------------

            case 71:
                return bollingerPosition(c);

            case 72:
                return bollingerBreakout(c);

            case 73:
                return bollingerMeanReversion(c);

            case 74:
                return volatilityExpansion(c);

            case 75:
                return volatilityContraction(c);

            case 76:
                return breakoutUp(c);

            case 77:
                return breakoutDown(c);

            case 78:
                return falseBreakout(c);

            case 79:
                return breakoutRetest(c);

            case 80:
                return rangeBreakPressure(c);

            // ----------------------------------------------------
            // 81 - 90 : ATR / PRESSURE / EXHAUSTION
            // ----------------------------------------------------

            case 81:
                return trueRangeDirection(c);

            case 82:
                return atrExpansion(c);

            case 83:
                return atrContraction(c);

            case 84:
                return candleRangePressure(c);

            case 85:
                return bodyRangePressure(c);

            case 86:
                return volatilityTrend(c);

            case 87:
                return trueRange(c, lastCandle(c)) >
                        averageTrueRange(c, 14) ? 1 : -1;

            case 88:
                return trueRange(c, lastCandle(c)) <
                        averageTrueRange(c, 14) ? -1 : 1;

            case 89:
                return exhaustionAfterRun(c);

            case 90:
                return reversalAfterExtreme(c);

            // ----------------------------------------------------
            // 91 - 100 : ADVANCED MARKET PSYCHOLOGY
            // ----------------------------------------------------

            case 91:
                return pressureBalance(c);

            case 92:
                return buyerSellerDominance(c);

            case 93:
                return candleSequencePsychology(c);

            case 94:
                return impulseCorrectionBalance(c);

            case 95:
                return trendExhaustion(c);

            case 96:
                return trapDetection(c);

            case 97:
                return rejectionConfirmation(c);

            case 98:
                return multiFactorAgreement(c);

            case 99:
                return finalPsychologyBias(c);

            case 100:
                return finalPriceActionConfirmation(c);

            default:
                return 0;
        }
    }

    // ============================================================
    // BASIC
    // ============================================================

    private static int direction(Candle x) {

        if (x.bullish()) return 1;
        if (x.bearish()) return -1;
        return 0;
    }

    private static int bodyDominance(List<Candle> c) {

        Candle x = lastCandle(c);

        if (x.bodyRatio() > 0.65) {
            return direction(x);
        }

        return 0;
    }

    private static int closeNearHighLow(Candle x) {

        double p = x.closeLocation();

        if (p > 0.80) return 1;
        if (p < 0.20) return -1;

        return 0;
    }

    private static int wickPressure(Candle x) {

        if (x.lowerWick() > x.upperWick() * 1.5) {
            return 1;
        }

        if (x.upperWick() > x.lowerWick() * 1.5) {
            return -1;
        }

        return 0;
    }

    private static int consecutiveDirection(
            List<Candle> c,
            int count
    ) {

        if (c.size() < count) return 0;

        int bulls = 0;
        int bears = 0;

        for (int i = c.size() - count; i < c.size(); i++) {

            Candle x = c.get(i);

            if (x.bullish()) bulls++;
            else if (x.bearish()) bears++;
        }

        if (bulls == count) return 1;
        if (bears == count) return -1;

        return 0;
    }

    private static int recentMomentum(
            List<Candle> c,
            int n
    ) {

        if (c.size() < n + 1) return 0;

        double start = c.get(c.size() - n - 1).close;
        double end = lastCandle(c).close;

        return compare(end, start);
    }

    private static int candleContinuation(
            Candle last,
            Candle prev
    ) {

        if (last.bullish() && prev.bullish()) return 1;
        if (last.bearish() && prev.bearish()) return -1;

        return 0;
    }

    private static int candleReversal(
            Candle last,
            Candle prev
    ) {

        if (prev.bearish() && last.bullish()
                && last.close > prev.open) {
            return 1;
        }

        if (prev.bullish() && last.bearish()
                && last.close < prev.open) {
            return -1;
        }

        return 0;
    }

    // ============================================================
    // WICK PSYCHOLOGY
    // ============================================================

    private static int bullishRejection(Candle x) {

        if (x.lowerWick() > x.body() * 1.5
                && x.closeLocation() > 0.55) {
            return 1;
        }

        return 0;
    }

    private static int bearishRejection(Candle x) {

        if (x.upperWick() > x.body() * 1.5
                && x.closeLocation() < 0.45) {
            return -1;
        }

        return 0;
    }

    private static int lowerWickPressure(List<Candle> c) {

        int up = 0;
        int down = 0;

        int n = Math.min(5, c.size());

        for (int i = c.size() - n; i < c.size(); i++) {

            Candle x = c.get(i);

            if (x.lowerWick() > x.body()) up++;
            if (x.upperWick() > x.body()) down++;
        }

        return compare(up, down);
    }

    private static int upperWickPressure(List<Candle> c) {

        int up = 0;
        int down = 0;

        int n = Math.min(5, c.size());

        for (int i = c.size() - n; i < c.size(); i++) {

            Candle x = c.get(i);

            if (x.upperWick() > x.body()) down++;
            if (x.lowerWick() > x.body()) up++;
        }

        return compare(up, down);
    }

    private static int strongBullClose(Candle x) {

        if (x.bullish() && x.closeLocation() > 0.75) {
            return 1;
        }

        return 0;
    }

    private static int strongBearClose(Candle x) {

        if (x.bearish() && x.closeLocation() < 0.25) {
            return -1;
        }

        return 0;
    }

    private static int wickAgainstTrend(List<Candle> c) {

        int trend = slopeRule(c, 8);

        Candle x = lastCandle(c);

        if (trend > 0 && x.upperWick() > x.body() * 1.7) {
            return -1;
        }

        if (trend < 0 && x.lowerWick() > x.body() * 1.7) {
            return 1;
        }

        return 0;
    }

    private static int wickContinuation(List<Candle> c) {

        Candle x = lastCandle(c);

        if (x.lowerWick() > x.upperWick()
                && x.closeLocation() > 0.6) {
            return 1;
        }

        if (x.upperWick() > x.lowerWick()
                && x.closeLocation() < 0.4) {
            return -1;
        }

        return 0;
    }

    private static int rejectionAfterImpulse(List<Candle> c) {

        if (c.size() < 5) return 0;

        double impulse = 0;

        for (int i = c.size() - 5; i < c.size() - 1; i++) {
            impulse += c.get(i).close - c.get(i).open;
        }

        Candle last = lastCandle(c);

        if (impulse > 0 && last.upperWick() > last.body()) {
            return -1;
        }

        if (impulse < 0 && last.lowerWick() > last.body()) {
            return 1;
        }

        return 0;
    }

    private static int exhaustionWick(List<Candle> c) {

        int trend = slopeRule(c, 10);

        Candle x = lastCandle(c);

        if (trend > 0 && x.upperWick() > x.body() * 2) {
            return -1;
        }

        if (trend < 0 && x.lowerWick() > x.body() * 2) {
            return 1;
        }

        return 0;
    }

    // ============================================================
    // TREND
    // ============================================================

    private static int slopeRule(
            List<Candle> c,
            int n
    ) {

        if (c.size() < n) return 0;

        double first = c.get(c.size() - n).close;
        double last = lastCandle(c).close;

        return compare(last, first);
    }

    private static int emaDirection(
            List<Candle> c,
            int period
    ) {

        double ema = ema(c, period);

        return compare(lastCandle(c).close, ema);
    }

    private static int priceVsSma(
            List<Candle> c,
            int period
    ) {

        double sma = sma(c, period);

        return compare(lastCandle(c).close, sma);
    }

    private static int trendAlignment(List<Candle> c) {

        double ema5 = ema(c, 5);
        double ema9 = ema(c, 9);
        double ema20 = ema(c, 20);

        if (ema5 > ema9 && ema9 > ema20) return 1;
        if (ema5 < ema9 && ema9 < ema20) return -1;

        return 0;
    }

    // ============================================================
    // MOMENTUM
    // ============================================================

    private static int momentumRule(
            List<Candle> c,
            int n
    ) {

        if (c.size() < n + 1) return 0;

        double total = 0;

        for (int i = c.size() - n; i < c.size(); i++) {
            total += c.get(i).close - c.get(i).open;
        }

        return compare(total, 0);
    }

    private static int rocRule(
            List<Candle> c,
            int n
    ) {

        if (c.size() <= n) return 0;

        double oldPrice = c.get(c.size() - n - 1).close;
        double current = lastCandle(c).close;

        if (Math.abs(oldPrice) < 0.000001) return 0;

        double roc =
                ((current - oldPrice) / Math.abs(oldPrice)) * 100.0;

        if (roc > 0.05) return 1;
        if (roc < -0.05) return -1;

        return 0;
    }

    private static int accelerationRule(List<Candle> c) {

        if (c.size() < 5) return 0;

        double recent =
                c.get(c.size() - 1).close -
                        c.get(c.size() - 2).close;

        double previous =
                c.get(c.size() - 2).close -
                        c.get(c.size() - 3).close;

        return compare(recent, previous);
    }

    private static int momentumDivergence(List<Candle> c) {

        if (c.size() < 8) return 0;

        double priceOld = c.get(c.size() - 8).close;
        double priceNew = lastCandle(c).close;

        double momOld =
                c.get(c.size() - 5).close -
                        c.get(c.size() - 8).close;

        double momNew =
                lastCandle(c).close -
                        c.get(c.size() - 4).close;

        if (priceNew > priceOld && momNew < momOld) {
            return -1;
        }

        if (priceNew < priceOld && momNew > momOld) {
            return 1;
        }

        return 0;
    }

    private static int impulseStrength(List<Candle> c) {

        Candle x = lastCandle(c);

        if (x.bodyRatio() > 0.75) {
            return direction(x);
        }

        return 0;
    }

    private static int momentumExhaustion(List<Candle> c) {

        if (c.size() < 6) return 0;

        int trend = slopeRule(c, 6);
        Candle x = lastCandle(c);

        if (trend > 0 && x.bodyRatio() < 0.25) {
            return -1;
        }

        if (trend < 0 && x.bodyRatio() < 0.25) {
            return 1;
        }

        return 0;
    }

    // ============================================================
    // RSI
    // ============================================================

    private static int rsiRule(
            List<Candle> c,
            int period
    ) {

        double rsi = rsi(c, period);

        if (rsi > 55) return 1;
        if (rsi < 45) return -1;

        return 0;
    }

    private static int rsiOversoldReversal(List<Candle> c) {

        double rsi = rsi(c, 14);

        if (rsi < 30 && lastCandle(c).bullish()) {
            return 1;
        }

        return 0;
    }

    private static int rsiOverboughtReversal(List<Candle> c) {

        double rsi = rsi(c, 14);

        if (rsi > 70 && lastCandle(c).bearish()) {
            return -1;
        }

        return 0;
    }

    private static int rsiTrendConfirmation(List<Candle> c) {

        double rsi = rsi(c, 14);

        if (rsi > 50 && slopeRule(c, 8) > 0) {
            return 1;
        }

        if (rsi < 50 && slopeRule(c, 8) < 0) {
            return -1;
        }

        return 0;
    }

    // ============================================================
    // STOCHASTIC
    // ============================================================

    private static int stochasticRule(List<Candle> c) {

        double k = stochastic(c, 14);

        if (k > 55) return 1;
        if (k < 45) return -1;

        return 0;
    }

    private static int stochasticReversal(List<Candle> c) {

        double k = stochastic(c, 14);
        Candle x = lastCandle(c);

        if (k < 20 && x.bullish()) return 1;
        if (k > 80 && x.bearish()) return -1;

        return 0;
    }

    private static int oscillatorAgreement(List<Candle> c) {

        double r = rsi(c, 14);
        double s = stochastic(c, 14);

        if (r > 55 && s > 55) return 1;
        if (r < 45 && s < 45) return -1;

        return 0;
    }

    private static int oscillatorMomentum(List<Candle> c) {

        double r = rsi(c, 7);
        double s = stochastic(c, 9);

        if (r > 60 && s > 60) return 1;
        if (r < 40 && s < 40) return -1;

        return 0;
    }

    private static int oscillatorExhaustion(List<Candle> c) {

        double r = rsi(c, 14);

        if (r > 75) return -1;
        if (r < 25) return 1;

        return 0;
    }

    // ============================================================
    // MACD / MOVING AVERAGE
    // ============================================================

    private static int macdDirection(List<Candle> c) {

        double macd =
                ema(c, 12) - ema(c, 26);

        return compare(macd, 0);
    }

    private static int macdMomentum(List<Candle> c) {

        if (c.size() < 5) return 0;

        double now =
                ema(c, 12) - ema(c, 26);

        List<Candle> oldList =
                new ArrayList<>(c.subList(0, c.size() - 2));

        double old =
                ema(oldList, 12) - ema(oldList, 26);

        return compare(now, old);
    }

    private static int macdCross(List<Candle> c) {

        if (c.size() < 3) return 0;

        List<Candle> before =
                new ArrayList<>(c.subList(0, c.size() - 1));

        double oldMacd =
                ema(before, 12) - ema(before, 26);

        double newMacd =
                ema(c, 12) - ema(c, 26);

        if (oldMacd <= 0 && newMacd > 0) return 1;
        if (oldMacd >= 0 && newMacd < 0) return -1;

        return 0;
    }

    private static int macdHistogram(List<Candle> c) {

        double macd =
                ema(c, 12) - ema(c, 26);

        double signal =
                emaMacdSignal(c);

        return compare(macd, signal);
    }

    private static int emaCross(
            List<Candle> c,
            int fast,
            int slow
    ) {

        double f = ema(c, fast);
        double s = ema(c, slow);

        return compare(f, s);
    }

    private static int movingAverageStack(List<Candle> c) {

        double e5 = ema(c, 5);
        double e9 = ema(c, 9);
        double e20 = ema(c, 20);

        if (e5 > e9 && e9 > e20) return 1;
        if (e5 < e9 && e9 < e20) return -1;

        return 0;
    }

    private static int movingAverageSlope(List<Candle> c) {

        if (c.size() < 5) return 0;

        double now = sma(c, 10);

        List<Candle> old =
                new ArrayList<>(c.subList(0, c.size() - 3));

        double previous = sma(old, 10);

        return compare(now, previous);
    }

    private static int movingAverageCompression(List<Candle> c) {

        double e5 = ema(c, 5);
        double e20 = ema(c, 20);

        double distance = Math.abs(e5 - e20);

        double avgRange = averageRange(c, 10);

        if (distance < avgRange * 0.15) {
            return direction(lastCandle(c));
        }

        return 0;
    }

    // ============================================================
    // SUPPORT / RESISTANCE / STRUCTURE
    // ============================================================

    private static int supportBounce(List<Candle> c) {

        double support = lowestLow(c, 10);
        Candle x = lastCandle(c);

        if (x.low <= support * 1.001
                && x.close > x.open) {
            return 1;
        }

        return 0;
    }

    private static int resistanceReject(List<Candle> c) {

        double resistance = highestHigh(c, 10);
        Candle x = lastCandle(c);

        if (x.high >= resistance * 0.999
                && x.close < x.open) {
            return -1;
        }

        return 0;
    }

    private static int higherHighs(List<Candle> c) {

        if (c.size() < 6) return 0;

        double oldHigh =
                highestHigh(c.subList(0, c.size() - 3), 3);

        double newHigh =
                highestHigh(c, 3);

        return compare(newHigh, oldHigh);
    }

    private static int lowerLows(List<Candle> c) {

        if (c.size() < 6) return 0;

        double oldLow =
                lowestLow(c.subList(0, c.size() - 3), 3);

        double newLow =
                lowestLow(c, 3);

        return compare(oldLow, newLow);
    }

    private static int higherLows(List<Candle> c) {

        if (c.size() < 6) return 0;

        double oldLow =
                lowestLow(c.subList(0, c.size() - 3), 3);

        double newLow =
                lowestLow(c, 3);

        return compare(newLow, oldLow);
    }

    private static int lowerHighs(List<Candle> c) {

        if (c.size() < 6) return 0;

        double oldHigh =
                highestHigh(c.subList(0, c.size() - 3), 3);

        double newHigh =
                highestHigh(c, 3);

        return compare(oldHigh, newHigh);
    }

    private static int structureBreakUp(List<Candle> c) {

        double resistance =
                highestHigh(c.subList(0, c.size() - 1), 8);

        return lastCandle(c).close > resistance ? 1 : 0;
    }

    private static int structureBreakDown(List<Candle> c) {

        double support =
                lowestLow(c.subList(0, c.size() - 1), 8);

        return lastCandle(c).close < support ? -1 : 0;
    }

    private static int rangePosition(List<Candle> c) {

        double high = highestHigh(c, 12);
        double low = lowestLow(c, 12);

        double range = Math.max(0.000001, high - low);

        double p =
                (lastCandle(c).close - low) / range;

        if (p > 0.70) return 1;
        if (p < 0.30) return -1;

        return 0;
    }

    private static int structureStrength(List<Candle> c) {

        int hh = higherHighs(c);
        int hl = higherLows(c);

        int lh = lowerHighs(c);
        int ll = lowerLows(c);

        if (hh > 0 && hl > 0) return 1;
        if (lh < 0 && ll < 0) return -1;

        return 0;
    }

    // ============================================================
    // BOLLINGER / VOLATILITY
    // ============================================================

    private static int bollingerPosition(List<Candle> c) {

        double mid = sma(c, 20);
        double sd = standardDeviation(c, 20);

        double upper = mid + sd * 2;
        double lower = mid - sd * 2;

        double price = lastCandle(c).close;

        if (price > upper) return 1;
        if (price < lower) return -1;

        return compare(price, mid);
    }

    private static int bollingerBreakout(List<Candle> c) {

        double mid = sma(c, 20);
        double sd = standardDeviation(c, 20);

        double upper = mid + sd * 2;
        double lower = mid - sd * 2;

        Candle x = lastCandle(c);

        if (x.close > upper) return 1;
        if (x.close < lower) return -1;

        return 0;
    }

    private static int bollingerMeanReversion(List<Candle> c) {

        double mid = sma(c, 20);
        double sd = standardDeviation(c, 20);

        double upper = mid + sd * 2;
        double lower = mid - sd * 2;

        Candle x = lastCandle(c);

        if (x.close > upper && x.bearish()) return -1;
        if (x.close < lower && x.bullish()) return 1;

        return 0;
    }

    private static int volatilityExpansion(List<Candle> c) {

        double now = averageRange(c, 3);
        double old = averageRange(c, 10);

        return compare(now, old);
    }

    private static int volatilityContraction(List<Candle> c) {

        double now = averageRange(c, 3);
        double old = averageRange(c, 10);

        if (now < old * 0.75) {
            return direction(lastCandle(c));
        }

        return 0;
    }

    private static int breakoutUp(List<Candle> c) {

        double resistance =
                highestHigh(c.subList(0, c.size() - 1), 10);

        if (lastCandle(c).close > resistance) {
            return 1;
        }

        return 0;
    }

    private static int breakoutDown(List<Candle> c) {

        double support =
                lowestLow(c.subList(0, c.size() - 1), 10);

        if (lastCandle(c).close < support) {
            return -1;
        }

        return 0;
    }

    private static int falseBreakout(List<Candle> c) {

        if (c.size() < 3) return 0;

        Candle x = lastCandle(c);
        double high =
                highestHigh(c.subList(0, c.size() - 1), 8);

        double low =
                lowestLow(c.subList(0, c.size() - 1), 8);

        if (x.high > high && x.close < high) {
            return -1;
        }

        if (x.low < low && x.close > low) {
            return 1;
        }

        return 0;
    }

    private static int breakoutRetest(List<Candle> c) {

        if (c.size() < 4) return 0;

        Candle x = lastCandle(c);

        double previousHigh =
                highestHigh(c.subList(0, c.size() - 1), 6);

        double previousLow =
                lowestLow(c.subList(0, c.size() - 1), 6);

        if (x.low <= previousHigh
                && x.close > previousHigh) {
            return 1;
        }

        if (x.high >= previousLow
                && x.close < previousLow) {
            return -1;
        }

        return 0;
    }

    private static int rangeBreakPressure(List<Candle> c) {

        double range = averageRange(c, 8);
        Candle x = lastCandle(c);

        if (x.range() > range * 1.4) {
            return direction(x);
        }

        return 0;
    }

    // ============================================================
    // ATR / VOLATILITY
    // ============================================================

    private static int trueRangeDirection(List<Candle> c) {

        double tr = trueRange(c, lastCandle(c));
        double atr = averageTrueRange(c, 14);

        return tr > atr ? direction(lastCandle(c)) : 0;
    }

    private static int atrExpansion(List<Candle> c) {

        double current =
                averageTrueRange(c, 5);

        double old =
                averageTrueRange(c, 14);

        return compare(current, old);
    }

    private static int atrContraction(List<Candle> c) {

        double current =
                averageTrueRange(c, 5);

        double old =
                averageTrueRange(c, 14);

        if (current < old * 0.8) {
            return direction(lastCandle(c));
        }

        return 0;
    }

    private static int candleRangePressure(List<Candle> c) {

        double current = lastCandle(c).range();
        double avg = averageRange(c, 10);

        if (current > avg * 1.5) {
            return direction(lastCandle(c));
        }

        return 0;
    }

    private static int bodyRangePressure(List<Candle> c) {

        Candle x = lastCandle(c);

        if (x.bodyRatio() > 0.7) {
            return direction(x);
        }

        return 0;
    }

    private static int volatilityTrend(List<Candle> c) {

        double shortVol = averageRange(c, 5);
        double longVol = averageRange(c, 15);

        if (shortVol > longVol) {
            return direction(lastCandle(c));
        }

        return 0;
    }

    private static int exhaustionAfterRun(List<Candle> c) {

        int trend = consecutiveDirection(c, 5);
        Candle x = lastCandle(c);

        if (trend > 0 && x.bodyRatio() < 0.25) return -1;
        if (trend < 0 && x.bodyRatio() < 0.25) return 1;

        return 0;
    }

    private static int reversalAfterExtreme(List<Candle> c) {

        double rsi = rsi(c, 14);
        Candle x = lastCandle(c);

        if (rsi > 75 && x.bearish()) return -1;
        if (rsi < 25 && x.bullish()) return 1;

        return 0;
    }

    // ============================================================
    // ADVANCED PSYCHOLOGY
    // ============================================================

    private static int pressureBalance(List<Candle> c) {

        double bull = 0;
        double bear = 0;

        int n = Math.min(8, c.size());

        for (int i = c.size() - n; i < c.size(); i++) {

            Candle x = c.get(i);

            if (x.bullish()) {
                bull += x.body() + x.lowerWick() * 0.5;
            }

            if (x.bearish()) {
                bear += x.body() + x.upperWick() * 0.5;
            }
        }

        return compare(bull, bear);
    }

    private static int buyerSellerDominance(List<Candle> c) {

        double buyers = 0;
        double sellers = 0;

        int n = Math.min(10, c.size());

        for (int i = c.size() - n; i < c.size(); i++) {

            Candle x = c.get(i);

            buyers += x.closeLocation() * x.range();
            sellers += (1.0 - x.closeLocation()) * x.range();
        }

        return compare(buyers, sellers);
    }

    private static int candleSequencePsychology(List<Candle> c) {

        if (c.size() < 6) return 0;

        int bull = 0;
        int bear = 0;

        for (int i = c.size() - 6; i < c.size(); i++) {

            Candle x = c.get(i);

            if (x.bullish()) bull++;
            if (x.bearish()) bear++;
        }

        if (bull >= 4) return 1;
        if (bear >= 4) return -1;

        return 0;
    }

    private static int impulseCorrectionBalance(List<Candle> c) {

        if (c.size() < 8) return 0;

        double impulse = 0;
        double correction = 0;

        for (int i = c.size() - 8; i < c.size() - 3; i++) {
            impulse += c.get(i).close - c.get(i).open;
        }

        for (int i = c.size() - 3; i < c.size(); i++) {
            correction += c.get(i).close - c.get(i).open;
        }

        if (impulse > 0 && correction > -impulse * 0.6) {
            return 1;
        }

        if (impulse < 0 && correction < -impulse * 0.6) {
            return -1;
        }

        return 0;
    }

    private static int trendExhaustion(List<Candle> c) {

        int trend = slopeRule(c, 10);
        double rsi = rsi(c, 14);

        if (trend > 0 && rsi > 72) return -1;
        if (trend < 0 && rsi < 28) return 1;

        return 0;
    }

    private static int trapDetection(List<Candle> c) {

        Candle x = lastCandle(c);

        double resistance =
                highestHigh(c.subList(0, c.size() - 1), 8);

        double support =
                lowestLow(c.subList(0, c.size() - 1), 8);

        if (x.high > resistance
                && x.close < resistance
                && x.upperWick() > x.body()) {
            return -1;
        }

        if (x.low < support
                && x.close > support
                && x.lowerWick() > x.body()) {
            return 1;
        }

        return 0;
    }

    private static int rejectionConfirmation(List<Candle> c) {

        Candle x = lastCandle(c);

        if (bullishRejection(x)
                > 0
                && slopeRule(c, 5) <= 0) {
            return 1;
        }

        if (bearishRejection(x)
                < 0
                && slopeRule(c, 5) >= 0) {
            return -1;
        }

        return 0;
    }

    private static int multiFactorAgreement(List<Candle> c) {

        int a = slopeRule(c, 8);
        int b = rsiTrendConfirmation(c);
        int d = macdDirection(c);
        int e = direction(lastCandle(c));

        int up = 0;
        int down = 0;

        if (a > 0) up++;
        if (b > 0) up++;
        if (d > 0) up++;
        if (e > 0) up++;

        if (a < 0) down++;
        if (b < 0) down++;
        if (d < 0) down++;
        if (e < 0) down++;

        if (up >= 3) return 1;
        if (down >= 3) return -1;

        return 0;
    }

    private static int finalPsychologyBias(List<Candle> c) {

        int pressure = pressureBalance(c);
        int structure = structureStrength(c);
        int momentum = momentumRule(c, 5);
        int wick = wickPressure(lastCandle(c));

        int up = 0;
        int down = 0;

        if (pressure > 0) up++;
        if (structure > 0) up++;
        if (momentum > 0) up++;
        if (wick > 0) up++;

        if (pressure < 0) down++;
        if (structure < 0) down++;
        if (momentum < 0) down++;
        if (wick < 0) down++;

        if (up >= 3) return 1;
        if (down >= 3) return -1;

        return 0;
    }

    private static int finalPriceActionConfirmation(
            List<Candle> c
    ) {

        Candle x = lastCandle(c);

        int trend = slopeRule(c, 8);
        int close = closeNearHighLow(x);
        int body = bodyDominance(c);
        int rejection = wickPressure(x);

        int up = 0;
        int down = 0;

        if (trend > 0) up++;
        if (close > 0) up++;
        if (body > 0) up++;
        if (rejection > 0) up++;

        if (trend < 0) down++;
        if (close < 0) down++;
        if (body < 0) down++;
        if (rejection < 0) down++;

        if (up >= 3) return 1;
        if (down >= 3) return -1;

        return 0;
    }

    // ============================================================
    // CANDLE EXTRACTION FROM SCREENSHOT
    // ============================================================

    private static List<Candle> extractCandles(Bitmap bitmap) {

        List<Candle> candles = new ArrayList<>();

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width < 50 || height < 50) {
            return candles;
        }

        /*
         * Scan central chart area.
         * Avoid top/bottom UI regions.
         */
        int top = (int) (height * 0.12);
        int bottom = (int) (height * 0.88);

        int step = Math.max(2, width / 180);

        List<Double> centers = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Boolean> bullish = new ArrayList<>();

        for (int x = 2; x < width - 2; x += step) {

            int red = 0;
            int green = 0;

            double high = Double.MAX_VALUE;
            double low = Double.MIN_VALUE;

            double sum = 0;
            int count = 0;

            for (int y = top; y < bottom; y += 2) {

                int pixel = bitmap.getPixel(x, y);

                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                /*
                 * Broad green candle detection.
                 */
                if (g > r * 1.15 && g > b * 1.05 && g > 70) {
                    green++;
                    high = Math.min(high, y);
                    low = Math.max(low, y);
                    sum += y;
                    count++;
                }

                /*
                 * Broad red candle detection.
                 */
                if (r > g * 1.15 && r > b * 1.05 && r > 70) {
                    red++;
                    high = Math.min(high, y);
                    low = Math.max(low, y);
                    sum += y;
                    count++;
                }
            }

            if (count >= 2) {

                boolean isBull = green >= red;

                double center =
                        sum / Math.max(1, count);

                centers.add(center);
                highs.add(high == Double.MAX_VALUE
                        ? center : high);
                lows.add(low == Double.MIN_VALUE
                        ? center : low);
                bullish.add(isBull);
            }
        }

        /*
         * Convert runs of detected chart columns into candles.
         */
        if (centers.size() < 10) {
            return fallbackCandles(bitmap);
        }

        int groupSize = Math.max(1, centers.size() / 50);

        for (int i = 0; i < centers.size(); i += groupSize) {

            int end =
                    Math.min(centers.size(), i + groupSize);

            if (end <= i) continue;

            double high = 0;
            double low = Double.MAX_VALUE;
            double open = centers.get(i);
            double close = centers.get(end - 1);

            int bullCount = 0;

            for (int j = i; j < end; j++) {

                high = Math.max(
                        high,
                        centers.get(j)
                );

                low = Math.min(
                        low,
                        centers.get(j)
                );

                if (bullish.get(j)) {
                    bullCount++;
                }
            }

            /*
             * Screen Y axis is inverted.
             * Smaller Y = higher price.
             *
             * Convert to mathematical price direction
             * by negating screen coordinates.
             */
            double o = -open;
            double cl = -close;
            double h = -high;
            double l = -low;

            if (bullCount >= (end - i) / 2.0) {

                if (cl <= o) {
                    cl = o + Math.max(0.5, Math.abs(o) * 0.002);
                }

            } else {

                if (cl >= o) {
                    cl = o - Math.max(0.5, Math.abs(o) * 0.002);
                }
            }

            h = Math.max(h, Math.max(o, cl));
            l = Math.min(l, Math.min(o, cl));

            candles.add(
                    new Candle(o, h, l, cl)
            );
        }

        return candles;
    }

    /**
     * Fallback image-based candle approximation.
     * Still deterministic and screenshot-only.
     */
    private static List<Candle> fallbackCandles(Bitmap bitmap) {

        List<Candle> result = new ArrayList<>();

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int count = Math.min(40, Math.max(10, width / 12));

        double previous =
                -(height * 0.50);

        for (int i = 0; i < count; i++) {

            int x =
                    (int) (((double) i / count) * width);

            int top = (int) (height * 0.15);
            int bottom = (int) (height * 0.85);

            int green = 0;
            int red = 0;

            int minY = bottom;
            int maxY = top;

            for (int y = top; y < bottom; y += 3) {

                int pixel = bitmap.getPixel(
                        Math.min(width - 1, Math.max(0, x)),
                        y
                );

                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                if (g > r * 1.15 && g > 70) {
                    green++;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }

                if (r > g * 1.15 && r > 70) {
                    red++;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }

            if (green + red < 2) {
                continue;
            }

            double close =
                    -(minY + maxY) / 2.0;

            double open = previous;

            if (green >= red) {
                close = Math.max(
                        close,
                        open + 0.5
                );
            } else {
                close = Math.min(
                        close,
                        open - 0.5
                );
            }

            double high =
                    Math.max(
                            Math.max(open, close),
                            -minY
                    );

            double low =
                    Math.min(
                            Math.min(open, close),
                            -maxY
                    );

            result.add(
                    new Candle(
                            open,
                            high,
                            low,
                            close
                    )
            );

            previous = close;
        }

        return result;
    }

    // ============================================================
    // CHART QUALITY
    // ============================================================

    private static double calculateChartQuality(
            Bitmap bitmap,
            List<Candle> candles
    ) {

        if (candles.size() < 10) {
            return 0;
        }

        double countScore =
                Math.min(100.0,
                        candles.size() * 2.0);

        double movement = 0;

        for (int i = 1; i < candles.size(); i++) {

            movement +=
                    Math.abs(
                            candles.get(i).close -
                                    candles.get(i - 1).close
                    );
        }

        double movementScore =
                movement > 0 ? 50 : 0;

        double quality =
                countScore * 0.6 +
                        movementScore * 0.4;

        return Math.max(
                0,
                Math.min(100, quality)
        );
    }

    // ============================================================
    // SMA
    // ============================================================

    private static double sma(
            List<Candle> c,
            int period
    ) {

        if (c.isEmpty()) return 0;

        int n = Math.min(period, c.size());

        double sum = 0;

        for (int i = c.size() - n; i < c.size(); i++) {
            sum += c.get(i).close;
        }

        return sum / n;
    }

    // ============================================================
    // EMA
    // ============================================================

    private static double ema(
            List<Candle> c,
            int period
    ) {

        if (c.isEmpty()) return 0;

        int p = Math.min(period, c.size());

        double value =
                c.get(0).close;

        double multiplier =
                2.0 / (p + 1.0);

        for (int i = 1; i < c.size(); i++) {

            value =
                    (c.get(i).close - value)
                            * multiplier
                            + value;
        }

        return value;
    }

    // ============================================================
    // RSI
    // ============================================================

    private static double rsi(
            List<Candle> c,
            int period
    ) {

        if (c.size() < 2) return 50;

        int n =
                Math.min(
                        period,
                        c.size() - 1
                );

        double gains = 0;
        double losses = 0;

        for (int i = c.size() - n; i < c.size(); i++) {

            double change =
                    c.get(i).close -
                            c.get(i - 1).close;

            if (change > 0) {
                gains += change;
            } else {
                losses -= change;
            }
        }

        if (losses == 0) return 100;

        double rs =
                gains / losses;

        return 100.0 -
                (100.0 / (1.0 + rs));
    }

    // ============================================================
    // STOCHASTIC
    // ============================================================

    private static double stochastic(
            List<Candle> c,
            int period
    ) {

        int n =
                Math.min(period, c.size());

        double high =
                highestHigh(c, n);

        double low =
                lowestLow(c, n);

        if (high - low < 0.000001) {
            return 50;
        }

        return
                ((lastCandle(c).close - low)
                        / (high - low))
                        * 100.0;
    }

    // ============================================================
    // MACD SIGNAL
    // ============================================================

    private static double emaMacdSignal(
            List<Candle> c
    ) {

        List<Candle> synthetic =
                new ArrayList<>();

        int start =
                Math.max(0, c.size() - 20);

        for (int i = start; i < c.size(); i++) {

            double macd =
                    ema(
                            c.subList(
                                    0,
                                    i + 1
                            ),
                            12
                    )
                    -
                    ema(
                            c.subList(
                                    0,
                                    i + 1
                            ),
                            26
                    );

            synthetic.add(
                    new Candle(
                            macd,
                            macd,
                            macd,
                            macd
                    )
            );
        }

        return ema(
                synthetic,
                9
        );
    }

    // ============================================================
    // ATR
    // ============================================================

    private static double trueRange(
            List<Candle> c,
            Candle current
    ) {

        if (c.size() < 2) {
            return current.range();
        }

        int index =
                c.indexOf(current);

        if (index <= 0) {
            return current.range();
        }

        Candle previous =
                c.get(index - 1);

        double a =
                current.high -
                        current.low;

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

    private static double averageTrueRange(
            List<Candle> c,
            int period
    ) {

        if (c.size() < 2) {
            return lastCandle(c).range();
        }

        int start =
                Math.max(
                        1,
                        c.size() - period
                );

        double sum = 0;
        int count = 0;

        for (int i = start; i < c.size(); i++) {

            Candle current = c.get(i);
            Candle previous = c.get(i - 1);

            double tr1 =
                    current.high -
                            current.low;

            double tr2 =
                    Math.abs(
                            current.high -
                                    previous.close
                    );

            double tr3 =
                    Math.abs(
                            current.low -
                                    previous.close
                    );

            double tr =
                    Math.max(
                            tr1,
                            Math.max(tr2, tr3)
                    );

            sum += tr;
            count++;
        }

        if (count == 0) {
            return lastCandle(c).range();
        }

        return sum / count;
    }

    // ============================================================
    // RANGE
    // ============================================================

    private static double averageRange(
            List<Candle> c,
            int period
    ) {

        int n =
                Math.min(period, c.size());

        double sum = 0;

        for (int i = c.size() - n; i < c.size(); i++) {
            sum += c.get(i).range();
        }

        return sum / Math.max(1, n);
    }

    // ============================================================
    // STANDARD DEVIATION
    // ============================================================

    private static double standardDeviation(
            List<Candle> c,
            int period
    ) {

        int n =
                Math.min(period, c.size());

        double mean =
                sma(c, n);

        double sum = 0;

        for (int i = c.size() - n; i < c.size(); i++) {

            double d =
                    c.get(i).close -
                            mean;

            sum += d * d;
        }

        return Math.sqrt(
                sum / Math.max(1, n)
        );
    }

    // ============================================================
    // HIGH / LOW
    // ============================================================

    private static double highestHigh(
            List<Candle> c,
            int period
    ) {

        int n =
                Math.min(period, c.size());

        double high =
                -Double.MAX_VALUE;

        for (int i = c.size() - n; i < c.size(); i++) {
            high =
                    Math.max(
                            high,
                            c.get(i).high
                    );
        }

        return high;
    }

    private static double lowestLow(
            List<Candle> c,
            int period
    ) {

        int n =
                Math.min(period, c.size());

        double low =
                Double.MAX_VALUE;

        for (int i = c.size() - n; i < c.size(); i++) {
            low =
                    Math.min(
                            low,
                            c.get(i).low
                    );
        }

        return low;
    }

    // ============================================================
    // LAST CANDLE
    // ============================================================

    private static Candle lastCandle(
            List<Candle> c
    ) {

        return c.get(c.size() - 1);
    }

    // ============================================================
    // COMPARE
    // ============================================================

    private static int compare(
            double a,
            double b
    ) {

        double difference = a - b;

        double scale =
                Math.max(
                        0.000001,
                        Math.abs(a) +
                                Math.abs(b)
                );

        double normalized =
                difference / scale;

        if (normalized > 0.003) return 1;
        if (normalized < -0.003) return -1;

        return 0;
    }
}
