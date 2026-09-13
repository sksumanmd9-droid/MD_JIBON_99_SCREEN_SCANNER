package com.mdjibon.scanner;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Screenshot candle analyzer.
 *
 * NOTE: The 200 checks are deterministic technical heuristics. They do not
 * guarantee a trading accuracy percentage; the returned confidence is only
 * the percentage of rules voting in the stronger direction.
 */
public final class Analyzer {
    private Analyzer() {}

    public static final int TOTAL_RULES = 200;
    private static final double EPS = 0.000001;

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

    private static final class Candle {
        final double open, high, low, close;
        Candle(double open, double high, double low, double close) {
            this.open = open;
            this.high = Math.max(high, Math.max(open, close));
            this.low = Math.min(low, Math.min(open, close));
            this.close = close;
        }
        double body() { return Math.abs(close - open); }
        double range() { return Math.max(EPS, high - low); }
        double upperWick() { return Math.max(0.0, high - Math.max(open, close)); }
        double lowerWick() { return Math.max(0.0, Math.min(open, close) - low); }
        boolean bullish() { return close > open; }
        boolean bearish() { return close < open; }
        boolean doji() { return body() <= range() * 0.12; }
        double bodyRatio() { return body() / range(); }
        double closeLocation() { return clamp((close - low) / range(), 0.0, 1.0); }
    }

    private static final class Score { int up, down, neutral; }

    public static Result analyze(Bitmap bitmap) {
        Result r = new Result();
        if (bitmap == null || bitmap.isRecycled()) return r;

        List<Candle> c = extractCandles(bitmap);
        r.detectedCandles = c.size();
        if (c.size() < 40) return r;

        r.quality = calculateChartQuality(bitmap, c);
        if (r.quality < 25.0) return r;

        Score s = new Score();
        for (int rule = 1; rule <= TOTAL_RULES; rule++) {
            int vote;
            try {
                vote = evaluateRule(rule, c);
            } catch (RuntimeException ignored) {
                vote = 0;
            }
            vote = Integer.compare(vote, 0);
            if (vote > 0) { s.up++; r.bullish++; }
            else if (vote < 0) { s.down++; r.bearish++; }
            else { s.neutral++; r.neutral++; }
            r.evaluatedRules++;
        }

        int majority = Math.max(s.up, s.down);
        r.confidence = majority * 100.0 / TOTAL_RULES;
        int minAgreement = (int) Math.ceil(TOTAL_RULES * 0.65);
        if (s.up >= minAgreement && s.up > s.down) r.signal = "UP";
        else if (s.down >= minAgreement && s.down > s.up) r.signal = "DOWN";
        else r.signal = "NO TRADE";

        r.timeframe = "SCREEN";
        r.candleSize = c.size() + " candles";
        return r;
    }

    private static int evaluateRule(int rule, List<Candle> c) {
        Candle x = last(c), p = prev(c);
        switch (rule) {
            case 1:return direction(x); case 2:return bodyDominance(c); case 3:return closeNearHighLow(x);
            case 4:return wickPressure(x); case 5:return consecutiveDirection(c,3); case 6:return consecutiveDirection(c,5);
            case 7:return recentMomentum(c,3); case 8:return recentMomentum(c,5); case 9:return candleContinuation(x,p); case 10:return candleReversal(x,p);
            case 11:return bullishRejection(x); case 12:return bearishRejection(x); case 13:return lowerWickPressure(c); case 14:return upperWickPressure(c);
            case 15:return strongBullClose(x); case 16:return strongBearClose(x); case 17:return wickAgainstTrend(c); case 18:return wickContinuation(c);
            case 19:return rejectionAfterImpulse(c); case 20:return exhaustionWick(c);
            case 21:return slopeRule(c,5); case 22:return slopeRule(c,8); case 23:return slopeRule(c,12); case 24:return emaDirection(c,5);
            case 25:return emaDirection(c,9); case 26:return emaDirection(c,20); case 27:return priceVsSma(c,5); case 28:return priceVsSma(c,10);
            case 29:return priceVsSma(c,20); case 30:return trendAlignment(c);
            case 31:return momentumRule(c,3); case 32:return momentumRule(c,5); case 33:return momentumRule(c,8); case 34:return rocRule(c,3);
            case 35:return rocRule(c,5); case 36:return rocRule(c,8); case 37:return accelerationRule(c); case 38:return momentumDivergence(c);
            case 39:return impulseStrength(c); case 40:return momentumExhaustion(c);
            case 41:return engulfingBull(c); case 42:return engulfingBear(c); case 43:return dojiPattern(c); case 44:return hammerPattern(c);
            case 45:return shootingStarPattern(c); case 46:return morningStar(c); case 47:return eveningStar(c); case 48:return threeWhiteSoldiers(c);
            case 49:return threeBlackCrows(c); case 50:return haramiPattern(c);
            case 51:return rsiRule(c,7); case 52:return rsiRule(c,14); case 53:return rsiOversoldReversal(c); case 54:return rsiOverboughtReversal(c);
            case 55:return rsiTrendConfirmation(c); case 56:return stochasticRule(c); case 57:return stochasticReversal(c); case 58:return oscillatorAgreement(c);
            case 59:return oscillatorMomentum(c); case 60:return oscillatorExhaustion(c);
            case 61:return macdDirection(c); case 62:return macdMomentum(c); case 63:return macdCross(c); case 64:return macdHistogram(c);
            case 65:return emaCross(c,5,9); case 66:return emaCross(c,9,20); case 67:return emaCross(c,20,50); case 68:return movingAverageStack(c);
            case 69:return movingAverageSlope(c); case 70:return movingAverageCompression(c);
            case 71:return supportBounce(c); case 72:return resistanceReject(c); case 73:return higherHighs(c); case 74:return lowerLows(c);
            case 75:return higherLows(c); case 76:return lowerHighs(c); case 77:return structureBreakUp(c); case 78:return structureBreakDown(c);
            case 79:return rangePosition(c); case 80:return structureStrength(c);
            case 81:return bollingerPosition(c); case 82:return bollingerBreakout(c); case 83:return bollingerMeanReversion(c); case 84:return volatilityExpansion(c);
            case 85:return volatilityContraction(c); case 86:return breakoutUp(c); case 87:return breakoutDown(c); case 88:return falseBreakout(c);
            case 89:return breakoutRetest(c); case 90:return rangeBreakPressure(c);
            case 91:return trueRangeDirection(c); case 92:return atrExpansion(c); case 93:return atrContraction(c); case 94:return candleRangePressure(c);
            case 95:return bodyRangePressure(c); case 96:return volatilityTrend(c); case 97:return exhaustionAfterRun(c); case 98:return reversalAfterExtreme(c);
            case 99:return pressureBalance(c); case 100:return buyerSellerDominance(c);
            default:return advancedRule(rule,c);
        }
    }

