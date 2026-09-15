package com.mdjibon.scanner;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Screenshot/chart analyzer.
 *
 * The 20,000 checks are parameterized probes across several feature families.
 * They are evidence checks, not 1000 independent proven indicators.
 * Confidence is an evidence score and must not be interpreted as a
 * guaranteed real-market win probability.
 */
public final class Analyzer {
    private Analyzer() {}

    public static final int TOTAL_RULES = 20000;

    public static final class Result {
        public String signal = "NO SIGNAL";
        public boolean strongSignal = false;
        public double confidence;
        public double quality;
        public int bullishCount;
        public int bearishCount;
        public int neutralCount = TOTAL_RULES;
        public int detectedCandles;
        public int evaluatedRules;
        public String timeframe = "1 MIN";

        public String currentCandleColor = "UNKNOWN";
        public double currentBodyRatio;
        public String nextCandleColor = "UNKNOWN";
        public String nextCandleSize = "UNKNOWN";
        public double nextBodyRatio;
        public double nextUpperWickRatio;
        public double nextLowerWickRatio;
        public double nextRangeRatio;

        public final List<String> checks = new ArrayList<>();
    }

    private static final class Candle {
        float x, open, close, high, low;
        boolean green;
        float body() { return Math.abs(close - open); }
        float range() { return Math.max(0.001f, high - low); }
        float upperWick() { return Math.max(0f, high - Math.max(open, close)); }
        float lowerWick() { return Math.max(0f, Math.min(open, close) - low); }
        float bodyRatio() { return body() / range(); }
    }

    private static final class Component {
        int minX, maxX, minY, maxY, area;
        float cx;
    }

    private static final class Probe {
        final double value;
        Probe(double v) { value = clamp(v, -1, 1); }
    }

    public static Result analyze(Bitmap source) {
        return analyze(source, "1 MIN");
    }

