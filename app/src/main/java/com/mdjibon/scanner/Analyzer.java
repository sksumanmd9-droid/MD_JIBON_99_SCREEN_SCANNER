package com.mdjibon.scanner;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * MD JIBON screenshot/floating-chart analyzer.
 *
 * Important:
 * - This class uses only pixels visible in the supplied screenshot.
 * - 1000 probes are grouped into independent feature families; they are not
 *   1000 guaranteed independent indicators.
 * - Confidence is an evidence score, not a guaranteed win probability.
 */
public final class Analyzer {

    private Analyzer() {}

    public static final int TOTAL_RULES = 1000;

    public static final class Result {
        public String signal = "NO TRADE";
        public double confidence = 0.0;
        public double quality = 0.0;
        public int bullishCount = 0;
        public int bearishCount = 0;
        public int neutralCount = TOTAL_RULES;
        public int detectedCandles = 0;
        public int evaluatedRules = 0;
        public String timeframe = "1 MIN";
        public String nextCandleColor = "UNKNOWN";
        public String nextCandleSize = "UNKNOWN";
        public double nextBodyRatio = 0.0;
        public double nextUpperWickRatio = 0.0;
        public double nextLowerWickRatio = 0.0;
        public double nextRangeRatio = 0.0;
        public final List<String> checks = new ArrayList<>();
    }

    private static final class Candle {
        float x;
        float open;
        float close;
        float high;
        float low;
        boolean green;

        float body() { return Math.abs(close - open); }
        float range() { return Math.max(0.001f, Math.abs(high - low)); }
        float upperWick() { return Math.max(0f, high - Math.max(open, close)); }
        float lowerWick() { return Math.max(0f, Math.min(open, close) - low); }
        float bodyRatio() { return body() / range(); }
    }

    private static final class Component {
        int minX, maxX, minY, maxY, area;
        float cx;
    }

    private static final class Probe {
        double value;
        Probe(double value) { this.value = clamp(value, -1.0, 1.0); }
    }