    private static int advancedRule(int rule,List<Candle> c) {
        switch(rule) {
            case 101:return roundNumberProximity(c); case 102:return roundNumberBreak(c); case 103:return roundNumberRejection(c); case 104:return keyLevelBounce(c);
            case 105:return keyLevelBreak(c); case 106:return keyLevelRetest(c); case 107:return psychologicalLevel(c); case 108:return midLevelRespect(c);
            case 109:return quarterLevelRespect(c); case 110:return tripleTop(c); case 111:return tripleBottom(c); case 112:return doubleTop(c);
            case 113:return doubleBottom(c); case 114:return necklineBreakUp(c); case 115:return necklineBreakDown(c); case 116:return priceCluster(c);
            case 117:return liquidityGrab(c); case 118:return stopHuntUp(c); case 119:return stopHuntDown(c); case 120:return sweepAndReverse(c);
            case 121:return trapDetectionBull(c); case 122:return trapDetectionBear(c); case 123:return trendExhaustionUp(c); case 124:return trendExhaustionDown(c);
            case 125:return multiFactorAgreement(c); case 126:return buyerPanic(c); case 127:return sellerPanic(c); case 128:return fearIndex(c);
            case 129:return greedIndex(c); case 130:return momentumAgreement(c); case 131:return sentimentShift(c); case 132:return capitulation(c);
            case 133:return euphoria(c); case 134:return accumulation(c); case 135:return distribution(c); case 136:return smartMoneyUp(c);
            case 137:return smartMoneyDown(c); case 138:return retailTrap(c); case 139:return institutionalPressure(c); case 140:return hiddenDivergence(c);
            case 141:return tweezerBottom(c); case 142:return tweezerTop(c); case 143:return marubozuBull(c); case 144:return marubozuBear(c);
            case 145:return spinningTop(c); case 146:return insideBar(c); case 147:return outsideBar(c); case 148:return beltHoldBull(c);
            case 149:return beltHoldBear(c); case 150:return kickingBull(c); case 151:return kickingBear(c); case 152:return abandonedBaby(c);
            case 153:return darkCloudCover(c); case 154:return piercingLine(c); case 155:return threeInsideUp(c); case 156:return threeInsideDown(c);
            case 157:return threeOutsideUp(c); case 158:return threeOutsideDown(c); case 159:return longLeggedDoji(c); case 160:return dragonflyDoji(c);
            case 161:return pressureClimaxUp(c); case 162:return pressureClimaxDown(c); case 163:return absorptionBull(c); case 164:return absorptionBear(c);
            case 165:return bodyWickBalance(c); case 166:return candleSizeExpansion(c); case 167:return candleSizeContraction(c); case 168:return bodyToWickRatio(c);
            case 169:return consecutiveRejection(c); case 170:return momentumPause(c); case 171:return momentumBurst(c); case 172:return momentumFade(c);
            case 173:return pressureShift(c); case 174:return dominanceShift(c); case 175:return buyerExhaustion(c); case 176:return sellerExhaustion(c);
            case 177:return lastCandleStrength(c); case 178:return lastCandleWeakness(c); case 179:return rangeExpansionBias(c); case 180:return rangeContractionBias(c);
            case 181:return shortTrendUp(c); case 182:return shortTrendDown(c); case 183:return midTrendUp(c); case 184:return midTrendDown(c);
            case 185:return longTrendUp(c); case 186:return longTrendDown(c); case 187:return multiTimeframeAgreement(c); case 188:return trendContinuationProb(c);
            case 189:return trendReversalProb(c); case 190:return overallBias(c); case 191:return finalBullPressure(c); case 192:return finalBearPressure(c);
            case 193:return finalBodyDominance(c); case 194:return finalWickDominance(c); case 195:return finalCloseStrength(c); case 196:return finalTrendDirection(c);
            case 197:return finalMomentumDirection(c); case 198:return finalStructureDirection(c); case 199:return finalPsychologyVote(c); case 200:return finalConfirmation(c);
            default:return 0;
        }
    }

    // -------------------- Core candle rules --------------------
    private static int direction(Candle x){return x.bullish()?1:x.bearish()?-1:0;}
    private static int bodyDominance(List<Candle> c){Candle x=last(c);return x.bodyRatio()>0.65?direction(x):0;}
    private static int closeNearHighLow(Candle x){return x.closeLocation()>0.80?1:x.closeLocation()<0.20?-1:0;}
    private static int wickPressure(Candle x){return x.lowerWick()>x.upperWick()*1.5?1:x.upperWick()>x.lowerWick()*1.5?-1:0;}
    private static int consecutiveDirection(List<Candle> c,int n){if(c.size()<n)return 0;int b=0,d=0;for(int i=c.size()-n;i<c.size();i++){if(c.get(i).bullish())b++;else if(c.get(i).bearish())d++;}return b==n?1:d==n?-1:0;}
    private static int recentMomentum(List<Candle> c,int n){return c.size()>n?compare(last(c).close,c.get(c.size()-n-1).close):0;}
    private static int candleContinuation(Candle x,Candle p){return x.bullish()&&p.bullish()?1:x.bearish()&&p.bearish()?-1:0;}
    private static int candleReversal(Candle x,Candle p){return p.bearish()&&x.bullish()&&x.close>p.open?1:p.bullish()&&x.bearish()&&x.close<p.open?-1:0;}
    private static int bullishRejection(Candle x){return x.lowerWick()>x.body()*1.5&&x.closeLocation()>0.55?1:0;}
    private static int bearishRejection(Candle x){return x.upperWick()>x.body()*1.5&&x.closeLocation()<0.45?-1:0;}
    private static int lowerWickPressure(List<Candle> c){int u=0,d=0,n=Math.min(5,c.size());for(int i=c.size()-n;i<c.size();i++){Candle x=c.get(i);if(x.lowerWick()>x.body())u++;if(x.upperWick()>x.body())d++;}return compare(u,d);}
    private static int upperWickPressure(List<Candle> c){int u=0,d=0,n=Math.min(5,c.size());for(int i=c.size()-n;i<c.size();i++){Candle x=c.get(i);if(x.upperWick()>x.body())d++;if(x.lowerWick()>x.body())u++;}return compare(u,d);}
    private static int strongBullClose(Candle x){return x.bullish()&&x.closeLocation()>0.75?1:0;}
    private static int strongBearClose(Candle x){return x.bearish()&&x.closeLocation()<0.25?-1:0;}
    private static int wickAgainstTrend(List<Candle> c){int t=slopeRule(c,8);Candle x=last(c);return t>0&&x.upperWick()>x.body()*1.7?-1:t<0&&x.lowerWick()>x.body()*1.7?1:0;}
    private static int wickContinuation(List<Candle> c){Candle x=last(c);return x.lowerWick()>x.upperWick()&&x.closeLocation()>0.6?1:x.upperWick()>x.lowerWick()&&x.closeLocation()<0.4?-1:0;}
    private static int rejectionAfterImpulse(List<Candle> c){if(c.size()<5)return 0;double imp=0;for(int i=c.size()-5;i<c.size()-1;i++)imp+=c.get(i).close-c.get(i).open;Candle x=last(c);return imp>0&&x.upperWick()>x.body()?-1:imp<0&&x.lowerWick()>x.body()?1:0;}
    private static int exhaustionWick(List<Candle> c){int t=slopeRule(c,10);Candle x=last(c);return t>0&&x.upperWick()>x.body()*2?-1:t<0&&x.lowerWick()>x.body()*2?1:0;}