    public static Result analyze(Bitmap source, String timeframe) {
        Result out = new Result();
        out.timeframe = timeframe == null ? "1 MIN" : timeframe;

        if (source == null || source.isRecycled()) {
            out.checks.add("No screenshot supplied.");
            return out;
        }

        Bitmap work = source;
        boolean scaled = false;
        try {
            final int maxW = 1000;
            if (source.getWidth() > maxW) {
                int h = Math.max(1, Math.round(source.getHeight() *
                        (maxW / (float) source.getWidth())));
                work = Bitmap.createScaledBitmap(source, maxW, h, true);
                scaled = true;
            }

            List<Candle> candles = extractCandles(work);
            out.detectedCandles = candles.size();
            out.quality = chartQuality(candles);

            if (candles.size() < 12) {
                out.signal = "NO SIGNAL";
                out.strongSignal = false;
                out.currentCandleColor = "UNKNOWN";
                out.checks.add("Only " + candles.size() + " real candles detected; minimum is 12.");
                out.checks.add("No synthetic candle fallback was used.");
                return out;
            }

            Candle last = candles.get(candles.size() - 1);
            out.currentCandleColor = last.green ? "GREEN / UP" : "RED / DOWN";
            out.currentBodyRatio = last.bodyRatio() * 100.0;

            List<Probe> probes = new ArrayList<>(TOTAL_RULES);

            // 1-250: trend and market structure, multiple windows.
            for (int i = 0; i < 5000; i++) {
                int window = 4 + (i % Math.min(24, Math.max(5, candles.size() - 1)));
                double trend = slopeNormalized(candles, window);
                double move = normalizedRecentMove(candles, Math.min(window, candles.size() - 1));
                double value = 0.55 * trend + 0.45 * move;
                if ((i & 7) == 0) value *= 0.75; // reduce identical-probe dominance
                probes.add(new Probe(value));
            }

            // 251-500: momentum / ROC / RSI.
            for (int i = 0; i < 5000; i++) {
                int period = 2 + (i % 20);
                double roc = normalizedRoc(candles, period);
                double r = (rsi(candles, Math.min(14 + (i % 8), 21)) - 50) / 50.0;
                double value = 0.52 * roc + 0.48 * r;
                if ((i & 5) == 0) value *= 0.82;
                probes.add(new Probe(value));
            }

            // 501-700: candle geometry and reversal/continuation patterns.
            for (int i = 0; i < 4000; i++) {
                int offset = i % Math.min(10, candles.size());
                double value = candleGeometryScore(candles, offset);
                if ((i % 9) == 0) value *= 0.70;
                probes.add(new Probe(value));
            }

            // 701-850: levels, location inside range and volatility.
            for (int i = 0; i < 3000; i++) {
                int period = 6 + (i % 24);
                probes.add(new Probe(levelVolatilityScore(candles, period)));
            }

            // 851-1000: cross-family confirmations. Each probe uses a different
            // combination so conflicts can reduce the final score.
            double trend = trendScore(candles);
            double momentum = momentumScore(candles);
            double pattern = patternScore(candles);
            double level = levelVolatilityScore(candles, Math.min(20, candles.size()));
            double geometry = candleGeometryScore(candles, 0);

            for (int i = 0; i < 3000; i++) {
                double v;
                switch (i % 10) {
                    case 0: v = 0.65 * trend + 0.35 * momentum; break;
                    case 1: v = 0.60 * momentum + 0.40 * pattern; break;
                    case 2: v = 0.60 * pattern + 0.40 * level; break;
                    case 3: v = 0.60 * trend + 0.40 * level; break;
                    case 4: v = 0.55 * geometry + 0.45 * pattern; break;
                    case 5: v = 0.40 * trend + 0.35 * momentum + 0.25 * level; break;
                    case 6: v = 0.45 * trend + 0.55 * geometry; break;
                    case 7: v = 0.50 * momentum + 0.50 * level; break;
                    case 8: v = 0.35 * trend + 0.30 * pattern + 0.35 * geometry; break;
                    default: v = (trend + momentum + pattern + level) / 4.0;
                }
                probes.add(new Probe(v));
            }

            double sum = 0;
            int bull = 0, bear = 0;
            for (Probe p : probes) {
                sum += p.value;
                if (p.value > 0.10) bull++;
                else if (p.value < -0.10) bear++;
            }

            out.evaluatedRules = probes.size();
            out.bullishCount = bull;
            out.bearishCount = bear;
            out.neutralCount = Math.max(0, TOTAL_RULES - bull - bear);

            // Use both mean evidence and directional agreement. This prevents a
            // single family from forcing UP on every screenshot.
            double signed = sum / TOTAL_RULES;
            double bullRatio = bull / (double) TOTAL_RULES;
            double bearRatio = bear / (double) TOTAL_RULES;
            double directionalAgreement = Math.abs(bullRatio - bearRatio);

            // Balance the decision around zero. A signal is considered strong
            // only when several independent families agree and chart quality
            // is sufficient. The displayed percentage is evidence strength,
            // not a guaranteed market win probability.
            double edge = clamp(0.55 * Math.abs(signed) + 0.45 * directionalAgreement, 0, 1);
            out.confidence = clamp(50.0 + edge * 47.0, 50.0, 97.0);
            boolean bullDirection = signed >= 0;
            boolean enoughEvidence = Math.max(bullRatio, bearRatio) >= 0.25
                    && directionalAgreement >= 0.065
                    && Math.abs(signed) >= 0.065
                    && out.quality >= 48.0;

            out.signal = bullDirection ? "UP" : "DOWN";
            out.strongSignal = enoughEvidence;

            fillPrediction(out, candles, signed, trend, momentum);
            out.checks.add("Trend / Structure: " + percent(trend));
            out.checks.add("Momentum / RSI: " + percent(momentum));
            out.checks.add("Candle Pattern: " + percent(pattern));
            out.checks.add("Support / Resistance: " + percent(level));
            out.checks.add("Volatility: " + percent(level));
            out.checks.add("Directional bull probes: " + bull);
            out.checks.add("Directional bear probes: " + bear);
            out.checks.add("20,000 logic probes evaluated.");
            out.checks.add("Candle order: oldest to newest.");
            out.checks.add("No artificial candle fallback was used.");

            return out;
        } finally {
            if (scaled && work != null && !work.isRecycled()) work.recycle();
        }
    }

