package com.mdjibon.scanner;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 7001;
    private static final int REQUEST_CAPTURE = 7002;
    private static final String PREF = "scanner_settings";
    private static final String KEY_MARKET = "market";
    private static final String KEY_TIMEFRAME = "timeframe";

    private FrameLayout root;
    private LinearLayout content;
    private TextView pageTitle, status, signalView, scoreView, detailsView;
    private ImageView preview;
    private Switch floatingSwitch, autoSwitch, soundSwitch, vibrationSwitch;
    private CountDownTimer timer;
    private Bitmap selectedBitmap;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String[] MARKETS = {
            "EUR/USD (OTC)", "GBP/USD (OTC)", "USD/JPY (OTC)", "USD/CHF (OTC)",
            "AUD/USD (OTC)", "USD/CAD (OTC)", "EUR/GBP (OTC)", "EUR/JPY (OTC)",
            "GBP/JPY (OTC)", "AUD/JPY (OTC)", "USD/BRL (OTC)", "USD/INR (OTC)",
            "USD/PKR (OTC)", "USD/BDT (OTC)", "EUR/AUD (OTC)", "GBP/AUD (OTC)",
            "AUD/CAD (OTC)", "NZD/USD (OTC)", "NZD/JPY (OTC)", "CAD/JPY (OTC)",
            "CHF/JPY (OTC)", "EUR/CAD (OTC)", "EUR/CHF (OTC)", "GBP/CAD (OTC)",
            "GBP/CHF (OTC)", "NZD/CAD (OTC)", "AUD/NZD (OTC)", "USD/ZAR (OTC)"
    };
    private static final String[] TIMEFRAMES = {"1 MIN", "5 MIN", "15 SEC", "30 SEC"};

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (ScreenCaptureService.ACTION_RESULT.equals(i.getAction())) {
                showResult(
                        i.getStringExtra("signal"),
                        i.getFloatExtra("score", 0),
                        i.getFloatExtra("quality", 0),
                        i.getIntExtra("candles", 0),
                        i.getIntExtra("rules", 0),
                        i.getStringExtra("nextColor"),
                        i.getStringExtra("nextSize"),
                        i.getStringExtra("currentColor"),
                        i.getStringExtra("savedPath"));
            } else if (ScreenCaptureService.ACTION_ERROR.equals(i.getAction())) {
                status.setText("ERROR: " + i.getStringExtra("message"));
                Toast.makeText(MainActivity.this, i.getStringExtra("message"), Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(3, 8, 15));
        getWindow().setNavigationBarColor(Color.rgb(3, 8, 15));
        buildShell();
        IntentFilter f = new IntentFilter();
        f.addAction(ScreenCaptureService.ACTION_RESULT);
        f.addAction(ScreenCaptureService.ACTION_ERROR);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean running = Settings.canDrawOverlays(this) && FloatingScannerService.isRunning();
        if (floatingSwitch != null) floatingSwitch.setChecked(running);
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(3, 8, 15));
        setContentView(root);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setPadding(dp(18), dp(14), dp(18), dp(10));
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = logoView(52);
        header.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        pageTitle = text("MD JIBON", 23, true);
        pageTitle.setTextColor(Color.WHITE);
        titles.addView(pageTitle);
        TextView sub = text("REAL SCREEN SCANNER", 11, false);
        sub.setTextColor(Color.rgb(85, 230, 165));
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setGravity(Gravity.CENTER);
        String[] names = {"HOME", "SCAN", "HISTORY", "SETTINGS"};
        for (String n : names) {
            Button x = smallButton(n);
            x.setOnClickListener(v -> {
                if ("HOME".equals(n)) showHome();
                else if ("SCAN".equals(n)) showScanPage();
                else if ("HISTORY".equals(n)) showHistory();
                else showSettings();
            });
            nav.addView(x, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        shell.addView(nav);
    }

    private void showHome() {
        pageTitle.setText("MD JIBON");
        content.removeAllViews();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        TextView hero = text("REAL MARKET\nSCREEN ANALYSIS", 27, true);
        hero.setTextColor(Color.WHITE);
        hero.setGravity(Gravity.CENTER);
        content.addView(hero, lp(-1, 92));

        TextView note = text("Capture the visible chart. Analyze it locally. Get UP / DOWN + evidence score.", 13, false);
        note.setTextColor(Color.LTGRAY);
        note.setGravity(Gravity.CENTER);
        content.addView(note, lp(-1, 48));

        content.addView(sectionLabel("MARKET & TIMEFRAME"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button market = button("MARKET\n" + market(), false);
        market.setOnClickListener(v -> chooseMarket());
        Button tf = button("TIMEFRAME\n" + timeframe(), false);
        tf.setOnClickListener(v -> chooseTimeframe());
        row.addView(market, new LinearLayout.LayoutParams(0, dp(72), 1));
        LinearLayout.LayoutParams t = new LinearLayout.LayoutParams(0, dp(72), 1);
        t.leftMargin = dp(8); row.addView(tf, t);
        content.addView(row);

        Button scan = primary("SCAN CURRENT SCREEN");
        scan.setOnClickListener(v -> startLiveScanFromApp());
        content.addView(scan, lp(-1, 62));

        Button upload = button("UPLOAD SCREENSHOT\nAnalyze & save permanently in app history", false);
        upload.setOnClickListener(v -> pickImage());
        content.addView(upload, lp(-1, 70));

        content.addView(sectionLabel("FLOATING SCANNER"));
        floatingSwitch = new Switch(this);
        floatingSwitch.setText("MD JIBON Floating Icon");
        floatingSwitch.setTextColor(Color.WHITE);
        floatingSwitch.setTextSize(16);
        floatingSwitch.setChecked(Settings.canDrawOverlays(this) && FloatingScannerService.isRunning());
        floatingSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) enableFloating(); else stopFloating();
        });
        content.addView(floatingSwitch, lp(-1, 58));

        Button history = button("VIEW SAVED SCANS", false);
        history.setOnClickListener(v -> showHistory());
        content.addView(history, lp(-1, 56));
    }

    private void showScanPage() {
        pageTitle.setText("LIVE SCAN");
        content.removeAllViews();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));
        status = text("READY â€” press SCAN", 17, true);
        status.setTextColor(Color.rgb(80, 240, 165));
        content.addView(status, lp(-1, 58));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setBackgroundColor(Color.rgb(7, 15, 25));
        content.addView(preview, lp(-1, 300));

        signalView = text("NO SIGNAL", 42, true);
        signalView.setGravity(Gravity.CENTER);
        signalView.setTextColor(Color.WHITE);
        content.addView(signalView, lp(-1, 85));
        scoreView = text("Evidence: --", 22, true);
        scoreView.setGravity(Gravity.CENTER);
        scoreView.setTextColor(Color.rgb(75, 240, 160));
        content.addView(scoreView, lp(-1, 48));
        detailsView = text("", 14, false);
        detailsView.setTextColor(Color.LTGRAY);
        content.addView(detailsView, lp(-1, 210));

        Button scan = primary("SCAN AGAIN");
        scan.setOnClickListener(v -> startLiveScanFromApp());
        content.addView(scan, lp(-1, 58));
        Button upload = button("USE SCREENSHOT INSTEAD", false);
        upload.setOnClickListener(v -> pickImage());
        content.addView(upload, lp(-1, 56));
    }

    private void showHistory() {
        pageTitle.setText("HISTORY");
        content.removeAllViews();
        content.setPadding(dp(14), dp(8), dp(14), dp(18));
        File dir = new File(getFilesDir(), "scans");
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
        if (files == null || files.length == 0) {
            TextView empty = text("No saved scans yet.\nRun a floating scan or upload a screenshot.", 17, false);
            empty.setTextColor(Color.LTGRAY); empty.setGravity(Gravity.CENTER);
            content.addView(empty, lp(-1, 240));
            return;
        }
        java.util.Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            LinearLayout card = new LinearLayout(this);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) img.setImageBitmap(b);
            card.addView(img, new LinearLayout.LayoutParams(dp(110), dp(78)));
            TextView meta = text(readMeta(f), 13, false);
            meta.setTextColor(Color.WHITE); meta.setPadding(dp(10),0,0,0);
            card.addView(meta, new LinearLayout.LayoutParams(0, dp(78), 1));
            content.addView(card, lp(-1, 94));
        }
    }

    private void showSettings() {
        pageTitle.setText("SETTINGS");
        content.removeAllViews();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));
        content.addView(sectionLabel("SCANNER CONTROLS"));
        floatingSwitch = new Switch(this);
        floatingSwitch.setText("Floating Icon"); floatingSwitch.setTextColor(Color.WHITE); floatingSwitch.setChecked(FloatingScannerService.isRunning());
        floatingSwitch.setOnCheckedChangeListener((b,c)-> { if(c) enableFloating(); else stopFloating(); });
        content.addView(floatingSwitch, lp(-1,58));
        autoSwitch = new Switch(this);
        autoSwitch.setText("Auto Scan on selected timeframe"); autoSwitch.setTextColor(Color.WHITE);
        autoSwitch.setChecked(getSharedPreferences(PREF,0).getBoolean("autoScan",false));
        autoSwitch.setOnCheckedChangeListener((b,c)->setAutoScan(c));
        content.addView(autoSwitch, lp(-1,58));
        soundSwitch = new Switch(this); soundSwitch.setText("Signal sound"); soundSwitch.setTextColor(Color.WHITE);
        content.addView(soundSwitch, lp(-1,58));
        vibrationSwitch = new Switch(this); vibrationSwitch.setText("Signal vibration"); vibrationSwitch.setTextColor(Color.WHITE);
        content.addView(vibrationSwitch, lp(-1,58));
        content.addView(sectionLabel("ANALYSIS"));
        TextView rules = text("1000 parameterized logic probes are evaluated from the captured chart.\n\nThe percentage shown is an evidence/confidence score, not a guaranteed win probability. Real accuracy must be measured from saved history.", 14, false);
        rules.setTextColor(Color.LTGRAY); content.addView(rules, lp(-1,130));
        Button clear = button("DELETE ALL SAVED SCANS", false);
        clear.setOnClickListener(v -> { deleteScans(); showHistory(); });
        content.addView(clear, lp(-1,56));
    }

    private void startLiveScanFromApp() {
        showScanPage();
        if (!ScreenCaptureService.isCaptureActive()) { requestCapture(); return; }
        Intent i = new Intent(this, ScreenCaptureService.class); i.setAction(ScreenCaptureService.ACTION_SCAN); startServiceCompat(i);
        status.setText("SCANNING SCREEN â€¢ 1000 LOGIC CHECKS...");
    }

    private void enableFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Display over other apps first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));
            if (floatingSwitch != null) floatingSwitch.setChecked(false);
            return;
        }
        if (!ScreenCaptureService.isCaptureActive()) { requestCapture(); return; }
        Intent i = new Intent(this, FloatingScannerService.class); startServiceCompat(i);
    }

    private void stopFloating() { stopService(new Intent(this, FloatingScannerService.class)); }

    private void requestCapture() {
        android.media.projection.MediaProjectionManager m = (android.media.projection.MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==REQUEST_CAPTURE && result==RESULT_OK && data!=null){
            Intent i=new Intent(this,ScreenCaptureService.class); i.setAction(ScreenCaptureService.ACTION_START_CAPTURE);
            i.putExtra("resultCode",result); i.putExtra("data",data); startServiceCompat(i);
            Toast.makeText(this,"Screen capture is ready.",Toast.LENGTH_SHORT).show();
            if(floatingSwitch!=null && floatingSwitch.isChecked()) startServiceCompat(new Intent(this,FloatingScannerService.class));
            return;
        }
        if(req==PICK_IMAGE && result==RESULT_OK && data!=null){
            try{
                InputStream in=getContentResolver().openInputStream(data.getData());
                Bitmap b=BitmapFactory.decodeStream(in); if(in!=null)in.close();
                if(selectedBitmap!=null&&!selectedBitmap.isRecycled())selectedBitmap.recycle();
                selectedBitmap=b;
                if(b!=null) analyzeSelected(); else Toast.makeText(this,"Could not read image.",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Image load failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }

    private void pickImage() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_IMAGE); }

    @Override protected void onActivityResult_old(int a,int b,Intent c){}

    private void chooseMarket(){
        final String[] items=MARKETS; new android.app.AlertDialog.Builder(this).setTitle("Select Market").setItems(items,(d,w)->{saveMarket(items[w]);showHome();}).show();
    }
    private void chooseTimeframe(){ final String[] items=TIMEFRAMES; new android.app.AlertDialog.Builder(this).setTitle("Select Timeframe").setItems(items,(d,w)->{saveTf(items[w]);showHome();}).show(); }
    private String market(){return getSharedPreferences(PREF,0).getString(KEY_MARKET,"USD/BRL (OTC)");}
    private String timeframe(){return getSharedPreferences(PREF,0).getString(KEY_TIMEFRAME,"1 MIN");}
    private void saveMarket(String x){getSharedPreferences(PREF,0).edit().putString(KEY_MARKET,x).apply();}
    private void saveTf(String x){getSharedPreferences(PREF,0).edit().putString(KEY_TIMEFRAME,x).apply();}
    private void setAutoScan(boolean on){getSharedPreferences(PREF,0).edit().putBoolean("autoScan",on).apply();Intent i=new Intent(this,FloatingScannerService.class);i.setAction(FloatingScannerService.ACTION_AUTO_SCAN);i.putExtra("enabled",on);startServiceCompat(i);}

    private void showResult(String signal,float score,float quality,int candles,int rules,String nextColor,String nextSize,String current,String saved){
        showScanPage();
        if(preview!=null && saved!=null){Bitmap b=BitmapFactory.decodeFile(saved);if(b!=null)preview.setImageBitmap(b);}
        String s=signal==null?"NO TRADE":signal; signalView.setText(s); signalView.setTextColor("UP".equals(s)?Color.rgb(65,245,155):Color.rgb(255,80,90));
        scoreView.setText(String.format(Locale.US,"Evidence %.1f%%",score));
        detailsView.setText("Market: "+market()+"\nTimeframe: "+timeframe()+"\nCurrent candle: "+safe(current)+"\nNext candle: "+safe(nextColor)+"\nSize: "+safe(nextSize)+"\nReal candles detected: "+candles+"\nLogic checks: "+rules+"\nQuality: "+String.format(Locale.US,"%.1f",quality)+"\n\nSaved inside app: "+(saved==null?"NO":"YES"));
        status.setText("ANALYSIS COMPLETE");
    }

    private void analyzeSelected(){ if(selectedBitmap==null){Toast.makeText(this,"Select a screenshot first.",Toast.LENGTH_SHORT).show();return;} showScanPage(); status.setText("ANALYZING UPLOADED SCREENSHOT..."); final Bitmap src=selectedBitmap.copy(Bitmap.Config.ARGB_8888,false); executor.execute(()->{Analyzer.Result r=Analyzer.analyze(src,timeframe());String path=saveBitmap(src,r);runOnUiThread(()->showResult(r.signal,(float)r.confidence,(float)r.quality,r.detectedCandles,r.evaluatedRules,r.nextCandleColor,r.nextCandleSize,r.currentCandleColor,path));src.recycle();}); }

    private String saveBitmap(Bitmap b,Analyzer.Result r){try{File d=new File(getFilesDir(),"scans");if(!d.exists())d.mkdirs();long ts=System.currentTimeMillis();File img=new File(d,"scan_"+ts+".png");FileOutputStream out=new FileOutputStream(img);b.compress(Bitmap.CompressFormat.PNG,100,out);out.close();File meta=new File(d,"scan_"+ts+".txt");FileOutputStream m=new FileOutputStream(meta);String x="Signal="+r.signal+"\nScore="+r.confidence+"\nMarket="+market()+"\nTimeframe="+timeframe()+"\nCurrent="+r.currentCandleColor+"\nNext="+r.nextCandleColor+"\nSize="+r.nextCandleSize+"\nCandles="+r.detectedCandles+"\nRules="+r.evaluatedRules; m.write(x.getBytes("UTF-8"));m.close();return img.getAbsolutePath();}catch(Exception e){return null;}}

    private String readMeta(File image){try{File m=new File(image.getParent(),image.getName().replace(".png",".txt"));if(!m.exists())return image.getName();byte[] data=new byte[(int)m.length()];FileInputStream in=new FileInputStream(m);in.read(data);in.close();return new String(data,"UTF-8");}catch(Exception e){return image.getName();}}
    private void deleteScans(){File d=new File(getFilesDir(),"scans");File[] f=d.listFiles();if(f!=null)for(File x:f)if(x.isFile())x.delete();Toast.makeText(this,"Saved scans deleted.",Toast.LENGTH_SHORT).show();}

    private void startServiceCompat(Intent i){if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private TextView sectionLabel(String s){TextView t=text(s,12,true);t.setTextColor(Color.rgb(60,235,160));t.setPadding(0,dp(14),0,dp(6));return t;}
    private Button primary(String s){Button b=button(s,true);return b;}
    private Button button(String s,boolean green){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);b.setBackgroundColor(green?Color.rgb(0,175,105):Color.rgb(11,30,43));return b;}
    private Button smallButton(String s){return button(s,false);}
    private TextView logoView(int size){TextView t=text("MD\nJIBON",10,true);t.setGravity(Gravity.CENTER);t.setTextColor(Color.rgb(70,245,160));t.setBackgroundColor(Color.rgb(8,30,37));return t;}
    private TextView text(String s,float z,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private String safe(String x){return x==null?"UNKNOWN":x;}
    private void restartCountdown(){ }

    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){}if(timer!=null)timer.cancel();executor.shutdownNow();if(selectedBitmap!=null&&!selectedBitmap.isRecycled())selectedBitmap.recycle();super.onDestroy();}
}