    // -------------------- Trend / momentum --------------------
    private static int slopeRule(List<Candle> c,int n){return c.size()>=n?compare(last(c).close,c.get(c.size()-n).close):0;}
    private static int emaDirection(List<Candle> c,int p){return compare(last(c).close,ema(c,p));}
    private static int priceVsSma(List<Candle> c,int p){return compare(last(c).close,sma(c,p));}
    private static int trendAlignment(List<Candle> c){double a=ema(c,5),b=ema(c,9),d=ema(c,20);return a>b&&b>d?1:a<b&&b<d?-1:0;}
    private static int momentumRule(List<Candle> c,int n){if(c.size()<n)return 0;double v=0;for(int i=c.size()-n;i<c.size();i++)v+=c.get(i).close-c.get(i).open;return compare(v,0);}
    private static int rocRule(List<Candle> c,int n){if(c.size()<=n)return 0;double old=c.get(c.size()-n-1).close,now=last(c).close;if(Math.abs(old)<EPS)return 0;double roc=(now-old)/Math.abs(old)*100;return roc>0.05?1:roc<-0.05?-1:0;}
    private static int accelerationRule(List<Candle> c){if(c.size()<3)return 0;return compare(last(c).close-prev(c).close,prev(c).close-c.get(c.size()-3).close);}
    private static int momentumDivergence(List<Candle> c){if(c.size()<8)return 0;double po=c.get(c.size()-8).close,pn=last(c).close,mo=c.get(c.size()-5).close-po,mn=last(c).close-c.get(c.size()-4).close;return pn>po&&mn<mo?-1:pn<po&&mn>mo?1:0;}
    private static int impulseStrength(List<Candle> c){Candle x=last(c);return x.bodyRatio()>0.75?direction(x):0;}
    private static int momentumExhaustion(List<Candle> c){int t=slopeRule(c,6);Candle x=last(c);return t>0&&x.bodyRatio()<0.25?-1:t<0&&x.bodyRatio()<0.25?1:0;}

    // -------------------- Candle patterns --------------------
    private static int engulfingBull(List<Candle> c){Candle a=prev(c),b=last(c);return a.bearish()&&b.bullish()&&b.close>a.open&&b.open<a.close?1:0;}
    private static int engulfingBear(List<Candle> c){Candle a=prev(c),b=last(c);return a.bullish()&&b.bearish()&&b.close<a.open&&b.open>a.close?-1:0;}
    private static int dojiPattern(List<Candle> c){Candle x=last(c);if(!x.doji())return 0;return x.lowerWick()>x.upperWick()*1.3?1:x.upperWick()>x.lowerWick()*1.3?-1:0;}
    private static int hammerPattern(List<Candle> c){Candle x=last(c);return x.lowerWick()>x.body()*2&&x.upperWick()<x.body()*0.5?1:0;}
    private static int shootingStarPattern(List<Candle> c){Candle x=last(c);return x.upperWick()>x.body()*2&&x.lowerWick()<x.body()*0.5?-1:0;}
    private static int morningStar(List<Candle> c){if(c.size()<3)return 0;Candle a=c.get(c.size()-3),b=prev(c),d=last(c);return a.bearish()&&b.bodyRatio()<0.3&&d.bullish()&&d.close>(a.open+a.close)/2?1:0;}
    private static int eveningStar(List<Candle> c){if(c.size()<3)return 0;Candle a=c.get(c.size()-3),b=prev(c),d=last(c);return a.bullish()&&b.bodyRatio()<0.3&&d.bearish()&&d.close<(a.open+a.close)/2?-1:0;}
    private static int threeWhiteSoldiers(List<Candle> c){return consecutiveDirection(c,3)==1?1:0;}
    private static int threeBlackCrows(List<Candle> c){return consecutiveDirection(c,3)==-1?-1:0;}
    private static int haramiPattern(List<Candle> c){Candle a=prev(c),b=last(c);boolean inside=Math.max(b.open,b.close)<Math.max(a.open,a.close)&&Math.min(b.open,b.close)>Math.min(a.open,a.close);return a.bearish()&&b.bullish()&&inside?1:a.bullish()&&b.bearish()&&inside?-1:0;}

    // -------------------- Oscillators --------------------
    private static int rsiRule(List<Candle> c,int p){double r=rsi(c,p);return r>55?1:r<45?-1:0;}
    private static int rsiOversoldReversal(List<Candle> c){return rsi(c,14)<30&&last(c).bullish()?1:0;}
    private static int rsiOverboughtReversal(List<Candle> c){return rsi(c,14)>70&&last(c).bearish()?-1:0;}
    private static int rsiTrendConfirmation(List<Candle> c){double r=rsi(c,14);int t=slopeRule(c,8);return r>50&&t>0?1:r<50&&t<0?-1:0;}
    private static int stochasticRule(List<Candle> c){double k=stochastic(c,14);return k>55?1:k<45?-1:0;}
    private static int stochasticReversal(List<Candle> c){double k=stochastic(c,14);return k<20&&last(c).bullish()?1:k>80&&last(c).bearish()?-1:0;}
    private static int oscillatorAgreement(List<Candle> c){double r=rsi(c,14),s=stochastic(c,14);return r>55&&s>55?1:r<45&&s<45?-1:0;}
    private static int oscillatorMomentum(List<Candle> c){double r=rsi(c,7),s=stochastic(c,9);return r>60&&s>60?1:r<40&&s<40?-1:0;}
    private static int oscillatorExhaustion(List<Candle> c){double r=rsi(c,14);return r>75?-1:r<25?1:0;}