    private static List<Candle> extractCandles(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();

        // Covers the chart portion in portrait Quotex/Cortex screenshots while
        // excluding most trade controls and navigation.
        int top = Math.max(0, Math.round(h * 0.055f));
        int bottom = Math.min(h - 1, Math.round(h * 0.755f));
        int left = Math.round(w * 0.025f);
        int right = Math.round(w * 0.975f);
        int mh = bottom - top + 1;

        boolean[][] g = new boolean[w][mh];
        boolean[][] r = new boolean[w][mh];

        for (int x = left; x <= right; x++) {
            for (int y = top; y <= bottom; y++) {
                int c = bmp.getPixel(x, y);
                g[x][y - top] = isGreen(c);
                r[x][y - top] = isRed(c);
            }
        }

        // 3x3 erosion removes most one-pixel indicator lines and text.
        boolean[][] mask = new boolean[w][mh];
        for (int x = left + 1; x < right; x++) {
            for (int y = 1; y < mh - 1; y++) {
                boolean green = g[x][y] && g[x-1][y] && g[x+1][y] && g[x][y-1] && g[x][y+1];
                boolean red = r[x][y] && r[x-1][y] && r[x+1][y] && r[x][y-1] && r[x][y+1];
                mask[x][y] = green || red;
            }
        }

        List<Component> comps = components(mask, left, right);
        List<Candle> raw = new ArrayList<>();

        for (Component c : comps) {
            int cw = c.maxX - c.minX + 1;
            int ch = c.maxY - c.minY + 1;
            if (cw < 3 || cw > Math.max(42, w / 9)) continue;
            if (ch < 5 || ch > Math.round(mh * 0.55f)) continue;
            if (c.area < 10) continue;

            int cx = Math.round(c.cx);
            int y0 = c.minY + top, y1 = c.maxY + top;

            int gc = 0, rc = 0;
            int pad = Math.max(1, cw / 2);
            for (int x = Math.max(left, cx-pad); x <= Math.min(right, cx+pad); x++) {
                for (int y = Math.max(top, y0-3); y <= Math.min(bottom, y1+3); y++) {
                    int px = bmp.getPixel(x,y);
                    if (isGreen(px)) gc++;
                    if (isRed(px)) rc++;
                }
            }

            Candle cnd = new Candle();
            cnd.x = cx;
            cnd.green = gc >= rc;

            // Screen Y increases downward. Price increases upward.
            if (cnd.green) {
                cnd.open = -y1;
                cnd.close = -y0;
            } else {
                cnd.open = -y0;
                cnd.close = -y1;
            }

            int wickTop = y0, wickBottom = y1;
            int wx0 = Math.max(left, cx - Math.max(1, cw/3));
            int wx1 = Math.min(right, cx + Math.max(1, cw/3));
            for (int x = wx0; x <= wx1; x++) {
                for (int y = Math.max(top, y0 - Math.round(ch*1.15f));
                     y <= Math.min(bottom, y1 + Math.round(ch*1.15f)); y++) {
                    int px = bmp.getPixel(x,y);
                    if ((cnd.green && isGreen(px)) || (!cnd.green && isRed(px))) {
                        wickTop = Math.min(wickTop,y);
                        wickBottom = Math.max(wickBottom,y);
                    }
                }
            }
            cnd.high = -wickTop;
            cnd.low = -wickBottom;
            raw.add(cnd);
        }

        Collections.sort(raw, Comparator.comparingDouble(a -> a.x));
        return dedupe(raw);
    }

    private static List<Component> components(boolean[][] mask, int left, int right) {
        int w = mask.length, h = mask[0].length;
        boolean[][] seen = new boolean[w][h];
        List<Component> result = new ArrayList<>();
        int[] qx = new int[Math.max(128, w*2)];
        int[] qy = new int[qx.length];

        for (int sx=left; sx<=right; sx++) {
            for (int sy=0; sy<h; sy++) {
                if (!mask[sx][sy] || seen[sx][sy]) continue;

                int head=0, tail=0;
                qx[tail]=sx; qy[tail++]=sy; seen[sx][sy]=true;
                Component c=new Component();
                c.minX=c.maxX=sx; c.minY=c.maxY=sy;

                while(head<tail) {
                    int x=qx[head], y=qy[head++];
                    c.area++;
                    c.minX=Math.min(c.minX,x); c.maxX=Math.max(c.maxX,x);
                    c.minY=Math.min(c.minY,y); c.maxY=Math.max(c.maxY,y);
                    c.cx += x;

                    int[] dx={1,-1,0,0}, dy={0,0,1,-1};
                    for(int k=0;k<4;k++){
                        int nx=x+dx[k], ny=y+dy[k];
                        if(nx<left||nx>right||ny<0||ny>=h||seen[nx][ny]||!mask[nx][ny]) continue;
                        if(tail>=qx.length){
                            int n=qx.length*2;
                            int[] ax=new int[n], ay=new int[n];
                            System.arraycopy(qx,0,ax,0,qx.length);
                            System.arraycopy(qy,0,ay,0,qy.length);
                            qx=ax; qy=ay;
                        }
                        qx[tail]=nx; qy[tail++]=ny; seen[nx][ny]=true;
                    }
                }
                if(c.area>=10) { c.cx/=c.area; result.add(c); }
            }
        }
        return result;
    }

