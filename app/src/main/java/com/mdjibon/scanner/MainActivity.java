package com.mdjibon.scanner;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PICK_IMAGE = 5001;
    private static final int REQUEST_CAPTURE = 5002;

    private ImageView preview;
    private TextView status, resultText, countdownText;
    private Button uploadButton, analyzeButton;
    private Spinner marketSpinner, timeframeSpinner;
    private Switch floatingSwitch, autoScanSwitch;
    private Bitmap selectedBitmap;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CountDownTimer timer;

    private static final String[] MARKETS = {
        "EUR/AUD (OTC)","GBP/JPY (OTC)","AUD/CHF (OTC)","CAD/JPY (OTC)",
        "NZD/JPY (OTC)","USD/COP (OTC)","USD/EGP (OTC)","USD/PHP (OTC)",
        "EUR/JPY (OTC)","USD/CHF (OTC)","EUR/NZD (OTC)","USD/MXN (OTC)",
        "CHF/JPY (OTC)","USD/CAD (OTC)","EUR/GBP (OTC)","NZD/USD (OTC)",
        "AUD/CAD (OTC)","GBP/AUD (OTC)","NZD/CHF (OTC)","AUD/JPY (OTC)",
        "USD/IDR (OTC)","USD/PKR (OTC)","GBP/CAD (OTC)","GBP/CHF (OTC)",
        "USD/INR (OTC)","AUD/USD (OTC)","USD/BRL (OTC)","CAD/CHF (OTC)",
        "EUR/CAD (OTC)","EUR/CHF (OTC)","EUR/USD (OTC)","USD/ARS (OTC)",
        "USD/BDT (OTC)","USD/DZD (OTC)","USD/JPY (OTC)","GBP/NZD (OTC)",
        "NZD/CAD (OTC)","AUD/NZD (OTC)","USD/ZAR (OTC)","GBP/USD (OTC)"
    };

    private static final String[] TIMEFRAMES = {"1 MIN","5 MIN","15 SEC","30 SEC","1 MIN"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(5,10,20));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(3,8,15));
        buildUi();
        startCountdown(60);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(12),dp(18),dp(18));
        root.setBackgroundColor(android.graphics.Color.rgb(5,10,20));
        scroll.addView(root);

        TextView title = text("MD JIBON 99%", 30, true);
        title.setTextColor(android.graphics.Color.WHITE);
        root.addView(title);

        TextView sub = text("ACCURACY SIGNAL AI", 14, false);
        sub.setTextColor(android.graphics.Color.LTGRAY);
        root.addView(sub);

        LinearLayout marketRow = new LinearLayout(this);
        marketSpinner = spinner(MARKETS);
        timeframeSpinner = spinner(TIMEFRAMES);
        marketRow.addView(marketSpinner, new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(120),dp(50));
        tp.leftMargin=dp(8); marketRow.addView(timeframeSpinner,tp);
        root.addView(marketRow);

        countdownText = text("NEXT CANDLE IN 00:59", 22, true);
        countdownText.setGravity(Gravity.CENTER);
        countdownText.setTextColor(android.graphics.Color.YELLOW);
        root.addView(countdownText);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(android.graphics.Color.rgb(10,17,29));
        root.addView(preview,new LinearLayout.LayoutParams(-1,dp(330)));

        uploadButton = button("UPLOAD PHOTO  â€¢  OPEN GALLERY", android.graphics.Color.rgb(25,130,240));
        uploadButton.setOnClickListener(v -> pickImage());
        root.addView(uploadButton);

        analyzeButton = button("ANALYZE MARKET  â€¢  1000 CHECKS", android.graphics.Color.rgb(0,195,100));
        analyzeButton.setOnClickListener(v -> analyzeSelected());
        root.addView(analyzeButton);

        TextView or = text("FLOATING SCANNER",18,true);
        or.setGravity(Gravity.CENTER);
        root.addView(or);

        floatingSwitch = new Switch(this);
        floatingSwitch.setText("Floating Icon ON / OFF");
        floatingSwitch.setTextColor(android.graphics.Color.WHITE);
        floatingSwitch.setTextSize(16);
        floatingSwitch.setOnCheckedChangeListener((buttonView,isChecked)-> {
            if(isChecked) enableFloating();
            else stopFloating();
        });
        root.addView(floatingSwitch);

        autoScanSwitch = new Switch(this);
        autoScanSwitch.setText("Auto Scan ON / OFF");
        autoScanSwitch.setTextColor(android.graphics.Color.WHITE);
        autoScanSwitch.setTextSize(16);
        root.addView(autoScanSwitch);

        resultText = text("ANALYSIS RESULT\nUpload a market screenshot to begin.",18,false);
        resultText.setTextColor(android.graphics.Color.WHITE);
        resultText.setPadding(dp(16),dp(16),dp(16),dp(16));
        root.addView(resultText,new LinearLayout.LayoutParams(-1,dp(470)));

        TextView disclaimer=text(
            "1000 logic probes â€¢ Real screenshot mode â€¢ Evidence score is not a guaranteed win rate.",
            12,false);
        disclaimer.setTextColor(android.graphics.Color.GRAY);
        root.addView(disclaimer);

        setContentView(scroll);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(i,PICK_IMAGE); }
        catch(Exception e) {
            i.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(i,PICK_IMAGE);
        }
    }

    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req==PICK_IMAGE && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            executor.execute(()->{
                Bitmap b=null;
                try {
                    InputStream in=getContentResolver().openInputStream(uri);
                    b=BitmapFactory.decodeStream(in);
                    if(in!=null) in.close();
                } catch(Exception ignored) {}
                Bitmap finalB=b;
                runOnUiThread(()->{
                    if(finalB==null){toast("Could not open image");return;}
                    if(selectedBitmap!=null && !selectedBitmap.isRecycled()) selectedBitmap.recycle();
                    selectedBitmap=finalB;
                    preview.setImageBitmap(finalB);
                    statusMessage("PHOTO READY â€¢ ANALYZING...");
                    analyzeSelected();
                });
            });
        } else if(req==REQUEST_CAPTURE && result==RESULT_OK && data!=null) {
            Intent s=new Intent(this,ScreenCaptureService.class);
            s.setAction(ScreenCaptureService.ACTION_START_CAPTURE);
            s.putExtra("resultCode",result);
            s.putExtra("data",data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(s); else startService(s);
            toast("Screen capture enabled");
            startFloatingService();
        }
    }

    private void analyzeSelected() {
        if(selectedBitmap==null || selectedBitmap.isRecycled()){
            toast("UPLOAD PHOTO FIRST"); return;
        }
        analyzeButton.setEnabled(false);
        statusMessage("ANALYZING â€¢ 1000 LOGIC CHECKS...");
        final Bitmap copy=selectedBitmap.copy(Bitmap.Config.ARGB_8888,false);
        final String market=String.valueOf(marketSpinner.getSelectedItem());
        executor.execute(()->{
            Analyzer.Result r=Analyzer.analyze(copy);
            if(!copy.isRecycled()) copy.recycle();
            runOnUiThread(()->{
                analyzeButton.setEnabled(true);
                showResult(r,market);
            });
        });
    }

    private void showResult(Analyzer.Result r,String market) {
        StringBuilder s=new StringBuilder();
        s.append("ANALYSIS RESULT\n\n");
        s.append(r.signal).append("  |  ").append(String.format(java.util.Locale.US,"%.1f%%",r.confidence)).append("\n\n");
        s.append("Market: ").append(market).append("\n");
        s.append("Chart quality: ").append(String.format(java.util.Locale.US,"%.1f%%",r.quality)).append("\n");
        s.append("Real candles detected: ").append(r.detectedCandles).append("\n");
        s.append("1000 logic probes: ").append(r.evaluatedRules).append("\n");
        s.append("Bull evidence: ").append(r.bullishCount).append("\n");
        s.append("Bear evidence: ").append(r.bearishCount).append("\n");
        s.append("Neutral: ").append(r.neutralCount).append("\n");
        s.append("Timeframe: ").append(r.timeframe).append("\n\n");
        s.append("NEXT CANDLE PREDICTION\n");
        s.append("Color: ").append(r.nextCandleColor).append("\n");
        s.append("Size: ").append(r.nextCandleSize).append("\n");
        s.append("Body: ").append(String.format(java.util.Locale.US,"%.1f%%",r.nextBodyRatio)).append("\n");
        s.append("Upper Wick: ").append(String.format(java.util.Locale.US,"%.1f%%",r.nextUpperWickRatio)).append("\n");
        s.append("Lower Wick: ").append(String.format(java.util.Locale.US,"%.1f%%",r.nextLowerWickRatio)).append("\n\n");
        for(String c:r.checks) s.append("âœ“ ").append(c).append("\n");
        resultText.setText(s.toString());
        statusMessage("ANALYSIS COMPLETE  |  "+market);
    }

    private void enableFloating() {
        if(!Settings.canDrawOverlays(this)){
            toast("Allow 'Display over other apps' first");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName()))); } catch(Exception ignored){}
            floatingSwitch.setChecked(false);
            return;
        }
        if(!ScreenCaptureService.isCaptureActive()){
            toast("Screen Capture permission is required");
            Intent m=getSystemService(android.media.projection.MediaProjectionManager.class).createScreenCaptureIntent();
            startActivityForResult(m,REQUEST_CAPTURE);
            floatingSwitch.setChecked(false);
            return;
        }
        startFloatingService();
    }

    private void startFloatingService(){
        Intent s=new Intent(this,FloatingScannerService.class);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(s); else startService(s);
    }

    private void stopFloating(){
        stopService(new Intent(this,FloatingScannerService.class));
    }

    private void statusMessage(String x){ if(resultText!=null) resultText.setText(x); }

    private void startCountdown(long sec){
        if(timer!=null)timer.cancel();
        timer=new CountDownTimer(sec*1000,1000){
            public void onTick(long ms){ countdownText.setText("NEXT CANDLE IN "+format(ms)); }
            public void onFinish(){ startCountdown(60); }
        }.start();
    }

    private String format(long ms){
        long total=Math.max(0,ms/1000);
        return String.format(java.util.Locale.US,"%02d:%02d",total/60,total%60);
    }

    private Spinner spinner(String[] a){
        Spinner s=new Spinner(this);
        ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,a){
            public View getView(int p,android.view.View c,android.view.ViewGroup parent){
                TextView t=(TextView)super.getView(p,c,parent); t.setTextColor(android.graphics.Color.WHITE); t.setTextSize(15); return t;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(ad); return s;
    }

    private Button button(String t,int color){
        Button b=new Button(this); b.setText(t); b.setTextSize(16); b.setTextColor(android.graphics.Color.WHITE);
        b.setAllCaps(false); b.setBackgroundColor(color); return b;
    }

    private TextView text(String t,float size,boolean bold){
        TextView v=new TextView(this); v.setText(t); v.setTextSize(size);
        if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);
        v.setPadding(0,dp(8),0,dp(8)); return v;
    }

    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void toast(String x){Toast.makeText(this,x,Toast.LENGTH_SHORT).show();}

    @Override protected void onDestroy(){
        if(timer!=null)timer.cancel();
        executor.shutdownNow();
        if(selectedBitmap!=null && !selectedBitmap.isRecycled()) selectedBitmap.recycle();
        super.onDestroy();
    }
}