    // -------------------- MACD / moving averages --------------------
    private static int macdDirection(List<Candle> c){return compare(ema(c,12)-ema(c,26),0);}
    private static int macdMomentum(List<Candle> c){if(c.size()<4)return 0;double now=ema(c,12)-ema(c,26);List<Candle> old=c.subList(0,c.size()-2);return compare(now,ema(old,12)-ema(old,26));}
    private static int macdCross(List<Candle> c){if(c.size()<3)return 0;List<Candle> old=c.subList(0,c.size()-1);double a=ema(old,12)-ema(old,26),b=ema(c,12)-ema(c,26);return a<=0&&b>0?1:a>=0&&b<0?-1:0;}
    private static int macdHistogram(List<Candle> c){return compare(ema(c,12)-ema(c,26),emaMacdSignal(c));}
    private static int emaCross(List<Candle> c,int f,int s){return compare(ema(c,f),ema(c,s));}
    private static int movingAverageStack(List<Candle> c){return trendAlignment(c);}
    private static int movingAverageSlope(List<Candle> c){if(c.size()<13)return 0;return compare(sma(c,10),sma(c.subList(0,c.size()-3),10));}
    private static int movingAverageCompression(List<Candle> c){return Math.abs(ema(c,5)-ema(c,20))<averageRange(c,10)*0.15?direction(last(c)):0;}

    // -------------------- Structure / volatility --------------------
    private static int supportBounce(List<Candle> c){double s=lowestHighSafeLow(c,10);return last(c).low<=s*1.001&&last(c).bullish()?1:0;}
    private static int resistanceReject(List<Candle> c){double h=highestHigh(c,10);return last(c).high>=h*0.999&&last(c).bearish()?-1:0;}
    private static int higherHighs(List<Candle> c){if(c.size()<6)return 0;return compare(highestHigh(c.subList(0,c.size()-3),3),highestHigh(c,3));}
    private static int lowerLows(List<Candle> c){if(c.size()<6)return 0;return compare(lowestLow(c.subList(0,c.size()-3),3),lowestLow(c,3));}
    private static int higherLows(List<Candle> c){if(c.size()<6)return 0;return compare(lowestLow(c,3),lowestLow(c.subList(0,c.size()-3),3));}
    private static int lowerHighs(List<Candle> c){if(c.size()<6)return 0;return compare(highestHigh(c.subList(0,c.size()-3),3),highestHigh(c,3));}
    private static int structureBreakUp(List<Candle> c){List<Candle> b=withoutLast(c);return last(c).close>highestHigh(b,8)?1:0;}
    private static int structureBreakDown(List<Candle> c){List<Candle> b=withoutLast(c);return last(c).close<lowestLow(b,8)?-1:0;}
    private static int rangePosition(List<Candle> c){double h=highestHigh(c,12),l=lowestLow(c,12),p=(last(c).close-l)/Math.max(EPS,h-l);return p>0.70?1:p<0.30?-1:0;}
    private static int structureStrength(List<Candle> c){int hh=higherHighs(c),hl=higherLows(c),lh=lowerHighs(c),ll=lowerLows(c);return hh>0&&hl>0?1:lh<0&&ll<0?-1:0;}
    private static int bollingerPosition(List<Candle> c){double m=sma(c,20),sd=standardDeviation(c,20),p=last(c).close;return p>m+2*sd?1:p<m-2*sd?-1:compare(p,m);}
    private static int bollingerBreakout(List<Candle> c){double m=sma(c,20),sd=standardDeviation(c,20),p=last(c).close;return p>m+2*sd?1:p<m-2*sd?-1:0;}
    private static int bollingerMeanReversion(List<Candle> c){double m=sma(c,20),sd=standardDeviation(c,20);Candle x=last(c);return x.close>m+2*sd&&x.bearish()?-1:x.close<m-2*sd&&x.bullish()?1:0;}
    private static int volatilityExpansion(List<Candle> c){return compare(averageRange(c,3),averageRange(c,10));}
    private static int volatilityContraction(List<Candle> c){return averageRange(c,3)<averageRange(c,10)*0.75?direction(last(c)):0;}
    private static int breakoutUp(List<Candle> c){return last(c).close>highestHigh(withoutLast(c),10)?1:0;}
    private static int breakoutDown(List<Candle> c){return last(c).close<lowestLow(withoutLast(c),10)?-1:0;}
    private static int falseBreakout(List<Candle> c){Candle x=last(c);List<Candle>b=withoutLast(c);double h=highestHigh(b,8),l=lowestLow(b,8);return x.high>h&&x.close<h?-1:x.low<l&&x.close>l?1:0;}
    private static int breakoutRetest(List<Candle> c){List<Candle>b=c.subList(0,Math.max(1,c.size()-2));double h=highestHigh(b,6),l=lowestLow(b,6);Candle x=last(c);return x.low<=h&&x.close>h?1:x.high>=l&&x.close<l?-1:0;}
    private static int rangeBreakPressure(List<Candle> c){return last(c).range()>averageRange(c,8)*1.4?direction(last(c)):0;}
    private static int trueRangeDirection(List<Candle> c){return trueRange(c,last(c))>averageTrueRange(c,14)?direction(last(c)):0;}
    private static int atrExpansion(List<Candle> c){return compare(averageTrueRange(c,5),averageTrueRange(c,14));}
    private static int atrContraction(List<Candle> c){return averageTrueRange(c,5)<averageTrueRange(c,14)*0.8?direction(last(c)):0;}
    private static int candleRangePressure(List<Candle> c){return last(c).range()>averageRange(c,10)*1.5?direction(last(c)):0;}
    private static int bodyRangePressure(List<Candle> c){return last(c).bodyRatio()>0.7?direction(last(c)):0;}
    private static int volatilityTrend(List<Candle> c){return averageRange(c,5)>averageRange(c,15)?direction(last(c)):0;}
    private static int exhaustionAfterRun(List<Candle> c){int t=consecutiveDirection(c,5);return t>0&&last(c).bodyRatio()<0.25?-1:t<0&&last(c).bodyRatio()<0.25?1:0;}
    private static int reversalAfterExtreme(List<Candle> c){double r=rsi(c,14);return r>75&&last(c).bearish()?-1:r<25&&last(c).bullish()?1:0;}
    private static int pressureBalance(List<Candle> c){double b=0,d=0;for(int i=Math.max(0,c.size()-8);i<c.size();i++){Candle x=c.get(i);if(x.bullish())b+=x.body()+x.lowerWick()*0.5;else if(x.bearish())d+=x.body()+x.upperWick()*0.5;}return compare(b,d);}
    private static int buyerSellerDominance(List<Candle> c){double b=0,s=0;for(int i=Math.max(0,c.size()-10);i<c.size();i++){Candle x=c.get(i);b+=x.closeLocation()*x.range();s+=(1-x.closeLocation())*x.range();}return compare(b,s);}