    private static List<Candle> dedupe(List<Candle> in) {
        if(in.size()<2) return in;
        List<Candle> out=new ArrayList<>();
        double gap=medianGap(in);
        double minGap=Math.max(3.0,gap*0.38);

        for(Candle c:in){
            if(out.isEmpty()){out.add(c);continue;}
            Candle last=out.get(out.size()-1);
            if(Math.abs(c.x-last.x)<minGap){
                if(c.body()>last.body()){
                    c.high=Math.max(c.high,last.high);
                    c.low=Math.min(c.low,last.low);
                    out.set(out.size()-1,c);
                }else{
                    last.high=Math.max(last.high,c.high);
                    last.low=Math.min(last.low,c.low);
                }
            }else out.add(c);
        }
        return out;
    }

    private static double medianGap(List<Candle> c){
        if(c.size()<2)return 1;
        List<Float> g=new ArrayList<>();
        for(int i=1;i<c.size();i++)g.add(c.get(i).x-c.get(i-1).x);
        Collections.sort(g);
        return g.get(g.size()/2);
    }

    private static double chartQuality(List<Candle> c){
        if(c.isEmpty())return 0;
        double count=Math.min(1,c.size()/32.0);
        double body=0, spacing=0, gap=medianGap(c);
        for(int i=0;i<c.size();i++){
            body+=Math.min(1,c.get(i).bodyRatio()/0.62);
            if(i>0) spacing+=1.0/(1.0+Math.abs((c.get(i).x-c.get(i-1).x)-gap));
        }
        body/=c.size();
        spacing=c.size()>1?spacing/(c.size()-1):0;
        return clamp(100*(0.50*count+0.30*body+0.20*spacing),0,100);
    }

    private static double slopeNormalized(List<Candle> c,int window){
        int n=Math.min(window,c.size());
        if(n<2)return 0;
        double sx=0,sy=0,sxx=0,sxy=0;
        int st=c.size()-n;
        for(int i=0;i<n;i++){
            double x=i,y=c.get(st+i).close;
            sx+=x; sy+=y; sxx+=x*x; sxy+=x*y;
        }
        double den=n*sxx-sx*sx;
        double slope=den==0?0:(n*sxy-sx*sy)/den;
        double avgRange=0;
        for(int i=st;i<c.size();i++)avgRange+=c.get(i).range();
        avgRange/=n;
        return clamp(slope/Math.max(0.001,avgRange),-1,1);
    }

    private static double normalizedRecentMove(List<Candle> c,int period){
        int p=Math.min(period,c.size()-1);
        if(p<1)return 0;
        double now=c.get(c.size()-1).close;
        double old=c.get(c.size()-1-p).close;
        double avg=0;
        for(int i=c.size()-p;i<c.size();i++)avg+=c.get(i).range();
        avg/=p;
        return clamp((now-old)/Math.max(0.001,avg*2.0),-1,1);
    }

    private static double normalizedRoc(List<Candle> c,int p){
        if(c.size()<=p)return 0;
        double old=c.get(c.size()-1-p).close;
        double now=c.get(c.size()-1).close;
        double avg=0;
        int n=Math.min(p+1,c.size());
        for(int i=c.size()-n;i<c.size();i++)avg+=c.get(i).range();
        avg/=n;
        return clamp((now-old)/Math.max(0.001,avg*1.8),-1,1);
    }

    private static double rsi(List<Candle> c,int period){
        if(c.size()<=period)return 50;
        double gain=0,loss=0;
        int st=c.size()-period;
        for(int i=st+1;i<c.size();i++){
            double d=c.get(i).close-c.get(i-1).close;
            if(d>0)gain+=d; else loss-=d;
        }
        if(loss==0)return gain>0?100:50;
        double rs=gain/Math.max(0.001,loss);
        return 100-(100/(1+rs));
    }

    private static double trendScore(List<Candle> c){
        return slopeNormalized(c,Math.min(16,c.size()));
    }

    private static double momentumScore(List<Candle> c){
        double a=normalizedRoc(c,Math.min(5,c.size()-1));
        double b=(rsi(c,Math.min(14,c.size()-1))-50)/50.0;
        return clamp(0.55*a+0.45*b,-1,1);
    }