    public static Result analyze(Bitmap source) {
        Result out = new Result();
        if (source == null || source.isRecycled()) {
            out.checks.add("No screenshot supplied");
            return out;
        }

        Bitmap work = source;
        boolean madeCopy = false;
        try {
            int maxW = 1000;
            if (source.getWidth() > maxW) {
                int h = Math.max(1, Math.round(source.getHeight() * (maxW / (float) source.getWidth())));
                work = Bitmap.createScaledBitmap(source, maxW, h, true);
                madeCopy = true;
            }

            List<Candle> candles = extractCandles(work);
            out.detectedCandles = candles.size();

            if (candles.size() < 12) {
                out.signal = "NO TRADE";
                out.quality = chartQuality(candles);
                out.checks.add("Insufficient real candles: " + candles.size() + "/12");
                out.checks.add("No artificial candle fallback was used.");
                return out;
            }

            out.quality = chartQuality(candles);

            List<Probe> probes = new ArrayList<>(TOTAL_RULES);

            // 1-250: multi-window trend / structure
            for (int i = 0; i < 250; i++) {
                int window = 5 + (i % Math.min(20, Math.max(5, candles.size() - 1)));
                double s = slope(candles, window);
                double recent = normalizedRecentMove(candles, Math.min(window, candles.size() - 1));
                double value = 0.58 * Math.tanh(s * 7.0) + 0.42 * recent;
                probes.add(new Probe(value));
            }

            // 251-500: momentum / ROC / RSI
            for (int i = 0; i < 250; i++) {
                int period = 3 + (i % 18);
                double roc = roc(candles, period);
                double rsi = rsi(candles, Math.min(period + 5, 21));
                double rsiBias = (rsi - 50.0) / 50.0;
                double value = 0.55 * Math.tanh(roc * 18.0) + 0.45 * rsiBias;
                probes.add(new Probe(value));
            }

            // 501-700: candle geometry / patterns
            for (int i = 0; i < 200; i++) {
                int offset = i % Math.min(8, candles.size());
                double value = candleGeometryScore(candles, offset);
                probes.add(new Probe(value));
            }

            // 701-850: levels / volatility / breakout
            for (int i = 0; i < 150; i++) {
                int period = 8 + (i % 20);
                double value = levelAndVolatilityScore(candles, period);
                probes.add(new Probe(value));
            }

            // 851-1000: independent confirmations and conflict penalties
            double trend = trendScore(candles);
            double momentum = momentumScore(candles);
            double pattern = patternScore(candles);
            double level = levelAndVolatilityScore(candles, Math.min(20, candles.size()));
            double geometry = candleGeometryScore(candles, 0);
            for (int i = 0; i < 150; i++) {
                double value;
                switch (i % 6) {
                    case 0: value = 0.55 * trend + 0.45 * momentum; break;
                    case 1: value = 0.60 * momentum + 0.40 * pattern; break;
                    case 2: value = 0.55 * pattern + 0.45 * level; break;
                    case 3: value = 0.55 * trend + 0.45 * level; break;
                    case 4: value = 0.50 * geometry + 0.50 * pattern; break;
                    default: value = 0.40 * trend + 0.35 * momentum + 0.25 * level;
                }
                probes.add(new Probe(value));
            }

            double sum = 0.0;
            int bull = 0, bear = 0;
            for (Probe p : probes) {
                sum += p.value;
                if (p.value > 0.08) bull++;
                else if (p.value < -0.08) bear++;
            }

            out.evaluatedRules = probes.size();
            out.bullishCount = bull;
            out.bearishCount = bear;
            out.neutralCount = Math.max(0, TOTAL_RULES - bull - bear);

            double signed = sum / Math.max(1, probes.size());
            double agreement = Math.max(bull, bear) / (double) TOTAL_RULES;
            double directionalStrength = Math.abs(signed);
            double confidence = 50.0 + directionalStrength * 50.0;
            // Quality gates the displayed score rather than manufacturing a high score.
            confidence *= (0.72 + 0.28 * (out.quality / 100.0));
            out.confidence = clamp(confidence, 0.0, 99.0);

            boolean directionBull = signed > 0;
            boolean decisive = agreement >= 0.55 && directionalStrength >= 0.10;
            if (out.quality < 45.0 || !decisive) {
                out.signal = "NO TRADE";
            } else {
                out.signal = directionBull ? "UP" : "DOWN";
            }

            fillPrediction(out, candles, signed);
            out.timeframe = "1 MIN";
            out.checks.add("Trend / Structure: " + percent(Math.abs(trend)));
            out.checks.add("Momentum / RSI: " + percent(Math.abs(momentum)));
            out.checks.add("Candle Pattern: " + percent(Math.abs(pattern)));
            out.checks.add("Support / Resistance: " + percent(Math.abs(level)));
            out.checks.add("Volatility Check: " + percent(Math.abs(level)));
            out.checks.add("1000 logic probes evaluated");
            out.checks.add("Candle order: oldest to newest.");
            out.checks.add("No artificial candle fallback was used.");
            return out;
        } finally {
            if (madeCopy && work != null && !work.isRecycled()) work.recycle();
        }
    }