    // -------------------- Advanced levels / psychology --------------------
    private static int roundNumberProximity(List<Candle> c){return levelDirection(last(c).close,0.005,0.0003,last(c));}
    private static int roundNumberBreak(List<Candle> c){if(c.size()<2)return 0;double a=prev(c).close,b=last(c).close;return Math.floor(a/0.005)!=Math.floor(b/0.005)?direction(last(c)):0;}
    private static int roundNumberRejection(List<Candle> c){return levelDirection(last(c).high,0.005,0.0003,last(c));}
    private static int keyLevelBounce(List<Candle> c){double h=highestHigh(c,20),l=lowestLow(c,20);Candle x=last(c);return x.low<=l*1.002&&x.bullish()?1:x.high>=h*0.998&&x.bearish()?-1:0;}
    private static int keyLevelBreak(List<Candle> c){List<Candle>b=withoutLast(c);double h=highestHigh(b,20),l=lowestLow(b,20);return last(c).close>h?1:last(c).close<l?-1:0;}
    private static int keyLevelRetest(List<Candle> c){List<Candle>b=withoutLast(c);double h=highestHigh(b,5),l=lowestLow(b,5);Candle x=last(c);return x.low<=h*1.002&&x.close>h?1:x.high>=l*0.998&&x.close<l?-1:0;}
    private static int psychologicalLevel(List<Candle> c){return levelDirection(last(c).close,0.01,0.0005,last(c));}
    private static int midLevelRespect(List<Candle> c){double m=sma(c,20);return Math.abs(last(c).close-m)/Math.max(EPS,Math.abs(m))<0.0005?direction(last(c)):0;}
    private static int quarterLevelRespect(List<Candle> c){return levelDirection(last(c).close,0.0025,0.0002,last(c));}
    private static int tripleTop(List<Candle> c){return repeatedHigh(c,15,3)?-1:0;}
    private static int tripleBottom(List<Candle> c){return repeatedLow(c,15,3)?1:0;}
    private static int doubleTop(List<Candle> c){return repeatedHigh(c,10,2)?-1:0;}
    private static int doubleBottom(List<Candle> c){return repeatedLow(c,10,2)?1:0;}
    private static int necklineBreakUp(List<Candle> c){return last(c).close>highestHigh(c.subList(Math.max(0,c.size()-12),Math.max(1,c.size()-2)),10)?1:0;}
    private static int necklineBreakDown(List<Candle> c){return last(c).close<lowestLow(c.subList(Math.max(0,c.size()-12),Math.max(1,c.size()-2)),10)?-1:0;}
    private static int priceCluster(List<Candle> c){return highestHigh(c,20)-lowestLow(c,20)<averageRange(c,20)*3?direction(last(c)):0;}
    private static int liquidityGrab(List<Candle> c){return falseBreakout(c);}
    private static int stopHuntUp(List<Candle> c){Candle x=last(c);double l=lowestLow(withoutLast(c),4);return x.low<l&&x.close>l&&x.bullish()?1:0;}
    private static int stopHuntDown(List<Candle> c){Candle x=last(c);double h=highestHigh(withoutLast(c),4);return x.high>h&&x.close<h&&x.bearish()?-1:0;}
    private static int sweepAndReverse(List<Candle> c){Candle x=last(c);double h=highestHigh(withoutLast(c),5),l=lowestLow(withoutLast(c),5);return x.high>h&&x.low<l?direction(x):0;}
    private static int trapDetectionBull(List<Candle> c){Candle x=last(c),p=prev(c);return p.bearish()&&x.low<p.low&&x.close>p.open?1:0;}
    private static int trapDetectionBear(List<Candle> c){Candle x=last(c),p=prev(c);return p.bullish()&&x.high>p.high&&x.close<p.open?-1:0;}
    private static int trendExhaustionUp(List<Candle> c){return consecutiveDirection(c,4)>0&&last(c).bodyRatio()<0.3&&last(c).upperWick()>last(c).body()?-1:0;}
    private static int trendExhaustionDown(List<Candle> c){return consecutiveDirection(c,4)<0&&last(c).bodyRatio()<0.3&&last(c).lowerWick()>last(c).body()?1:0;}
    private static int multiFactorAgreement(List<Candle> c){return vote(slopeRule(c,8),rsiTrendConfirmation(c),macdDirection(c),direction(last(c)),3);}
    private static int buyerPanic(List<Candle> c){return last(c).bearish()&&last(c).body()>prev(c).body()*2&&last(c).closeLocation()<0.15?1:0;}
    private static int sellerPanic(List<Candle> c){return last(c).bullish()&&last(c).body()>prev(c).body()*2&&last(c).closeLocation()>0.85?-1:0;}
    private static int fearIndex(List<Candle> c){return rsi(c,14)<25?1:0;}
    private static int greedIndex(List<Candle> c){return rsi(c,14)>75?-1:0;}
    private static int momentumAgreement(List<Candle> c){return vote(momentumRule(c,3),momentumRule(c,5),momentumRule(c,8),0,3);}
    private static int sentimentShift(List<Candle> c){if(c.size()<6)return 0;return vote(consecutiveDirection(c,3),-consecutiveDirection(c.subList(0,c.size()-3),3),0,0,1);}
    private static int capitulation(List<Candle> c){Candle x=last(c);return x.bearish()&&x.bodyRatio()>0.75&&x.closeLocation()<0.1&&rsi(c,14)<35?1:0;}
    private static int euphoria(List<Candle> c){Candle x=last(c);return x.bullish()&&x.bodyRatio()>0.75&&x.closeLocation()>0.9&&rsi(c,14)>65?-1:0;}
    private static int accumulation(List<Candle> c){return highestHigh(c,10)-lowestLow(c,10)<averageRange(c,10)*2.5&&slopeRule(c,10)>=0?1:0;}
    private static int distribution(List<Candle> c){return highestHigh(c,10)-lowestLow(c,10)<averageRange(c,10)*2.5&&slopeRule(c,10)<=0?-1:0;}
    private static int smartMoneyUp(List<Candle> c){return higherLows(c)>0&&higherHighs(c)>0?1:0;}
    private static int smartMoneyDown(List<Candle> c){return lowerLows(c)<0&&lowerHighs(c)<0?-1:0;}
    private static int retailTrap(List<Candle> c){return falseBreakout(c);}
    private static int institutionalPressure(List<Candle> c){return last(c).bodyRatio()>0.8?direction(last(c)):0;}
    private static int hiddenDivergence(List<Candle> c){double p=last(c).close-c.get(Math.max(0,c.size()-10)).close;double r=rsi(c,14);return p<0&&r>50?1:p>0&&r<50?-1:0;}