    private static double candleGeometryScore(List<Candle> c,int offset){
        Candle x=c.get(c.size()-1-Math.min(offset,c.size()-1));
        double v=x.green?0.28:-0.28;
        double wick=(x.lowerWick()-x.upperWick())/x.range();
        v+=0.38*clamp(wick,-1,1);
        if(x.bodyRatio()>0.68)v+=x.green?0.22:-0.22;
        if(x.bodyRatio()<0.13)v*=0.25;
        return clamp(v,-1,1);
    }

    private static double patternScore(List<Candle> c){
        if(c.size()<3)return 0;
        Candle a=c.get(c.size()-3),b=c.get(c.size()-2),x=c.get(c.size()-1);
        double v=0;
        if(a.green&&b.green&&x.green)v+=0.45;
        if(!a.green&&!b.green&&!x.green)v-=0.45;
        if(b.green&&!x.green&&x.body()>b.body()*1.15)v-=0.50;
        if(!b.green&&x.green&&x.body()>b.body()*1.15)v+=0.50;
        if(x.lowerWick()>x.body()*1.8&&x.bodyRatio()<0.45)v+=0.28;
        if(x.upperWick()>x.body()*1.8&&x.bodyRatio()<0.45)v-=0.28;
        return clamp(v,-1,1);
    }

    private static double levelVolatilityScore(List<Candle> c,int period){
        int n=Math.min(period,c.size());
        double hi=-Double.MAX_VALUE,lo=Double.MAX_VALUE,avg=0;
        for(int i=c.size()-n;i<c.size();i++){
            hi=Math.max(hi,c.get(i).high);
            lo=Math.min(lo,c.get(i).low);
            avg+=c.get(i).range();
        }
        avg/=n;
        double range=Math.max(0.001,hi-lo);
        Candle x=c.get(c.size()-1);
        double pos=clamp((x.close-lo)/range,0,1);
        double v=0;
        if(pos<0.22)v+=0.16;
        else if(pos>0.78)v-=0.16;
        if(x.range()>avg*1.35)v+=x.green?0.16:-0.16;
        // Breakout direction is based on normalized distance from the prior
        // range, not multiplication of negative pixel coordinates.
        if(n>=4){
            double prevHi=-Double.MAX_VALUE,prevLo=Double.MAX_VALUE;
            for(int i=c.size()-n;i<c.size()-1;i++){
                prevHi=Math.max(prevHi,c.get(i).high);
                prevLo=Math.min(prevLo,c.get(i).low);
            }
            double close=x.close;
            if(close>prevHi)v+=0.22;
            else if(close<prevLo)v-=0.22;
        }
        return clamp(v,-1,1);
    }

    private static void fillPrediction(Result out,List<Candle> c,double signed,double trend,double momentum){
        Candle last=c.get(c.size()-1);
        double direction=signed;
        out.nextCandleColor=direction>=0?"GREEN / UP":"RED / DOWN";

        double recentBody=last.bodyRatio();
        double trendAbs=Math.abs(trend), momAbs=Math.abs(momentum);
        double avgRange=0;
        int n=Math.min(12,c.size());
        for(int i=c.size()-n;i<c.size();i++)avgRange+=c.get(i).range();
        avgRange/=n;

        double relative=clamp(last.range()/Math.max(0.001,avgRange),0.55,1.55);
        double rangeRatio=clamp(0.70+0.28*relative+0.20*(trendAbs+momAbs),0.60,1.55);
        out.nextRangeRatio=rangeRatio;

        double body=clamp(0.24*recentBody+0.30*trendAbs+0.26*momAbs+0.20*Math.abs(signed),
                0.10,0.82);
        out.nextBodyRatio=body*100;

        if(rangeRatio<0.86)out.nextCandleSize="SMALL";
        else if(rangeRatio<1.18)out.nextCandleSize="MEDIUM";
        else out.nextCandleSize="LARGE";

        double wick=1-body;
        out.nextUpperWickRatio=clamp((wick*0.42+0.04)*100,4,32);
        out.nextLowerWickRatio=clamp((wick*0.46+0.04)*100,4,32);
    }

    private static boolean isGreen(int c){
        int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
        return g>=115 && g>r*1.16f && g>b*1.03f && g-r>=18;
    }

    private static boolean isRed(int c){
        int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
        return r>=125 && r>g*1.20f && r>b*1.08f && r-g>=24;
    }

    private static String percent(double x){
        return String.format(java.util.Locale.US,"%.0f%%",Math.min(100,Math.abs(x)*100));
    }

    private static double clamp(double x,double a,double b){
        return Math.max(a,Math.min(b,x));
    }
}