    private static List<Candle> extractCandles(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        // Portrait trading screenshots: ignore status/navigation and lower trade controls.
        int top = Math.max(0, Math.round(h * 0.055f));
        int bottom = Math.min(h - 1, Math.round(h * 0.755f));
        int left = Math.round(w * 0.025f);
        int right = Math.round(w * 0.975f);

        // Same-color erosion makes thin indicator lines much less likely to become candles.
        boolean[][] green = new boolean[w][bottom - top + 1];
        boolean[][] red = new boolean[w][bottom - top + 1];

        for (int x = left; x <= right; x++) {
            for (int y = top; y <= bottom; y++) {
                int c = bmp.getPixel(x, y);
                if (isGreen(c)) green[x][y - top] = true;
                if (isRed(c)) red[x][y - top] = true;
            }
        }

        boolean[][] mask = new boolean[w][bottom - top + 1];
        for (int x = left + 1; x < right; x++) {
            for (int yy = 1; yy < mask[0].length - 1; yy++) {
                boolean g = green[x][yy] && green[x - 1][yy] && green[x + 1][yy]
                        && green[x][yy - 1] && green[x][yy + 1];
                boolean r = red[x][yy] && red[x - 1][yy] && red[x + 1][yy]
                        && red[x][yy - 1] && red[x][yy + 1];
                mask[x][yy] = g || r;
            }
        }

        List<Component> components = components(mask, left, right);
        List<Candle> raw = new ArrayList<>();

        for (Component c : components) {
            int cw = c.maxX - c.minX + 1;
            int ch = c.maxY - c.minY + 1;
            if (cw < 3 || cw > Math.max(40, w / 10)) continue;
            if (ch < 6 || ch > Math.round((bottom - top) * 0.55f)) continue;
            if (c.area < 10) continue;

            int cx = Math.round(c.cx);
            int y0 = c.minY + top;
            int y1 = c.maxY + top;

            // Count original green/red pixels around the component to classify body direction.
            int greenCount = 0, redCount = 0;
            int padX = Math.max(1, cw / 2);
            for (int x = Math.max(left, cx - padX); x <= Math.min(right, cx + padX); x++) {
                for (int y = Math.max(top, y0 - 3); y <= Math.min(bottom, y1 + 3); y++) {
                    int px = bmp.getPixel(x, y);
                    if (isGreen(px)) greenCount++;
                    if (isRed(px)) redCount++;
                }
            }
            boolean isG = greenCount >= redCount;
            float bodyTop = y0;
            float bodyBottom = y1;

            // Screen Y grows downward, therefore green/open is at body bottom and close at body top.
            Candle candle = new Candle();
            candle.x = cx;
            if (isG) {
                candle.open = -bodyBottom;
                candle.close = -bodyTop;
            } else {
                candle.open = -bodyTop;
                candle.close = -bodyBottom;
            }

            // Wick estimate from same-direction pixels around the candle center.
            int wickTop = y0;
            int wickBottom = y1;
            int wx0 = Math.max(left, cx - Math.max(1, cw / 3));
            int wx1 = Math.min(right, cx + Math.max(1, cw / 3));
            for (int x = wx0; x <= wx1; x++) {
                for (int y = Math.max(top, y0 - Math.round(ch * 0.9f));
                     y <= Math.min(bottom, y1 + Math.round(ch * 0.9f)); y++) {
                    int px = bmp.getPixel(x, y);
                    if ((isG && isGreen(px)) || (!isG && isRed(px))) {
                        wickTop = Math.min(wickTop, y);
                        wickBottom = Math.max(wickBottom, y);
                    }
                }
            }

            candle.high = -wickTop;
            candle.low = -wickBottom;
            candle.green = isG;
            raw.add(candle);
        }

        Collections.sort(raw, Comparator.comparingDouble(a -> a.x));
        return dedupe(raw);
    }

    private static List<Component> components(boolean[][] mask, int left, int right) {
        int w = mask.length;
        int h = mask[0].length;
        boolean[][] seen = new boolean[w][h];
        List<Component> result = new ArrayList<>();
        int[] qx = new int[Math.max(64, w * 2)];
        int[] qy = new int[Math.max(64, w * 2)];

        for (int sx = left; sx <= right; sx++) {
            for (int sy = 0; sy < h; sy++) {
                if (!mask[sx][sy] || seen[sx][sy]) continue;
                int head = 0, tail = 0;
                if (tail >= qx.length) continue;
                qx[tail] = sx; qy[tail++] = sy; seen[sx][sy] = true;

                Component c = new Component();
                c.minX = c.maxX = sx;
                c.minY = c.maxY = sy;
                c.area = 0;
                long sumX = 0;

                while (head < tail) {
                    int x = qx[head], y = qy[head++];
                    c.area++;
                    sumX += x;
                    c.minX = Math.min(c.minX, x);
                    c.maxX = Math.max(c.maxX, x);
                    c.minY = Math.min(c.minY, y);
                    c.maxY = Math.max(c.maxY, y);

                    int[] dx = {1,-1,0,0};
                    int[] dy = {0,0,1,-1};
                    for (int k = 0; k < 4; k++) {
                        int nx = x + dx[k], ny = y + dy[k];
                        if (nx < left || nx > right || ny < 0 || ny >= h) continue;
                        if (!mask[nx][ny] || seen[nx][ny]) continue;
                        if (tail >= qx.length) {
                            int n = qx.length * 2;
                            int[] nxq = new int[n], nyq = new int[n];
                            System.arraycopy(qx, 0, nxq, 0, qx.length);
                            System.arraycopy(qy, 0, nyq, 0, qy.length);
                            qx = nxq; qy = nyq;
                        }
                        qx[tail] = nx; qy[tail++] = ny;
                        seen[nx][ny] = true;
                    }
                }
                if (c.area >= 10) {
                    c.cx = sumX / (float)c.area;
                    result.add(c);
                }
            }
        }
        return result;
    }