    // -------------------- Advanced candle patterns --------------------
    private static int tweezerBottom(List<Candle> c){Candle a=prev(c),b=last(c);double avg=(Math.abs(a.low)+Math.abs(b.low))/2;return Math.abs(a.low-b.low)/Math.max(EPS,avg)<0.001&&a.bearish()&&b.bullish()?1:0;}
    private static int tweezerTop(List<Candle> c){Candle a=prev(c),b=last(c);double avg=(Math.abs(a.high)+Math.abs(b.high))/2;return Math.abs(a.high-b.high)/Math.max(EPS,avg)<0.001&&a.bullish()&&b.bearish()?-1:0;}
    private static int marubozuBull(List<Candle> c){return last(c).bullish()&&last(c).bodyRatio()>0.95?1:0;}
    private static int marubozuBear(List<Candle> c){return last(c).bearish()&&last(c).bodyRatio()>0.95?-1:0;}
    private static int spinningTop(List<Candle> c){Candle x=last(c);return x.bodyRatio()<0.30&&x.upperWick()>x.body()&&x.lowerWick()>x.body()?direction(x):0;}
    private static int insideBar(List<Candle> c){Candle a=prev(c),b=last(c);return b.high<a.high&&b.low>a.low?direction(a):0;}
    private static int outsideBar(List<Candle> c){Candle a=prev(c),b=last(c);return b.high>a.high&&b.low<a.low?direction(b):0;}
    private static int beltHoldBull(List<Candle> c){Candle x=last(c);return x.bullish()&&x.lowerWick()<x.body()*0.1&&x.bodyRatio()>0.7?1:0;}
    private static int beltHoldBear(List<Candle> c){Candle x=last(c);return x.bearish()&&x.upperWick()<x.body()*0.1&&x.bodyRatio()>0.7?-1:0;}
    private static int kickingBull(List<Candle> c){Candle a=prev(c),b=last(c);return a.bearish()&&b.bullish()&&a.bodyRatio()>0.9&&b.bodyRatio()>0.9&&b.open>a.close?1:0;}
    private static int kickingBear(List<Candle> c){Candle a=prev(c),b=last(c);return a.bullish()&&b.bearish()&&a.bodyRatio()>0.9&&b.bodyRatio()>0.9&&b.open<a.close?-1:0;}
    private static int abandonedBaby(List<Candle> c){if(c.size()<3)return 0;Candle a=c.get(c.size()-3),b=prev(c),d=last(c);return a.bearish()&&b.doji()&&d.bullish()&&b.high<a.low?1:a.bullish()&&b.doji()&&d.bearish()&&b.low>a.high?-1:0;}
    private static int darkCloudCover(List<Candle> c){Candle a=prev(c),b=last(c);return a.bullish()&&b.bearish()&&b.open>a.high&&b.close<(a.open+a.close)/2?-1:0;}
    private static int piercingLine(List<Candle> c){Candle a=prev(c),b=last(c);return a.bearish()&&b.bullish()&&b.open<a.low&&b.close>(a.open+a.close)/2?1:0;}
    private static int threeInsideUp(List<Candle> c){if(c.size()<3)return 0;Candle a=c.get(c.size()-3),b=prev(c),d=last(c);return a.bearish()&&b.bullish()&&d.bullish()&&d.close>a.open?1:0;}
    private static int threeInsideDown(List<Candle> c){if(c.size()<3)return 0;Candle a=c.get(c.size()-3),b=prev(c),d=last(c);return a.bullish()&&b.bearish()&&d.bearish()&&d.close<a.open?-1:0;}
    private static int threeOutsideUp(List<Candle> c){if(c.size()<3)return 0;return engulfingBull(c)==1&&last(c).bullish()?1:0;}
    private static int threeOutsideDown(List<Candle> c){if(c.size()<3)return 0;return engulfingBear(c)==-1&&last(c).bearish()?-1:0;}
    private static int longLeggedDoji(List<Candle> c){Candle x=last(c);return x.doji()&&x.upperWick()>x.range()*0.3&&x.lowerWick()>x.range()*0.3?wickPressure(x):0;}
    private static int dragonflyDoji(List<Candle> c){Candle x=last(c);return x.bodyRatio()<0.1&&x.lowerWick()>x.range()*0.6&&x.upperWick()<x.range()*0.1?1:0;}

