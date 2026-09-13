package com.mdjibon.scanner;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public final class Analyzer {

    private Analyzer() {
    }

    public static final int TOTAL_RULES = 200;

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
        double open, high, low, close;

        Candle(double open, double high, double low, double close) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }

        double body() { return Math.abs(close - open); }
        double range() { return Math.max(0.000001, high - low); }
        double upperWick() { return Math.max(0.0, high - Math.max(open, close)); }
        double lowerWick() { return Math.max(0.0, Math.min(open, close) - low); }
        boolean bullish() { return close > open; }
        boolean bearish() { return close < open; }
        boolean doji() { return body() <= range() * 0.12; }
        double bodyRatio() { return body() / range(); }
        double closeLocation() { return (close - low) / range(); }
    }

    private static final class Score {
        int up, down, neutral;
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

        if (candles.size() < 40) {
            result.signal = "NO TRADE";
            result.quality = 0;
            result.evaluatedRules = 0;
            return result;
        }

        result.quality = calculateChartQuality(bitmap, candles);

        if (result.quality < 25.0) {
            result.signal = "NO TRADE";
            result.evaluatedRules = 0;
            return result;
        }

        Score score = new Score();

        for (int rule = 1; rule <= TOTAL_RULES; rule++) {

            int vote = evaluateRule(rule, candles);

            if (vote > 0) {
                score.up++;
                result.bullish++;
            } else if (vote < 0) {
                score.down++;
                result.bearish++;
            } else {
                score.neutral++;
                result.neutral++;
            }

            result.evaluatedRules++;
        }

        if (result.evaluatedRules != TOTAL_RULES) {
            result.signal = "NO TRADE";
            result.confidence = 0;
            return result;
        }

        int majority = Math.max(score.up, score.down);
        result.confidence = (majority * 100.0) / TOTAL_RULES;

        int minAgreement = (int) (TOTAL_RULES * 0.65);

        if (score.up >= minAgreement && score.up > score.down) {
            result.signal = "UP";
        } else if (score.down >= minAgreement && score.down > score.up) {
            result.signal = "DOWN";
        } else {
            result.signal = "NO TRADE";
        }

        result.timeframe = "SCREEN";
        result.candleSize = candles.size() + " candles";

        return result;
    }

    // ============================================================
    // RULE DISPATCHER
    // ============================================================

    private static int evaluateRule(int rule, List<Candle> c) {

        Candle last = lastCandle(c);
        Candle prev = c.get(c.size() - 2);

        switch (rule) {

            case 1: return direction(last);
            case 2: return bodyDominance(c);
            case 3: return closeNearHighLow(last);
            case 4: return wickPressure(last);
            case 5: return consecutiveDirection(c, 3);
            case 6: return consecutiveDirection(c, 5);
            case 7: return recentMomentum(c, 3);
            case 8: return recentMomentum(c, 5);
            case 9: return candleContinuation(last, prev);
            case 10: return candleReversal(last, prev);

            case 11: return bullishRejection(last);
            case 12: return bearishRejection(last);
            case 13: return lowerWickPressure(c);
            case 14: return upperWickPressure(c);
            case 15: return strongBullClose(last);
            case 16: return strongBearClose(last);
            case 17: return wickAgainstTrend(c);
            case 18: return wickContinuation(c);
            case 19: return rejectionAfterImpulse(c);
            case 20: return exhaustionWick(c);

            case 21: return slopeRule(c, 5);
            case 22: return slopeRule(c, 8);
            case 23: return slopeRule(c, 12);
            case 24: return emaDirection(c, 5);
            case 25: return emaDirection(c, 9);
            case 26: return emaDirection(c, 20);
            case 27: return priceVsSma(c, 5);
            case 28: return priceVsSma(c, 10);
            case 29: return priceVsSma(c, 20);
            case 30: return trendAlignment(c);

            case 31: return momentumRule(c, 3);
            case 32: return momentumRule(c, 5);
            case 33: return momentumRule(c, 8);
            case 34: return rocRule(c, 3);
            case 35: return rocRule(c, 5);
            case 36: return rocRule(c, 8);
            case 37: return accelerationRule(c);
            case 38: return momentumDivergence(c);
            case 39: return impulseStrength(c);
            case 40: return momentumExhaustion(c);

            case 41: return engulfingBull(c);
            case 42: return engulfingBear(c);
            case 43: return dojiPattern(c);
            case 44: return hammerPattern(c);
            case 45: return shootingStarPattern(c);
            case 46: return morningStar(c);
            case 47: return eveningStar(c);
            case 48: return threeWhiteSoldiers(c);
            case 49: return threeBlackCrows(c);
            case 50: return haramiPattern(c);

            case 51: return rsiRule(c, 7);
            case 52: return rsiRule(c, 14);
            case 53: return rsiOversoldReversal(c);
            case 54: return rsiOverboughtReversal(c);
            case 55: return rsiTrendConfirmation(c);
            case 56: return stochasticRule(c);
            case 57: return stochasticReversal(c);
            case 58: return oscillatorAgreement(c);
            case 59: return oscillatorMomentum(c);
            case 60: return oscillatorExhaustion(c);

            case 61: return macdDirection(c);
            case 62: return macdMomentum(c);
            case 63: return macdCross(c);
            case 64: return macdHistogram(c);
            case 65: return emaCross(c, 5, 9);
            case 66: return emaCross(c, 9, 20);
            case 67: return emaCross(c, 20, 50);
            case 68: return movingAverageStack(c);
            case 69: return movingAverageSlope(c);
            case 70: return movingAverageCompression(c);

            case 71: return supportBounce(c);
            case 72: return resistanceReject(c);
            case 73: return higherHighs(c);
            case 74: return lowerLows(c);
            case 75: return higherLows(c);
            case 76: return lowerHighs(c);
            case 77: return structureBreakUp(c);
            case 78: return structureBreakDown(c);
            case 79: return rangePosition(c);
            case 80: return structureStrength(c);

            case 81: return bollingerPosition(c);
            case 82: return bollingerBreakout(c);
            case 83: return bollingerMeanReversion(c);
            case 84: return volatilityExpansion(c);
            case 85: return volatilityContraction(c);
            case 86: return breakoutUp(c);
            case 87: return breakoutDown(c);
            case 88: return falseBreakout(c);
            case 89: return breakoutRetest(c);
            case 90: return rangeBreakPressure(c);

            default: return advancedRule(rule, c);
        }
    }

    // ============================================================
    // BASIC RULES (1-10)
    // ============================================================

    private static int direction(Candle x) {
        if (x.bullish()) return 1;
        if (x.bearish()) return -1;
        return 0;
    }

    private static int bodyDominance(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bodyRatio() > 0.65) return direction(x);
        return 0;
    }

    private static int closeNearHighLow(Candle x) {
        double p = x.closeLocation();
        if (p > 0.80) return 1;
        if (p < 0.20) return -1;
        return 0;
    }

    private static int wickPressure(Candle x) {
        if (x.lowerWick() > x.upperWick() * 1.5) return 1;
        if (x.upperWick() > x.lowerWick() * 1.5) return -1;
        return 0;
    }

    private static int consecutiveDirection(List<Candle> c, int count) {
        if (c.size() < count) return 0;
        int bulls = 0, bears = 0;
        for (int i = c.size() - count; i < c.size(); i++) {
            Candle x = c.get(i);
            if (x.bullish()) bulls++;
            else if (x.bearish()) bears++;
        }
        if (bulls == count) return 1;
        if (bears == count) return -1;
        return 0;
    }

    private static int recentMomentum(List<Candle> c, int n) {
        if (c.size() < n + 1) return 0;
        double start = c.get(c.size() - n - 1).close;
        double end = lastCandle(c).close;
        return compare(end, start);
    }

    private static int candleContinuation(Candle last, Candle prev) {
        if (last.bullish() && prev.bullish()) return 1;
        if (last.bearish() && prev.bearish()) return -1;
        return 0;
    }

    private static int candleReversal(Candle last, Candle prev) {
        if (prev.bearish() && last.bullish() && last.close > prev.open) return 1;
        if (prev.bullish() && last.bearish() && last.close < prev.open) return -1;
        return 0;
    }

    // ============================================================
    // WICK PSYCHOLOGY (11-20)
    // ============================================================

    private static int bullishRejection(Candle x) {
        if (x.lowerWick() > x.body() * 1.5 && x.closeLocation() > 0.55) return 1;
        return 0;
    }

    private static int bearishRejection(Candle x) {
        if (x.upperWick() > x.body() * 1.5 && x.closeLocation() < 0.45) return -1;
        return 0;
    }

    private static int lowerWickPressure(List<Candle> c) {
        int up = 0, down = 0;
        int n = Math.min(5, c.size());
        for (int i = c.size() - n; i < c.size(); i++) {
            Candle x = c.get(i);
            if (x.lowerWick() > x.body()) up++;
            if (x.upperWick() > x.body()) down++;
        }
        return compare(up, down);
    }

    private static int upperWickPressure(List<Candle> c) {
        int up = 0, down = 0;
        int n = Math.min(5, c.size());
        for (int i = c.size() - n; i < c.size(); i++) {
            Candle x = c.get(i);
            if (x.upperWick() > x.body()) down++;
            if (x.lowerWick() > x.body()) up++;
        }
        return compare(up, down);
    }

    private static int strongBullClose(Candle x) {
        if (x.bullish() && x.closeLocation() > 0.75) return 1;
        return 0;
    }

    private static int strongBearClose(Candle x) {
        if (x.bearish() && x.closeLocation() < 0.25) return -1;
        return 0;
    }

    private static int wickAgainstTrend(List<Candle> c) {
        int trend = slopeRule(c, 8);
        Candle x = lastCandle(c);
        if (trend > 0 && x.upperWick() > x.body() * 1.7) return -1;
        if (trend < 0 && x.lowerWick() > x.body() * 1.7) return 1;
        return 0;
    }

    private static int wickContinuation(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.lowerWick() > x.upperWick() && x.closeLocation() > 0.6) return 1;
        if (x.upperWick() > x.lowerWick() && x.closeLocation() < 0.4) return -1;
        return 0;
    }

    private static int rejectionAfterImpulse(List<Candle> c) {
        if (c.size() < 5) return 0;
        double impulse = 0;
        for (int i = c.size() - 5; i < c.size() - 1; i++) {
            impulse += c.get(i).close - c.get(i).open;
        }
        Candle last = lastCandle(c);
        if (impulse > 0 && last.upperWick() > last.body()) return -1;
        if (impulse < 0 && last.lowerWick() > last.body()) return 1;
        return 0;
    }

    private static int exhaustionWick(List<Candle> c) {
        int trend = slopeRule(c, 10);
        Candle x = lastCandle(c);
        if (trend > 0 && x.upperWick() > x.body() * 2) return -1;
        if (trend < 0 && x.lowerWick() > x.body() * 2) return 1;
        return 0;
    }

    // ============================================================
    // TREND (21-30)
    // ============================================================

    private static int slopeRule(List<Candle> c, int n) {
        if (c.size() < n) return 0;
        double first = c.get(c.size() - n).close;
        double last = lastCandle(c).close;
        return compare(last, first);
    }

    private static int emaDirection(List<Candle> c, int period) {
        double ema = ema(c, period);
        return compare(lastCandle(c).close, ema);
    }

    private static int priceVsSma(List<Candle> c, int period) {
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
    // MOMENTUM (31-40)
    // ============================================================

    private static int momentumRule(List<Candle> c, int n) {
        if (c.size() < n + 1) return 0;
        double total = 0;
        for (int i = c.size() - n; i < c.size(); i++) {
            total += c.get(i).close - c.get(i).open;
        }
        return compare(total, 0);
    }

    private static int rocRule(List<Candle> c, int n) {
        if (c.size() <= n) return 0;
        double oldPrice = c.get(c.size() - n - 1).close;
        double current = lastCandle(c).close;
        if (Math.abs(oldPrice) < 0.000001) return 0;
        double roc = ((current - oldPrice) / Math.abs(oldPrice)) * 100.0;
        if (roc > 0.05) return 1;
        if (roc < -0.05) return -1;
        return 0;
    }

    private static int accelerationRule(List<Candle> c) {
        if (c.size() < 5) return 0;
        double recent = c.get(c.size() - 1).close - c.get(c.size() - 2).close;
        double previous = c.get(c.size() - 2).close - c.get(c.size() - 3).close;
        return compare(recent, previous);
    }

    private static int momentumDivergence(List<Candle> c) {
        if (c.size() < 8) return 0;
        double priceOld = c.get(c.size() - 8).close;
        double priceNew = lastCandle(c).close;
        double momOld = c.get(c.size() - 5).close - c.get(c.size() - 8).close;
        double momNew = lastCandle(c).close - c.get(c.size() - 4).close;
        if (priceNew > priceOld && momNew < momOld) return -1;
        if (priceNew < priceOld && momNew > momOld) return 1;
        return 0;
    }

    private static int impulseStrength(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bodyRatio() > 0.75) return direction(x);
        return 0;
    }

    private static int momentumExhaustion(List<Candle> c) {
        if (c.size() < 6) return 0;
        int trend = slopeRule(c, 6);
        Candle x = lastCandle(c);
        if (trend > 0 && x.bodyRatio() < 0.25) return -1;
        if (trend < 0 && x.bodyRatio() < 0.25) return 1;
        return 0;
    }

    // ============================================================
    // CANDLE PATTERNS (41-50)
    // ============================================================

    private static int engulfingBull(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle last = lastCandle(c);
        Candle prev = c.get(c.size() - 2);
        if (prev.bearish() && last.bullish() &&
                last.close > prev.open && last.open < prev.close) return 1;
        return 0;
    }

    private static int engulfingBear(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle last = lastCandle(c);
        Candle prev = c.get(c.size() - 2);
        if (prev.bullish() && last.bearish() &&
                last.close < prev.open && last.open > prev.close) return -1;
        return 0;
    }

    private static int dojiPattern(List<Candle> c) {
        return 0;
    }

    private static int hammerPattern(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.lowerWick() > x.body() * 2 && x.upperWick() < x.body() * 0.5) return 1;
        return 0;
    }

    private static int shootingStarPattern(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.upperWick() > x.body() * 2 && x.lowerWick() < x.body() * 0.5) return -1;
        return 0;
    }

    private static int morningStar(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle d = lastCandle(c);
        if (a.bearish() && b.bodyRatio() < 0.3 && d.bullish() &&
                d.close > (a.open + a.close) / 2) return 1;
        return 0;
    }

    private static int eveningStar(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle d = lastCandle(c);
        if (a.bullish() && b.bodyRatio() < 0.3 && d.bearish() &&
                d.close < (a.open + a.close) / 2) return -1;
        return 0;
    }

    private static int threeWhiteSoldiers(List<Candle> c) {
        if (c.size() < 3) return 0;
        for (int i = c.size() - 3; i < c.size(); i++) {
            if (!c.get(i).bullish()) return 0;
        }
        return 1;
    }

    private static int threeBlackCrows(List<Candle> c) {
        if (c.size() < 3) return 0;
        for (int i = c.size() - 3; i < c.size(); i++) {
            if (!c.get(i).bearish()) return 0;
        }
        return -1;
    }

    private static int haramiPattern(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle last = lastCandle(c);
        Candle prev = c.get(c.size() - 2);
        if (prev.body() > last.body() * 1.5 && prev.bullish() && last.bearish()) return -1;
        if (prev.body() > last.body() * 1.5 && prev.bearish() && last.bullish()) return 1;
        return 0;
    }

    // ============================================================
    // RSI / STOCHASTIC (51-60)
    // ============================================================

    private static int rsiRule(List<Candle> c, int period) {
        double rsi = rsi(c, period);
        if (rsi > 55) return 1;
        if (rsi < 45) return -1;
        return 0;
    }

    private static int rsiOversoldReversal(List<Candle> c) {
        double rsi = rsi(c, 14);
        if (rsi < 30 && lastCandle(c).bullish()) return 1;
        return 0;
    }

    private static int rsiOverboughtReversal(List<Candle> c) {
        double rsi = rsi(c, 14);
        if (rsi > 70 && lastCandle(c).bearish()) return -1;
        return 0;
    }

    private static int rsiTrendConfirmation(List<Candle> c) {
        double rsi = rsi(c, 14);
        if (rsi > 50 && slopeRule(c, 8) > 0) return 1;
        if (rsi < 50 && slopeRule(c, 8) < 0) return -1;
        return 0;
    }

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
    // MACD / MA (61-70)
    // ============================================================

    private static int macdDirection(List<Candle> c) {
        double macd = ema(c, 12) - ema(c, 26);
        return compare(macd, 0);
    }

    private static int macdMomentum(List<Candle> c) {
        if (c.size() < 5) return 0;
        double now = ema(c, 12) - ema(c, 26);
        List<Candle> oldList = new ArrayList<>(c.subList(0, c.size() - 2));
        double old = ema(oldList, 12) - ema(oldList, 26);
        return compare(now, old);
    }

    private static int macdCross(List<Candle> c) {
        if (c.size() < 3) return 0;
        List<Candle> before = new ArrayList<>(c.subList(0, c.size() - 1));
        double oldMacd = ema(before, 12) - ema(before, 26);
        double newMacd = ema(c, 12) - ema(c, 26);
        if (oldMacd <= 0 && newMacd > 0) return 1;
        if (oldMacd >= 0 && newMacd < 0) return -1;
        return 0;
    }

    private static int macdHistogram(List<Candle> c) {
        double macd = ema(c, 12) - ema(c, 26);
        double signal = emaMacdSignal(c);
        return compare(macd, signal);
    }

    private static int emaCross(List<Candle> c, int fast, int slow) {
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
        List<Candle> old = new ArrayList<>(c.subList(0, c.size() - 3));
        double previous = sma(old, 10);
        return compare(now, previous);
    }

    private static int movingAverageCompression(List<Candle> c) {
        double e5 = ema(c, 5);
        double e20 = ema(c, 20);
        double distance = Math.abs(e5 - e20);
        double avgRange = averageRange(c, 10);
        if (distance < avgRange * 0.15) return direction(lastCandle(c));
        return 0;
    }

    // ============================================================
    // SUPPORT / RESISTANCE (71-80)
    // ============================================================

    private static int supportBounce(List<Candle> c) {
        double support = lowestLow(c, 10);
        Candle x = lastCandle(c);
        if (x.low <= support * 1.001 && x.close > x.open) return 1;
        return 0;
    }

    private static int resistanceReject(List<Candle> c) {
        double resistance = highestHigh(c, 10);
        Candle x = lastCandle(c);
        if (x.high >= resistance * 0.999 && x.close < x.open) return -1;
        return 0;
    }

    private static int higherHighs(List<Candle> c) {
        if (c.size() < 6) return 0;
        double oldHigh = highestHigh(c.subList(0, c.size() - 3), 3);
        double newHigh = highestHigh(c, 3);
        return compare(newHigh, oldHigh);
    }

    private static int lowerLows(List<Candle> c) {
        if (c.size() < 6) return 0;
        double oldLow = lowestLow(c.subList(0, c.size() - 3), 3);
        double newLow = lowestLow(c, 3);
        return compare(oldLow, newLow);
    }

    private static int higherLows(List<Candle> c) {
        if (c.size() < 6) return 0;
        double oldLow = lowestLow(c.subList(0, c.size() - 3), 3);
        double newLow = lowestLow(c, 3);
        return compare(newLow, oldLow);
    }

    private static int lowerHighs(List<Candle> c) {
        if (c.size() < 6) return 0;
        double oldHigh = highestHigh(c.subList(0, c.size() - 3), 3);
        double newHigh = highestHigh(c, 3);
        return compare(oldHigh, newHigh);
    }

    private static int structureBreakUp(List<Candle> c) {
        double resistance = highestHigh(c.subList(0, c.size() - 1), 8);
        return lastCandle(c).close > resistance ? 1 : 0;
    }

    private static int structureBreakDown(List<Candle> c) {
        double support = lowestLow(c.subList(0, c.size() - 1), 8);
        return lastCandle(c).close < support ? -1 : 0;
    }

    private static int rangePosition(List<Candle> c) {
        double high = highestHigh(c, 12);
        double low = lowestLow(c, 12);
        double range = Math.max(0.000001, high - low);
        double p = (lastCandle(c).close - low) / range;
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
    // BOLLINGER / VOLATILITY (81-90)
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
        if (now < old * 0.75) return direction(lastCandle(c));
        return 0;
    }

    private static int breakoutUp(List<Candle> c) {
        double resistance = highestHigh(c.subList(0, c.size() - 1), 10);
        return lastCandle(c).close > resistance ? 1 : 0;
    }

    private static int breakoutDown(List<Candle> c) {
        double support = lowestLow(c.subList(0, c.size() - 1), 10);
        return lastCandle(c).close < support ? -1 : 0;
    }

    private static int falseBreakout(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle x = lastCandle(c
  
// ============================================================
// ATR / RANGE (91-100)
// ============================================================

private static int trueRangeDirection(List<Candle> c) {
    double tr = trueRange(c, lastCandle(c));
    double atr = averageTrueRange(c, 14);
    return tr > atr ? direction(lastCandle(c)) : 0;
}

private static int atrExpansion(List<Candle> c) {
    double current = averageTrueRange(c, 5);
    double old = averageTrueRange(c, 14);
    return compare(current, old);
}

private static int atrContraction(List<Candle> c) {
    double current = averageTrueRange(c, 5);
    double old = averageTrueRange(c, 14);
    if (current < old * 0.8) return direction(lastCandle(c));
    return 0;
}

private static int candleRangePressure(List<Candle> c) {
    double current = lastCandle(c).range();
    double avg = averageRange(c, 10);
    if (current > avg * 1.5) return direction(lastCandle(c));
    return 0;
}

private static int bodyRangePressure(List<Candle> c) {
    Candle x = lastCandle(c);
    if (x.bodyRatio() > 0.7) return direction(x);
    return 0;
}

private static int volatilityTrend(List<Candle> c) {
    double shortVol = averageRange(c, 5);
    double longVol = averageRange(c, 15);
    if (shortVol > longVol) return direction(lastCandle(c));
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

private static int pressureBalance(List<Candle> c) {
    double bull = 0, bear = 0;
    int n = Math.min(8, c.size());
    for (int i = c.size() - n; i < c.size(); i++) {
        Candle x = c.get(i);
        if (x.bullish()) bull += x.body() + x.lowerWick() * 0.5;
        if (x.bearish()) bear += x.body() + x.upperWick() * 0.5;
    }
    return compare(bull, bear);
}

private static int buyerSellerDominance(List<Candle> c) {
    double buyers = 0, sellers = 0;
    int n = Math.min(10, c.size());
    for (int i = c.size() - n; i < c.size(); i++) {
        Candle x = c.get(i);
        buyers += x.closeLocation() * x.range();
        sellers += (1.0 - x.closeLocation()) * x.range();
    }
    return compare(buyers, sellers);
}

// ============================================================
// ADVANCED RULE DISPATCHER (101-200)
// ============================================================

private static int advancedRule(int rule, List<Candle> c) {
    switch (rule) {
        case 101: return roundNumberProximity(c);
        case 102: return roundNumberBreak(c);
        case 103: return roundNumberRejection(c);
        case 104: return keyLevelBounce(c);
        case 105: return keyLevelBreak(c);
        case 106: return keyLevelRetest(c);
        case 107: return psychologicalLevel(c);
        case 108: return midLevelRespect(c);
        case 109: return quarterLevelRespect(c);
        case 110: return tripleTop(c);
        case 111: return tripleBottom(c);
        case 112: return doubleTop(c);
        case 113: return doubleBottom(c);
        case 114: return necklineBreakUp(c);
        case 115: return necklineBreakDown(c);
        case 116: return priceCluster(c);
        case 117: return liquidityGrab(c);
        case 118: return stopHuntUp(c);
        case 119: return stopHuntDown(c);
        case 120: return sweepAndReverse(c);
        case 121: return trapDetectionBull(c);
        case 122: return trapDetectionBear(c);
        case 123: return trendExhaustionUp(c);
        case 124: return trendExhaustionDown(c);
        case 125: return multiFactorAgreement(c);
        case 126: return buyerPanic(c);
        case 127: return sellerPanic(c);
        case 128: return fearIndex(c);
        case 129: return greedIndex(c);
        case 130: return momentumAgreement(c);
        case 131: return sentimentShift(c);
        case 132: return capitulation(c);
        case 133: return euphoria(c);
        case 134: return accumulation(c);
        case 135: return distribution(c);
        case 136: return smartMoneyUp(c);
        case 137: return smartMoneyDown(c);
        case 138: return retailTrap(c);
        case 139: return institutionalPressure(c);
        case 140: return hiddenDivergence(c);
        case 141: return tweezerBottom(c);
        case 142: return tweezerTop(c);
        case 143: return marubozuBull(c);
        case 144: return marubozuBear(c);
        case 145: return spinningTop(c);
        case 146: return insideBar(c);
        case 147: return outsideBar(c);
        case 148: return beltHoldBull(c);
        case 149: return beltHoldBear(c);
        case 150: return kickingBull(c);
        case 151: return kickingBear(c);
        case 152: return abandonedBaby(c);
        case 153: return darkCloudCover(c);
        case 154: return piercingLine(c);
        case 155: return threeInsideUp(c);
        case 156: return threeInsideDown(c);
        case 157: return threeOutsideUp(c);
        case 158: return threeOutsideDown(c);
        case 159: return longLeggedDoji(c);
        case 160: return dragonflyDoji(c);
        case 161: return pressureClimaxUp(c);
        case 162: return pressureClimaxDown(c);
        case 163: return absorptionBull(c);
        case 164: return absorptionBear(c);
        case 165: return bodyWickBalance(c);
        case 166: return candleSizeExpansion(c);
        case 167: return candleSizeContraction(c);
        case 168: return bodyToWickRatio(c);
        case 169: return consecutiveRejection(c);
        case 170: return momentumPause(c);
        case 171: return momentumBurst(c);
        case 172: return momentumFade(c);
        case 173: return pressureShift(c);
        case 174: return dominanceShift(c);
        case 175: return buyerExhaustion(c);
        case 176: return sellerExhaustion(c);
        case 177: return lastCandleStrength(c);
        case 178: return lastCandleWeakness(c);
        case 179: return rangeExpansionBias(c);
        case 180: return rangeContractionBias(c);
        case 181: return shortTrendUp(c);
        case 182: return shortTrendDown(c);
        case 183: return midTrendUp(c);
        case 184: return midTrendDown(c);
        case 185: return longTrendUp(c);
        case 186: return longTrendDown(c);
        case 187: return multiTimeframeAgreement(c);
        case 188: return trendContinuationProb(c);
        case 189: return trendReversalProb(c);
        case 190: return overallBias(c);
        case 191: return finalBullPressure(c);
        case 192: return finalBearPressure(c);
        case 193: return finalBodyDominance(c);
        case 194: return finalWickDominance(c);
        case 195: return finalCloseStrength(c);
        case 196: return finalTrendDirection(c);
        case 197: return finalMomentumDirection(c);
        case 198: return finalStructureDirection(c);
        case 199: return finalPsychologyVote(c);
        case 200: return finalConfirmation(c);
        default: return 0;
    }
}

// ============================================================
// ROUND NUMBER + KEY LEVELS (101-120)
// ============================================================

private static int roundNumberProximity(List<Candle> c) {
    double price = lastCandle(c).close;
    double absP = Math.abs(price);
    if (absP < 0.01) return 0;
    double remainder = absP % 0.005;
    double distance = Math.min(remainder, 0.005 - remainder);
    if (distance < 0.0003) return direction(lastCandle(c));
    return 0;
}

private static int roundNumberBreak(List<Candle> c) {
    if (c.size() < 3) return 0;
    Candle last = lastCandle(c);
    Candle prev = c.get(c.size() - 2);
    double p1 = prev.close;
    double p2 = last.close;
    boolean crossed = Math.floor(p1 / 0.005) != Math.floor(p2 / 0.005);
    if (crossed) return direction(last);
    return 0;
}

private static int roundNumberRejection(List<Candle> c) {
    Candle x = lastCandle(c);
    double absP = Math.abs(x.high);
    if (absP < 0.01) return 0;
    double remainder = absP % 0.005;
    if (remainder < 0.0003 && x.bearish()) return -1;
    if (remainder > 0.0047 && x.bullish()) return 1;
    return 0;
}

private static int keyLevelBounce(List<Candle> c) {
    if (c.size() < 20) return 0;
    double high = highestHigh(c, 20);
    double low = lowestLow(c, 20);
    Candle x = lastCandle(c);
    if (x.low <= low * 1.002 && x.bullish()) return 1;
    if (x.high >= high * 0.998 && x.bearish()) return -1;
    return 0;
}

private static int keyLevelBreak(List<Candle> c) {
    if (c.size() < 20) return 0;
    List<Candle> before = c.subList(0, c.size() - 1);
    double high = highestHigh(before, 20);
    double low = lowestLow(before, 20);
    double close = lastCandle(c).close;
    if (close > high) return 1;
    if (close < low) return -1;
    return 0;
}

private static int keyLevelRetest(List<Candle> c) {
    if (c.size() < 5) return 0;
    Candle x = lastCandle(c);
    double recentHigh = highestHigh(c.subList(0, c.size() - 2), 5);
    double recentLow = lowestLow(c.subList(0, c.size() - 2), 5);
    if (x.low <= recentHigh * 1.002 && x.close > recentHigh) return 1;
    if (x.high >= recentLow * 0.998 && x.close < recentLow) return -1;
    return 0;
}

private static int psychologicalLevel(List<Candle> c) {
    double price = Math.abs(lastCandle(c).close);
    if (price < 0.01) return 0;
    double remainder = price % 0.01;
    if (remainder < 0.0005) return direction(lastCandle(c));
    if (remainder > 0.0095) return direction(lastCandle(c));
    return 0;
}

private static int midLevelRespect(List<Candle> c) {
    double mid = sma(c, 20);
    double price = lastCandle(c).close;
    if (Math.abs(price - mid) / Math.max(0.000001, mid) < 0.0005) {
        return direction(lastCandle(c));
    }
    return 0;
}

private static int quarterLevelRespect(List<Candle> c) {
    double price = Math.abs(lastCandle(c).close);
    if (price < 0.01) return 0;
    double remainder = price % 0.0025;
    double distance = Math.min(remainder, 0.0025 - remainder);
    if (distance < 0.0002) return direction(lastCandle(c));
    return 0;
}

private static int tripleTop(List<Candle> c) {
    if (c.size() < 15) return 0;
    double high1 = highestHigh(c.subList(0, 5), 5);
    double high2 = highestHigh(c.subList(5, 10), 5);
    double high3 = highestHigh(c.subList(10, 15), 5);
    double avg = (high1 + high2 + high3) / 3.0;
    double diff = Math.max(Math.max(Math.abs(high1 - avg),
            Math.abs(high2 - avg)), Math.abs(high3 - avg));
    if (diff / Math.max(0.000001, avg) < 0.001) return -1;
    return 0;
}

private static int tripleBottom(List<Candle> c) {
    if (c.size() < 15) return 0;
    double low1 = lowestLow(c.subList(0, 5), 5);
    double low2 = lowestLow(c.subList(5, 10), 5);
    double low3 = lowestLow(c.subList(10, 15), 5);
    double avg = (low1 + low2 + low3) / 3.0;
    double diff = Math.max(Math.max(Math.abs(low1 - avg),
            Math.abs(low2 - avg)), Math.abs(low3 - avg));
    if (diff / Math.max(0.000001, avg) < 0.001) return 1;
    return 0;
}

private static int doubleTop(List<Candle> c) {
    if (c.size() < 10) return 0;
    double high1 = highestHigh(c.subList(0, 5), 5);
    double high2 = highestHigh(c.subList(5, 10), 5);
    double avg = (high1 + high2) / 2.0;
    if (Math.abs(high1 - high2) / Math.max(0.000001, avg) < 0.001) return -1;
    return 0;
}

private static int doubleBottom(List<Candle> c) {
    if (c.size() < 10) return 0;
    double low1 = lowestLow(c.subList(0, 5), 5);
    double low2 = lowestLow(c.subList(5, 10), 5);
    double avg = (low1 + low2) / 2.0;
    if (Math.abs(low1 - low2) / Math.max(0.000001, avg) < 0.001) return 1;
    return 0;
}

private static int necklineBreakUp(List<Candle> c) {
    if (c.size() < 12) return 0;
    List<Candle> pattern = c.subList(c.size() - 12, c.size() - 2);
    double neckline = highestHigh(pattern, 10);
    return lastCandle(c).close > neckline ? 1 : 0;
}

private static int necklineBreakDown(List<Candle> c) {
    if (c.size() < 12) return 0;
    List<Candle> pattern = c.subList(c.size() - 12, c.size() - 2);
    double neckline = lowestLow(pattern, 10);
    return lastCandle(c).close < neckline ? -1 : 0;
}

private static int priceCluster(List<Candle> c) {
    if (c.size() < 20) return 0;
    double range = highestHigh(c, 20) - lowestLow(c, 20);
    double avgRange = averageRange(c, 20);
    if (range < avgRange * 3) return direction(lastCandle(c));
    return 0;
}

private static int liquidityGrab(List<Candle> c) {
    if (c.size() < 10) return 0;
    Candle x = lastCandle(c);
    double high = highestHigh(c.subList(0, c.size() - 1), 8);
    double low = lowestLow(c.subList(0, c.size() - 1), 8);
    if (x.high > high && x.close < high && x.upperWick() > x.body()) return -1;
    if (x.low < low && x.close > low && x.lowerWick() > x.body()) return 1;
    return 0;
}

private static int stopHuntUp(List<Candle> c) {
    if (c.size() < 5) return 0;
    Candle x = lastCandle(c);
    double recentLow = lowestLow(c.subList(0, c.size() - 1), 4);
    if (x.low < recentLow && x.close > recentLow && x.bullish()) return 1;
    return 0;
}

private static int stopHuntDown(List<Candle> c) {
    if (c.size() < 5) return 0;
    Candle x = lastCandle(c);
    double recentHigh = highestHigh(c.subList(0, c.size() - 1), 4);
    if (x.high > recentHigh && x.close < recentHigh && x.bearish()) return -1;
    return 0;
}

private static int sweepAndReverse(List<Candle> c) {
    if (c.size() < 6) return 0;
    Candle x = lastCandle(c);
    double high = highestHigh(c.subList(0, c.size() - 1), 5);
    double low = lowestLow(c.subList(0, c.size() - 1), 5);
    if (x.high > high && x.low < low) return direction(x);
    return 0;
}

// ============================================================
// ADVANCED PSYCHOLOGY (121-140)
// ============================================================

private static int trapDetectionBull(List<Candle> c) {
    if (c.size() < 4) return 0;
    Candle x = lastCandle(c);
    Candle prev = c.get(c.size() - 2);
    if (prev.bearish() && x.low < prev.low && x.close > prev.open) return 1;
    return 0;
}

private static int trapDetectionBear(List<Candle> c) {
    if (c.size() < 4) return 0;
    Candle x = lastCandle(c);
    Candle prev = c.get(c.size() - 2);
    if (prev.bullish() && x.high > prev.high && x.close < prev.open) return -1;
    return 0;
}

private static int trendExhaustionUp(List<Candle> c) {
    int trend = consecutiveDirection(c, 4);
    if (trend <= 0) return 0;
    Candle x = lastCandle(c);
    if (x.bodyRatio() < 0.3 && x.upperWick() > x.body()) return -1;
    return 0;
}

private static int trendExhaustionDown(List<Candle> c) {
    int trend = consecutiveDirection(c, 4);
    if (trend >= 0) return 0;
    Candle x = lastCandle(c);
    if (x.bodyRatio() < 0.3 && x.lowerWick() > x.body()) return 1;
    return 0;
}

private static int multiFactorAgreement(List<Candle> c) {
    int a = slopeRule(c, 8);
    int b = rsiTrendConfirmation(c);
    int d = macdDirection(c);
    int e = direction(lastCandle(c));
    int up = 0, down = 0;
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

private static int buyerPanic(List<Candle> c) {
    if (c.size() < 3) return 0;
    Candle x = lastCandle(c);
    Candle prev = c.get(c.size() - 2);
    if (x.body() > prev.body() * 2 && x.bearish() &&
            x.closeLocation() < 0.15) return 1;
    return 0;
}

private static int sellerPanic(List<Candle> c) {
    if (c.size() < 3) return 0;
    Candle x = lastCandle(c);
    Candle prev = c.get(c.size() - 2);
    if (x.body() > prev.body() * 2 && x.bullish() &&
            x.closeLocation() > 0.85) return -1;
    return 0;
}

private static int fearIndex(List<Candle> c) {
    double rsi = rsi(c, 14);
    if (rsi < 25) return 1;
    return 0;
}

private static int greedIndex(List<Candle> c) {
    double rsi = rsi(c, 14);
    if (rsi > 75) return -1;
    return 0;
}

private static int momentumAgreement(List<Candle> c) {
    int a = momentumRule(c, 3);
    int b = momentumRule(c, 5);
    int d = momentumRule(c, 8);
    if (a > 0 && b > 0 && d > 0) return 1;
    if (a < 0 && b < 0 && d < 0) return -1;
    return 0;
}

private static int sentimentShift(List<Candle> c) {
    if (c.size() < 6) return 0;
    int recent = consecutiveDirection(c, 3);
    List<Candle> older = c.subList(0, c.size() - 3);
    int prev = consecutiveDirection(older, 3);
    if (recent > 0 && prev < 0) return 1;
    if (recent < 0 && prev > 0) return -1;
    return 0;
}

private static int capitulation(List<Candle> c) {
    if (c.size() < 3) return 0;
    Candle x = lastCandle(c);
    if (x.bearish() && x.bodyRatio() > 0.75 && x.closeLocation() < 0.1) {
        double rsi = rsi(c, 14);
        if (rsi < 35) return 1;
    }
    return 0;
}

private static int euphoria(List<Candle> c) {
    if (c.size() < 3) return 0;
    Candle x = lastCandle(c);
    if (x.bullish() && x.bodyRatio() > 0.75 && x.closeLocation() > 0.9) {
        double rsi = rsi(c, 14);
        if (rsi > 65) return -1;
    }
    return 0;
}

private static int accumulation(List<Candle> c) {
    if (c.size() < 10) return 0;
    double range = highestHigh(c, 10) - lowestLow(c, 10);
    double avgRange = averageRange(c, 10);
    if (range < avgRange * 2.5) {
        int slope = slopeRule(c, 10);
        if (slope >= 0) return 1;
    }
    return 0;
}

private static int distribution(List<Candle> c) {
    if (c.size() < 10) return 0;
    double range = highestHigh(c, 10) - lowestLow(c, 10);
    double avgRange = averageRange(c, 10);
    if (range < avgRange * 2.5) {
        int slope = slopeRule(c, 10);
        if (slope <= 0) return -1;
    }
    return 0;
}

private static int smartMoneyUp(List<Candle> c) {
    if (c.size() < 6) return 0;
    double lowOld = lowestLow(c.subList(0, c.size() - 3), 3);
    double lowNew = lowestLow(c, 3);
    double highOld = highestHigh(c.subList(0, c.size() - 3), 3);
    double highNew = highestHigh(c, 3);
    if (lowNew > lowOld && highNew > highOld) return 1;
    return 0;
}

private static int smartMoneyDown(List<Candle> c) {
    if (c.size() < 6) return 0;
    double lowOld = lowestLow(c.subList(0, c.size() - 3), 3);
    double lowNew = lowestLow(c, 3);
    double highOld = highestHigh(c.subList(0, c.size() - 3), 3);
    double highNew = highestHigh(c, 3);
    if (lowNew < lowOld && highNew < highOld) return -1;
    return 0;
}

private static int retailTrap(List<Candle> c) {
    if (c.size() < 5) return 0;
    Candle x = lastCandle(c);
    double high = highestHigh(c.subList(0, c.size() - 1), 4);
    double low = lowestLow(c.subList(0, c.size() - 1), 4);
    if (x.high > high && x.close < high) return -1;
    if (x.low < low && x.close > low) return 1;
    return 0;
}

private static int institutionalPressure(List<Candle> c) {
    if (c.size() < 3) return 0;
    Candle x = lastCandle(c);
    if (x.bodyRatio() > 0.8) return direction(x);
    return 0;
}

private static int hiddenDivergence(List<Candle> c) {
    if (c.size() < 10) return 0;
    double priceOld = c.get(c.size() - 10).close;
    double priceNew = lastCandle(c).close;
    if (priceNew < priceOld) {
        double rsi = rsi(c, 14);
        if (rsi > 50) return 1;
    }
    if (priceNew > priceOld) {
        double rsi = rsi(c, 14);
        if (rsi < 50) return -1;
    }
    return 0;
        }
        
    // ============================================================
    // ADVANCED CANDLE PATTERNS (141-160)
    // ============================================================

    private static int tweezerBottom(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        double diff = Math.abs(a.low - b.low);
        double avg = (Math.abs(a.low) + Math.abs(b.low)) / 2.0;
        if (diff / Math.max(0.000001, avg) < 0.001 && a.bearish() && b.bullish()) return 1;
        return 0;
    }

    private static int tweezerTop(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        double diff = Math.abs(a.high - b.high);
        double avg = (Math.abs(a.high) + Math.abs(b.high)) / 2.0;
        if (diff / Math.max(0.000001, avg) < 0.001 && a.bullish() && b.bearish()) return -1;
        return 0;
    }

    private static int marubozuBull(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bullish() && x.bodyRatio() > 0.95) return 1;
        return 0;
    }

    private static int marubozuBear(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bearish() && x.bodyRatio() > 0.95) return -1;
        return 0;
    }

    private static int spinningTop(List<Candle> c) {
        return 0;
    }

    private static int insideBar(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        if (b.high < a.high && b.low > a.low) return direction(a);
        return 0;
    }

    private static int outsideBar(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        if (b.high > a.high && b.low < a.low) return direction(b);
        return 0;
    }

    private static int beltHoldBull(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bullish() && x.lowerWick() < x.body() * 0.1 && x.bodyRatio() > 0.7) return 1;
        return 0;
    }

    private static int beltHoldBear(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bearish() && x.upperWick() < x.body() * 0.1 && x.bodyRatio() > 0.7) return -1;
        return 0;
    }

    private static int kickingBull(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        if (a.bearish() && b.bullish() && a.bodyRatio() > 0.9 &&
                b.bodyRatio() > 0.9 && b.open > a.close) return 1;
        return 0;
    }

    private static int kickingBear(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        if (a.bullish() && b.bearish() && a.bodyRatio() > 0.9 &&
                b.bodyRatio() > 0.9 && b.open < a.close) return -1;
        return 0;
    }

    private static int abandonedBaby(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle d = lastCandle(c);
        if (a.bearish() && b.doji() && d.bullish() && b.high < a.low) return 1;
        if (a.bullish() && b.doji() && d.bearish() && b.low > a.high) return -1;
        return 0;
    }

    private static int darkCloudCover(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        if (a.bullish() && b.bearish() && b.open > a.high &&
                b.close < (a.open + a.close) / 2) return -1;
        return 0;
    }

    private static int piercingLine(List<Candle> c) {
        if (c.size() < 2) return 0;
        Candle a = c.get(c.size() - 2);
        Candle b = lastCandle(c);
        if (a.bearish() && b.bullish() && b.open < a.low &&
                b.close > (a.open + a.close) / 2) return 1;
        return 0;
    }

    private static int threeInsideUp(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle d = lastCandle(c);
        if (a.bearish() && b.bullish() && b.close < a.open &&
                d.bullish() && d.close > a.open) return 1;
        return 0;
    }

    private static int threeInsideDown(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle d = lastCandle(c);
        if (a.bullish() && b.bearish() && b.close > a.open &&
                d.bearish() && d.close < a.open) return -1;
        return 0;
    }

    private static int threeOutsideUp(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle d = lastCandle(c);
        if (a.bearish() && b.bullish() && b.close > a.open &&
                d.bullish() && d.close > b.close) return 1;
        return 0;
    }

    private static int threeOutsideDown(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle a = c.get(c.size() - 3);
        Candle b = c.get(c.size() - 2);
        Candle d = lastCandle(c);
        if (a.bullish() && b.bearish() && b.close < a.open &&
                d.bearish() && d.close < b.close) return -1;
        return 0;
    }

    private static int longLeggedDoji(List<Candle> c) {
        return 0;
    }

    private static int dragonflyDoji(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bodyRatio() < 0.1 && x.lowerWick() > x.range() * 0.6 &&
                x.upperWick() < x.range() * 0.1) return 1;
        return 0;
    }

    // ============================================================
    // PRESSURE + VOLUME (161-180)
    // ============================================================

    private static int pressureClimaxUp(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle x = lastCandle(c);
        double avgRange = averageRange(c, 10);
        if (x.bullish() && x.range() > avgRange * 2 && x.closeLocation() > 0.8) return 1;
        return 0;
    }

    private static int pressureClimaxDown(List<Candle> c) {
        if (c.size() < 3) return 0;
        Candle x = lastCandle(c);
        double avgRange = averageRange(c, 10);
        if (x.bearish() && x.range() > avgRange * 2 && x.closeLocation() < 0.2) return -1;
        return 0;
    }

    private static int absorptionBull(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.lowerWick() > x.body() * 2 && x.bullish()) return 1;
        return 0;
    }

    private static int absorptionBear(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.upperWick() > x.body() * 2 && x.bearish()) return -1;
        return 0;
    }

    private static int bodyWickBalance(List<Candle> c) {
        Candle x = lastCandle(c);
        double body = x.body();
        double wick = x.upperWick() + x.lowerWick();
        return compare(body, wick);
    }

    private static int candleSizeExpansion(List<Candle> c) {
        if (c.size() < 5) return 0;
        Candle x = lastCandle(c);
        double avg = averageRange(c, 10);
        if (x.range() > avg * 1.4) return direction(x);
        return 0;
    }

    private static int candleSizeContraction(List<Candle> c) {
        if (c.size() < 5) return 0;
        Candle x = lastCandle(c);
        double avg = averageRange(c, 10);
        if (x.range() < avg * 0.6) return direction(x);
        return 0;
    }

    private static int bodyToWickRatio(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bodyRatio() > 0.7) return direction(x);
        if (x.bodyRatio() < 0.3) return -direction(x);
        return 0;
    }

    private static int consecutiveRejection(List<Candle> c) {
        if (c.size() < 4) return 0;
        int up = 0, down = 0;
        for (int i = c.size() - 4; i < c.size(); i++) {
            Candle x = c.get(i);
            if (x.lowerWick() > x.body() * 1.5) up++;
            if (x.upperWick() > x.body() * 1.5) down++;
        }
        return compare(up, down);
    }

    private static int momentumPause(List<Candle> c) {
        if (c.size() < 4) return 0;
        int trend = slopeRule(c, 4);
        Candle x = lastCandle(c);
        if (trend > 0 && x.doji()) return -1;
        if (trend < 0 && x.doji()) return 1;
        return 0;
    }

    private static int momentumBurst(List<Candle> c) {
        if (c.size() < 4) return 0;
        Candle x = lastCandle(c);
        double avg = averageRange(c, 10);
        if (x.range() > avg * 1.8 && x.bodyRatio() > 0.7) return direction(x);
        return 0;
    }

    private static int momentumFade(List<Candle> c) {
        if (c.size() < 6) return 0;
        int trend = slopeRule(c, 6);
        Candle x = lastCandle(c);
        double avg = averageRange(c, 10);
        if (trend > 0 && x.range() < avg * 0.5) return -1;
        if (trend < 0 && x.range() < avg * 0.5) return 1;
        return 0;
    }

    private static int pressureShift(List<Candle> c) {
        if (c.size() < 5) return 0;
        double bullOld = 0, bullNew = 0;
        for (int i = c.size() - 5; i < c.size() - 2; i++) {
            if (c.get(i).bullish()) bullOld++;
        }
        for (int i = c.size() - 3; i < c.size(); i++) {
            if (c.get(i).bullish()) bullNew++;
        }
        return compare(bullNew, bullOld);
    }

    private static int dominanceShift(List<Candle> c) {
        if (c.size() < 6) return 0;
        double buyersOld = 0, buyersNew = 0;
        for (int i = c.size() - 6; i < c.size() - 3; i++) {
            Candle x = c.get(i);
            buyersOld += x.closeLocation();
        }
        for (int i = c.size() - 3; i < c.size(); i++) {
            Candle x = c.get(i);
            buyersNew += x.closeLocation();
        }
        return compare(buyersNew, buyersOld);
    }

    private static int buyerExhaustion(List<Candle> c) {
        if (c.size() < 5) return 0;
        int bulls = 0;
        for (int i = c.size() - 5; i < c.size() - 1; i++) {
            if (c.get(i).bullish()) bulls++;
        }
        if (bulls >= 4 && lastCandle(c).upperWick() > lastCandle(c).body()) return -1;
        return 0;
    }

    private static int sellerExhaustion(List<Candle> c) {
        if (c.size() < 5) return 0;
        int bears = 0;
        for (int i = c.size() - 5; i < c.size() - 1; i++) {
            if (c.get(i).bearish()) bears++;
        }
        if (bears >= 4 && lastCandle(c).lowerWick() > lastCandle(c).body()) return 1;
        return 0;
    }

    private static int lastCandleStrength(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bodyRatio() > 0.7 && x.closeLocation() > 0.7) return 1;
        return 0;
    }

    private static int lastCandleWeakness(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.bodyRatio() > 0.7 && x.closeLocation() < 0.3) return -1;
        return 0;
    }

    private static int rangeExpansionBias(List<Candle> c) {
        double now = averageRange(c, 3);
        double old = averageRange(c, 10);
        if (now > old * 1.3) return direction(lastCandle(c));
        return 0;
    }

    private static int rangeContractionBias(List<Candle> c) {
        double now = averageRange(c, 3);
        double old = averageRange(c, 10);
        if (now < old * 0.7) return direction(lastCandle(c));
        return 0;
    }

    // ============================================================
    // MULTI-TIMEFRAME + FINAL (181-200)
    // ============================================================

    private static int shortTrendUp(List<Candle> c) {
        return slopeRule(c, 5) > 0 ? 1 : 0;
    }

    private static int shortTrendDown(List<Candle> c) {
        return slopeRule(c, 5) < 0 ? -1 : 0;
    }

    private static int midTrendUp(List<Candle> c) {
        return slopeRule(c, 10) > 0 ? 1 : 0;
    }

    private static int midTrendDown(List<Candle> c) {
        return slopeRule(c, 10) < 0 ? -1 : 0;
    }

    private static int longTrendUp(List<Candle> c) {
        return slopeRule(c, 20) > 0 ? 1 : 0;
    }

    private static int longTrendDown(List<Candle> c) {
        return slopeRule(c, 20) < 0 ? -1 : 0;
    }

    private static int multiTimeframeAgreement(List<Candle> c) {
        int s = slopeRule(c, 5);
        int m = slopeRule(c, 10);
        int l = slopeRule(c, 20);
        if (s > 0 && m > 0 && l > 0) return 1;
        if (s < 0 && m < 0 && l < 0) return -1;
        return 0;
    }

    private static int trendContinuationProb(List<Candle> c) {
        int s = slopeRule(c, 5);
        int m = slopeRule(c, 10);
        Candle x = lastCandle(c);
        if (s > 0 && m > 0 && x.bullish()) return 1;
        if (s < 0 && m < 0 && x.bearish()) return -1;
        return 0;
    }

    private static int trendReversalProb(List<Candle> c) {
        int s = slopeRule(c, 5);
        int m = slopeRule(c, 10);
        Candle x = lastCandle(c);
        if (s < 0 && m > 0 && x.bullish()) return 1;
        if (s > 0 && m < 0 && x.bearish()) return -1;
        return 0;
    }

    private static int overallBias(List<Candle> c) {
        int up = 0, down = 0;
        if (slopeRule(c, 5) > 0) up++; else if (slopeRule(c, 5) < 0) down++;
        if (slopeRule(c, 10) > 0) up++; else if (slopeRule(c, 10) < 0) down++;
        if (rsi(c, 14) > 55) up++; else if (rsi(c, 14) < 45) down++;
        if (macdDirection(c) > 0) up++; else if (macdDirection(c) < 0) down++;
        return compare(up, down);
    }

    private static int finalBullPressure(List<Candle> c) {
        double bull = 0, bear = 0;
        int n = Math.min(6, c.size());
        for (int i = c.size() - n; i < c.size(); i++) {
            Candle x = c.get(i);
            if (x.bullish()) bull += x.body() + x.lowerWick();
            if (x.bearish()) bear += x.body() + x.upperWick();
        }
        if (bull > bear * 1.2) return 1;
        return 0;
    }

    private static int finalBearPressure(List<Candle> c) {
        double bull = 0, bear = 0;
        int n = Math.min(6, c.size());
        for (int i = c.size() - n; i < c.size(); i++) {
            Candle x = c.get(i);
            if (x.bullish()) bull += x.body() + x.lowerWick();
            if (x.bearish()) bear += x.body() + x.upperWick();
        }
        if (bear > bull * 1.2) return -1;
        return 0;
    }

    private static int finalBodyDominance(List<Candle> c) {
        Candle x = lastCandle(c);
        return direction(x);
    }

    private static int finalWickDominance(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.lowerWick() > x.upperWick() * 1.5) return 1;
        if (x.upperWick() > x.lowerWick() * 1.5) return -1;
        return 0;
    }

    private static int finalCloseStrength(List<Candle> c) {
        Candle x = lastCandle(c);
        if (x.closeLocation() > 0.75) return 1;
        if (x.closeLocation() < 0.25) return -1;
        return 0;
    }

    private static int finalTrendDirection(List<Candle> c) {
        return slopeRule(c, 8);
    }

    private static int finalMomentumDirection(List<Candle> c) {
        return momentumRule(c, 5);
    }

    private static int finalStructureDirection(List<Candle> c) {
        return structureStrength(c);
    }

    private static int finalPsychologyVote(List<Candle> c) {
        int p = pressureBalance(c);
        int d = buyerSellerDominance(c);
        int m = momentumRule(c, 5);
        int up = 0, down = 0;
        if (p > 0) up++; if (p < 0) down++;
        if (d > 0) up++; if (d < 0) down++;
        if (m > 0) up++; if (m < 0) down++;
        if (up >= 2) return 1;
        if (down >= 2) return -1;
        return 0;
    }

    private static int finalConfirmation(List<Candle> c) {
        int a = slopeRule(c, 8);
        int b = direction(lastCandle(c));
        int d = rsiTrendConfirmation(c);
        int e = macdDirection(c);
        int up = 0, down = 0;
        if (a > 0) up++; if (a < 0) down++;
        if (b > 0) up++; if (b < 0) down++;
        if (d > 0) up++; if (d < 0) down++;
        if (e > 0) up++; if (e < 0) down++;
        if (up >= 3) return 1;
        if (down >= 3) return -1;
        return 0;
    }

    // ============================================================
    // CANDLE EXTRACTION (HSV-based)
    // ============================================================

    private static List<Candle> extractCandles(Bitmap bitmap) {

        List<Candle> candles = new ArrayList<>();

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width < 50 || height < 50) return candles;

        int top = (int) (height * 0.12);
        int bottom = (int) (height * 0.88);

        int step = Math.max(2, width / 180);

        List<Double> centers = new ArrayList<>();
        List<Boolean> bullish = new ArrayList<>();

        float[] hsv = new float[3];

        for (int x = 2; x < width - 2; x += step) {

            int red = 0, green = 0;
            double high = Double.MAX_VALUE;
            double low = -Double.MAX_VALUE;
            double sum = 0;
            int count = 0;

            for (int y = top; y < bottom; y += 2) {

                int pixel = bitmap.getPixel(x, y);
                Color.colorToHSV(pixel, hsv);

                float h = hsv[0];
                float s = hsv[1];
                float v = hsv[2];

                if (s < 0.35f || v < 0.25f) continue;

                if (h >= 80 && h <= 160) {
                    green++;
                    high = Math.min(high, y);
                    low = Math.max(low, y);
                    sum += y;
                    count++;
                } else if ((h >= 0 && h <= 25) || (h >= 340 && h <= 360)) {
                    red++;
                    high = Math.min(high, y);
                    low = Math.max(low, y);
                    sum += y;
                    count++;
                }
            }

            if (count >= 2) {
                centers.add(sum / Math.max(1, count));
                bullish.add(green >= red);
            }
        }

        if (centers.size() < 40) return fallbackCandles(bitmap);

        int groupSize = Math.max(1, centers.size() / 60);

        for (int i = 0; i < centers.size(); i += groupSize) {

            int end = Math.min(centers.size(), i + groupSize);
            if (end <= i) continue;

            double high = Double.MAX_VALUE;
            double low = -Double.MAX_VALUE;
            double open = centers.get(i);
            double close = centers.get(end - 1);
            int bullCount = 0;

            for (int j = i; j < end; j++) {
                high = Math.min(high, centers.get(j));
                low = Math.max(low, centers.get(j));
                if (bullish.get(j)) bullCount++;
            }

            double o = -open;
            double cl = -close;
            double h = -high;
            double l = -low;

            if (bullCount >= (end - i) / 2.0) {
                if (cl <= o) cl = o + Math.max(0.5, Math.abs(o) * 0.002);
            } else {
                if (cl >= o) cl = o - Math.max(0.5, Math.abs(o) * 0.002);
            }

            h = Math.max(h, Math.max(o, cl));
            l = Math.min(l, Math.min(o, cl));

            candles.add(new Candle(o, h, l, cl));
        }

        return candles;
    }

    private static List<Candle> fallbackCandles(Bitmap bitmap) {

        List<Candle> result = new ArrayList<>();

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int count = Math.min(60, Math.max(40, width / 12));
        double previous = -(height * 0.50);

        float[] hsv = new float[3];

        for (int i = 0; i < count; i++) {

            int x = (int) (((double) i / count) * width);
            int top = (int) (height * 0.15);
            int bottom = (int) (height * 0.85);

            int green = 0, red = 0;
            int 