    private static List<Candle> dedupe(List<Candle> in) {
        if (in.isEmpty()) return in;
        List<Candle> out = new ArrayList<>();
        float typical = 8f;
        for (int i = 1; i < in.size(); i++) {
            typical += Math.abs(in.get(i).x - in.get(i - 1).x);
        }
        typical = typical / Math.max(1, in.size() - 1);
        float minGap = Math.max(3f, typical * 0.38f);

        for (Candle c : in) {
            if (out.isEmpty()) {
                out.add(c);
                continue;
            }
            Candle last = out.get(out.size() - 1);
            if (Math.abs(c.x - last.x) < minGap) {
                // Keep the larger body and extend the real wick bounds.
                if (c.body() > last.body()) {
                    c.high = Math.max(c.high, last.high);
                    c.low = Math.min(c.low, last.low);
                    out.set(out.size() - 1, c);
                } else {
                    last.high = Math.max(last.high, c.high);
                    last.low = Math.min(last.low, c.low);
                }
            } else {
                out.add(c);
            }
        }
        return out;
    }

    private static double chartQuality(List<Candle> c) {
        if (c.isEmpty()) return 0;
        double count = Math.min(1.0, c.size() / 35.0);
        double bodies = 0;
        double spacing = 0;
        for (int i = 0; i < c.size(); i++) {
            bodies += Math.min(1.0, c.get(i).bodyRatio() / 0.65);
            if (i > 0) spacing += 1.0 / (1.0 + Math.abs((c.get(i).x - c.get(i-1).x) - medianGap(c)));
        }
        double bodyScore = bodies / c.size();
        double spacingScore = c.size() > 1 ? spacing / (c.size() - 1) : 0;
        return clamp(100.0 * (0.50 * count + 0.30 * bodyScore + 0.20 * spacingScore), 0, 100);
    }