    // -------------------- Pressure / final rules --------------------
    private static int pressureClimaxUp(List<Candle> c){Candle x=last(c);return x.bullish()&&x.range()>averageRange(c,10)*2&&x.closeLocation()>0.8?1:0;}
    private static int pressureClimaxDown(List<Candle> c){Candle x=last(c);return x.bearish()&&x.range()>averageRange(c,10)*2&&x.closeLocation()<0.2?-1:0;}
    private static int absorptionBull(List<Candle> c){Candle x=last(c);return x.lowerWick()>x.body()*2&&x.bullish()?1:0;}
    private static int absorptionBear(List<Candle> c){Candle x=last(c);return x.upperWick()>x.body()*2&&x.bearish()?-1:0;}
    private static int bodyWickBalance(List<Candle> c){Candle x=last(c);return compare(x.body(),x.upperWick()+x.lowerWick());}
    private static int candleSizeExpansion(List<Candle> c){return last(c).range()>averageRange(c,10)*1.4?direction(last(c)):0;}
    private static int candleSizeContraction(List<Candle> c){return last(c).range()<averageRange(c,10)*0.6?direction(last(c)):0;}
    private static int bodyToWickRatio(List<Candle> c){Candle x=last(c);return x.bodyRatio()>0.7?direction(x):x.bodyRatio()<0.3?-direction(x):0;}
    private static int consecutiveRejection(List<Candle> c){int u=0,d=0;for(int i=Math.max(0,c.size()-4);i<c.size();i++){Candle x=c.get(i);if(x.lowerWick()>x.body()*1.5)u++;if(x.upperWick()>x.body()*1.5)d++;}return compare(u,d);}
    private static int momentumPause(List<Candle> c){int t=slopeRule(c,4);return t>0&&last(c).doji()?-1:t<0&&last(c).doji()?1:0;}
    private static int momentumBurst(List<Candle> c){return last(c).range()>averageRange(c,10)*1.8&&last(c).bodyRatio()>0.7?direction(last(c)):0;}
    private static int momentumFade(List<Candle> c){int t=slopeRule(c,6);return t>0&&last(c).range()<averageRange(c,10)*0.5?-1:t<0&&last(c).range()<averageRange(c,10)*0.5?1:0;}
    private static int pressureShift(List<Candle> c){if(c.size()<5)return 0;double old=0,nw=0;for(int i=c.size()-5;i<c.size()-2;i++)if(c.get(i).bullish())old++;for(int i=c.size()-3;i<c.size();i++)if(c.get(i).bullish())nw++;return compare(nw,old);}
    private static int dominanceShift(List<Candle> c){if(c.size()<6)return 0;double a=0,b=0;for(int i=c.size()-6;i<c.size()-3;i++)a+=c.get(i).closeLocation();for(int i=c.size()-3;i<c.size();i++)b+=c.get(i).closeLocation();return compare(b,a);}
    private static int buyerExhaustion(List<Candle> c){int b=0;for(int i=Math.max(0,c.size()-5);i<c.size()-1;i++)if(c.get(i).bullish())b++;return b>=4&&last(c).upperWick()>last(c).body()?-1:0;}
    private static int sellerExhaustion(List<Candle> c){int b=0;for(int i=Math.max(0,c.size()-5);i<c.size()-1;i++)if(c.get(i).bearish())b++;return b>=4&&last(c).lowerWick()>last(c).body()?1:0;}
    private static int lastCandleStrength(List<Candle> c){Candle x=last(c);return x.bodyRatio()>0.7&&x.closeLocation()>0.7?1:0;}
    private static int lastCandleWeakness(List<Candle> c){Candle x=last(c);return x.bodyRatio()>0.7&&x.closeLocation()<0.3?-1:0;}
    private static int rangeExpansionBias(List<Candle> c){return averageRange(c,3)>averageRange(c,10)*1.3?direction(last(c)):0;}
    private static int rangeContractionBias(List<Candle> c){return averageRange(c,3)<averageRange(c,10)*0.7?direction(last(c)):0;}
    private static int shortTrendUp(List<Candle> c){return slopeRule(c,5)>0?1:0;} private static int shortTrendDown(List<Candle> c){return slopeRule(c,5)<0?-1:0;}
    private static int midTrendUp(List<Candle> c){return slopeRule(c,10)>0?1:0;} private static int midTrendDown(List<Candle> c){return slopeRule(c,10)<0?-1:0;}
    private static int longTrendUp(List<Candle> c){return slopeRule(c,20)>0?1:0;} private static int longTrendDown(List<Candle> c){return slopeRule(c,20)<0?-1:0;}
    private static int multiTimeframeAgreement(List<Candle> c){return vote(slopeRule(c,5),slopeRule(c,10),slopeRule(c,20),0,3);}
    private static int trendContinuationProb(List<Candle> c){int s=slopeRule(c,5),m=slopeRule(c,10);return s>0&&m>0&&last(c).bullish()?1:s<0&&m<0&&last(c).bearish()?-1:0;}
    private static int trendReversalProb(List<Candle> c){int s=slopeRule(c,5),m=slopeRule(c,10);return s<0&&m>0&&last(c).bullish()?1:s>0&&m<0&&last(c).bearish()?-1:0;}
    private static int overallBias(List<Candle> c){return vote(slopeRule(c,5),slopeRule(c,10),rsiRule(c,14),macdDirection(c),2);}
    private static int finalBullPressure(List<Candle> c){double b=0,d=0;for(int i=Math.max(0,c.size()-6);i<c.size();i++){Candle x=c.get(i);if(x.bullish())b+=x.body()+x.lowerWick();else if(x.bearish())d+=x.body()+x.upperWick();}return b>d*1.2?1:0;}
    private static int finalBearPressure(List<Candle> c){double b=0,d=0;for(int i=Math.max(0,c.size()-6);i<c.size();i++){Candle x=c.get(i);if(x.bullish())b+=x.body()+x.lowerWick();else if(x.bearish())d+=x.body()+x.upperWick();}return d>b*1.2?-1:0;}
    private static int finalBodyDominance(List<Candle> c){return direction(last(c));}
    private static int finalWickDominance(List<Candle> c){return wickPressure(last(c));}
    private static int finalCloseStrength(List<Candle> c){return closeNearHighLow(last(c));}
    private static int finalTrendDirection(List<Candle> c){return slopeRule(c,8);}
    private static int finalMomentumDirection(List<Candle> c){return momentumRule(c,5);}
    private static int finalStructureDirection(List<Candle> c){return structureStrength(c);}
    private static int finalPsychologyVote(List<Candle> c){return vote(pressureBalance(c),buyerSellerDominance(c),momentumRule(c,5),0,2);}
    private static int finalConfirmation(List<Candle> c){return vote(slopeRule(c,8),direction(last(c)),rsiTrendConfirmation(c),macdDirection(c),3);}

    // -------------------- Screenshot extraction --------------------
    private static List<Candle> extractCandles(Bitmap bitmap){
        List<Candle> out=new ArrayList<>(); int w=bitmap.getWidth(),h=bitmap.getHeight();
        if(w<50||h<50)return out;
        int top=(int)(h*.12),bottom=(int)(h*.88),step=Math.max(2,w/180);float[] hsv=new float[3];
        List<Double> centers=new ArrayList<>();List<Boolean> bulls=new ArrayList<>();
        for(int x=2;x<w-2;x+=step){int green=0,red=0,minY=bottom,maxY=top;double sum=0;int count=0;
            for(int y=top;y<bottom;y+=2){Color.colorToHSV(bitmap.getPixel(x,y),hsv);float hue=hsv[0],sat=hsv[1],val=hsv[2];if(sat<.35f||val<.25f)continue;
                boolean g=hue>=80&&hue<=160, r=(hue<=25||hue>=340);if(!g&&!r)continue;if(g)green++;else red++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);sum+=y;count++;}
            if(count>=2){centers.add(sum/count);bulls.add(green>=red);}
        }
        if(centers.size()<40)return fallbackCandles(bitmap);
        int group=Math.max(1,centers.size()/60);
        for(int i=0;i<centers.size();i+=group){int end=Math.min(centers.size(),i+group);double high=Double.MAX_VALUE,low=-Double.MAX_VALUE,o=centers.get(i),cl=centers.get(end-1);int bc=0;
            for(int j=i;j<end;j++){high=Math.min(high,centers.get(j));low=Math.max(low,centers.get(j));if(bulls.get(j))bc++;}
            double open=-o,close=-cl;if(bc>=(end-i)/2.0){if(close<=open)close=open+Math.max(.5,Math.abs(open)*.002);}else if(close>=open)close=open-Math.max(.5,Math.abs(open)*.002);
            out.add(new Candle(open,-high,-low,close));
        }return out;
    }
    private static List<Candle> fallbackCandles(Bitmap bitmap){
        List<Candle> out=new ArrayList<>();int w=bitmap.getWidth(),h=bitmap.getHeight(),count=Math.min(60,Math.max(40,w/12));double previous=-(h*.5);float[] hsv=new float[3];
        for(int i=0;i<count;i++){int x=(int)(((double)i/count)*w);int top=(int)(h*.15),bottom=(int)(h*.85),green=0,red=0,minY=bottom,maxY=top;
            for(int y=top;y<bottom;y+=3){Color.colorToHSV(bitmap.getPixel(Math.min(w-1,Math.max(0,x)),y),hsv);float hu=hsv[0],s=hsv[1],v=hsv[2];if(s<.35f||v<.25f)continue;if(hu>=80&&hu<=160){green++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}else if(hu<=25||hu>=340){red++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}
            if(green+red<2)continue;double close=-(minY+maxY)/2.0,open=previous;if(green>=red)close=Math.max(close,open+.5);else close=Math.min(close,open-.5);out.add(new Candle(open,-minY,-maxY,close));previous=close;}
        return out;
    }

    private static double calculateChartQuality(Bitmap b,List<Candle> c){if(c.size()<40)return 0;double count=Math.min(100,c.size()*1.5),move=0;for(int i=1;i<c.size();i++)move+=Math.abs(c.get(i).close-c.get(i-1).close);return clamp(count*.6+(move>0?50:0)*.4,0,100);}

    // -------------------- Math helpers --------------------
    private static double sma(List<Candle> c,int p){if(c.isEmpty())return 0;int n=Math.min(p,c.size());double s=0;for(int i=c.size()-n;i<c.size();i++)s+=c.get(i).close;return s/n;}
    private static double ema(List<Candle> c,int p){if(c.isEmpty())return 0;double v=c.get(0).close,m=2.0/(Math.min(p,c.size())+1.0);for(int i=1;i<c.size();i++)v=(c.get(i).close-v)*m+v;return v;}
    private static double rsi(List<Candle> c,int p){if(c.size()<2)return 50;int n=Math.min(p,c.size()-1);double g=0,l=0;for(int i=c.size()-n;i<c.size();i++){double d=c.get(i).close-c.get(i-1).close;if(d>0)g+=d;else l-=d;}if(l<EPS)return g>EPS?100:50;double rs=g/l;return 100-(100/(1+rs));}
    private static double stochastic(List<Candle> c,int p){double h=highestHigh(c,p),l=lowestLow(c,p);return h-l<EPS?50:(last(c).close-l)/(h-l)*100;}
    private static double emaMacdSignal(List<Candle> c){List<Candle>s=new ArrayList<>();int start=Math.max(0,c.size()-20);for(int i=start;i<c.size();i++){double m=ema(c.subList(0,i+1),12)-ema(c.subList(0,i+1),26);s.add(new Candle(m,m,m,m));}return ema(s,9);}
    private static double trueRange(List<Candle> c,Candle x){int i=c.indexOf(x);if(i<=0)return x.range();Candle p=c.get(i-1);return Math.max(x.high-x.low,Math.max(Math.abs(x.high-p.close),Math.abs(x.low-p.close)));}
    private static double averageTrueRange(List<Candle> c,int p){if(c.size()<2)return last(c).range();int st=Math.max(1,c.size()-p);double s=0;int n=0;for(int i=st;i<c.size();i++){Candle x=c.get(i),q=c.get(i-1);s+=Math.max(x.high-x.low,Math.max(Math.abs(x.high-q.close),Math.abs(x.low-q.close)));n++;}return n==0?last(c).range():s/n;}
    private static double averageRange(List<Candle> c,int p){int n=Math.min(p,c.size());double s=0;for(int i=c.size()-n;i<c.size();i++)s+=c.get(i).range();return s/Math.max(1,n);}
    private static double standardDeviation(List<Candle> c,int p){int n=Math.min(p,c.size());double m=sma(c,n),s=0;for(int i=c.size()-n;i<c.size();i++){double d=c.get(i).close-m;s+=d*d;}return Math.sqrt(s/Math.max(1,n));}
    private static double highestHigh(List<Candle> c,int p){if(c.isEmpty())return 0;int n=Math.min(p,c.size());double h=-Double.MAX_VALUE;for(int i=c.size()-n;i<c.size();i++)h=Math.max(h,c.get(i).high);return h;}
    private static double lowestLow(List<Candle> c,int p){if(c.isEmpty())return 0;int n=Math.min(p,c.size());double l=Double.MAX_VALUE;for(int i=c.size()-n;i<c.size();i++)l=Math.min(l,c.get(i).low);return l;}
    private static double lowestHighSafeLow(List<Candle> c,int p){return lowestLow(c,p);}
    private static Candle last(List<Candle> c){return c.get(c.size()-1);}
    private static Candle prev(List<Candle> c){return c.get(c.size()-2);}
    private static List<Candle> withoutLast(List<Candle> c){return c.subList(0,Math.max(1,c.size()-1));}
    private static int compare(double a,double b){double d=a-b,scale=Math.max(EPS,Math.abs(a)+Math.abs(b)),n=d/scale;return n>.003?1:n<-.003?-1:0;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static int vote(int a,int b,int d,int e,int needed){int u=0,n=0;if(a>0)u++;else if(a<0)n++;if(b>0)u++;else if(b<0)n++;if(d>0)u++;else if(d<0)n++;if(e>0)u++;else if(e<0)n++;return u>=needed?1:n>=needed?-1:0;}
    private static int levelDirection(double price,double step,double tolerance,Candle x){double p=Math.abs(price);if(p<EPS)return 0;double rem=p%step; double dist=Math.min(rem,step-rem);return dist<tolerance?direction(x):0;}
    private static boolean repeatedHigh(List<Candle> c,int total,int parts){if(c.size()<total)return false;int n=total/parts;double[] a=new double[parts];double avg=0;for(int j=0;j<parts;j++){a[j]=highestHigh(c.subList(c.size()-total+j*n,c.size()-total+(j+1)*n),n);avg+=a[j];}avg/=parts;for(double v:a)if(Math.abs(v-avg)/Math.max(EPS,Math.abs(avg))>=.001)return false;return true;}
    private static boolean repeatedLow(List<Candle> c,int total,int parts){if(c.size()<total)return false;int n=total/parts;double[] a=new double[parts];double avg=0;for(int j=0;j<parts;j++){a[j]=lowestLow(c.subList(c.size()-total+j*n,c.size()-total+(j+1)*n),n);avg+=a[j];}avg/=parts;for(double v:a)if(Math.abs(v-avg)/Math.max(EPS,Math.abs(avg))>=.001)return false;return true;}
}