    private static double medianGap(List<Candle> c) {
        if (c.size() < 2) return 1;
        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < c.size(); i++) gaps.add(c.get(i).x - c.get(i-1).x);
        Collections.sort(gaps);
        return gaps.get(gaps.size()/2);
    }

    private static double slope(List<Candle> c, int window) {
        int n = Math.min(window, c.size());
        if (n < 2) return 0;
        double sx=0, sy=0, sxx=0, sxy=0;
        int start = c.size()-n;
        for (int i=0;i<n;i++) {
            double x=i, y=c.get(start+i).close;
            sx+=x; sy+=y; sxx+=x*x; sxy+=x*y;
        }
        double den=n*sxx-sx*sx;
        return den==0?0:(n*sxy-sx*sy)/den;
    }

    private static double normalizedRecentMove(List<Candle> c, int period) {
        if (c.size() < 2) return 0;
        int p = Math.min(period, c.size()-1);
        double now = c.get(c.size()-1).close;
        double old = c.get(c.size()-1-p).close;
        double range = 0;
        for (int i=Math.max(0,c.size()-p-1); i<c.size();i++) range += c.get(i).range();
        range /= Math.max(1,p+1);
        return clamp((now-old)/(range*2.0), -1, 1);
    }

    private static double roc(List<Candle> c, int period) {
        if (c.size() <= period) return 0;
        double old=c.get(c.size()-1-period).close;
        return old==0?0:(c.get(c.size()-1).close-old)/Math.abs(old);
    }

    private static double rsi(List<Candle> c, int period) {
        if (c.size() <= period) return 50;
        double gain=0, loss=0;
        int start=c.size()-period;
        for (int i=start+1;i<c.size();i++) {
            double d=c.get(i).close-c.get(i-1).close;
            if (d>0) gain+=d; else loss-=d;
        }
        if (loss==0) return gain>0?100:50;
        double rs=(gain/period)/(loss/period);
        return 100-(100/(1+rs));
    }

    private static double trendScore(List<Candle> c) {
        double s = slope(c, Math.min(15,c.size()));
        return Math.tanh(s*7.0);
    }

    private static double momentumScore(List<Candle> c) {
        double a=roc(c, Math.min(5,c.size()-1));
        double r=(rsi(c, Math.min(14,c.size()-1))-50)/50.0;
        return clamp(0.55*Math.tanh(a*18)+0.45*r,-1,1);
    }

    private static double candleGeometryScore(List<Candle> c, int offset) {
        int idx=c.size()-1-Math.min(offset,c.size()-1);
        Candle x=c.get(idx);
        double v=x.green?0.35:-0.35;
        double body=x.bodyRatio();
        double wickBias=(x.lowerWick()-x.upperWick())/x.range();
        v += 0.35*wickBias;
        if (body>0.65) v += x.green?0.25:-0.25;
        if (body<0.15) v *= 0.35;
        return clamp(v,-1,1);
    }

    private static double patternScore(List<Candle> c) {
        if (c.size()<3) return 0;
        Candle a=c.get(c.size()-3), b=c.get(c.size()-2), x=c.get(c.size()-1);
        double v=0;
        if (a.green && b.green && x.green) v+=0.65;
        if (!a.green && !b.green && !x.green) v-=0.65;
        if (b.green && !x.green && x.body()>b.body()*1.15) v-=0.45;
        if (!b.green && x.green && x.body()>b.body()*1.15) v+=0.45;
        if (x.lowerWick()>x.body()*1.8 && x.bodyRatio()<0.45) v+=0.30;
        if (x.upperWick()>x.body()*1.8 && x.bodyRatio()<0.45) v-=0.30;
        return clamp(v,-1,1);
    }

    private static double levelAndVolatilityScore(List<Candle> c, int period) {
        int n=Math.min(period,c.size());
        double hi=-Double.MAX_VALUE, lo=Double.MAX_VALUE;
        for(int i=c.size()-n;i<c.size();i++){
            hi=Math.max(hi,c.get(i).high);
            lo=Math.min(lo,c.get(i).low);
        }
        Candle x=c.get(c.size()-1);
        double range=Math.max(0.001,hi-lo);
        double pos=(x.close-lo)/range;
        double v=0;
        if(pos>0.78) v-=0.18;
        else if(pos<0.22) v+=0.18;
        double avg=0;
        for(int i=c.size()-n;i<c.size();i++) avg+=c.get(i).range();
        avg/=n;
        if(x.range()>avg*1.35) v += x.green?0.22:-0.22;
        return clamp(v,-1,1);
    }

    private static void fillPrediction(Result out, List<Candle> c, double signed) {
        Candle last=c.get(c.size()-1);
        double trend=Math.abs(trendScore(c));
        double momentum=Math.abs(momentumScore(c));
        double body=Math.max(0.12, Math.min(0.88,
                0.38*last.bodyRatio()+0.28*trend+0.22*momentum+0.12));
        double avgRange=0;
        int n=Math.min(12,c.size());
        for(int i=c.size()-n;i<c.size();i++) avgRange+=c.get(i).range();
        avgRange/=n;
        double rangeRatio=clamp(0.80+0.55*(0.5*trend+0.5*momentum),0.65,1.45);

        out.nextCandleColor = signed >= 0 ? "GREEN / UP" : "RED / DOWN";
        out.nextBodyRatio = body*100.0;
        out.nextRangeRatio = rangeRatio;
        if (rangeRatio < 0.88) out.nextCandleSize="SMALL";
        else if (rangeRatio < 1.15) out.nextCandleSize="MEDIUM";
        else out.nextCandleSize="LARGE";

        double wick=1.0-body;
        out.nextUpperWickRatio=clamp((wick*0.40+0.06)*100,4,30);
        out.nextLowerWickRatio=clamp((wick*0.45+0.06)*100,4,30);
    }

    private static boolean isGreen(int c) {
        int r=Color.red(c), g=Color.green(c), b=Color.blue(c);
        return g>125 && g>r*1.18f && g>b*1.05f && (g-r)>22;
    }

    private static boolean isRed(int c) {
        int r=Color.red(c), g=Color.green(c), b=Color.blue(c);
        return r>135 && r>g*1.25f && r>b*1.10f && (r-g)>30;
    }

    private static String percent(double x) {
        return String.format(java.util.Locale.US, "%.0f%%", Math.min(100, Math.abs(x)*100));
    }

    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    private static float clamp(float x,float a,float b){return Math.max(a,Math.min(b,x));}
}
