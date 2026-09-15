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

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 8101;
    private static final int REQUEST_CAPTURE = 8102;
    private static final String PREF = "scanner_settings";

    private static final String[] TIMEFRAMES = {
            "5 SEC", "10 SEC", "15 SEC", "30 SEC", "1 MIN", "5 MIN"
    };

    private static final String[] MARKETS = {
            "EUR/USD (OTC)", "GBP/USD (OTC)", "USD/JPY (OTC)", "USD/CHF (OTC)",
            "AUD/USD (OTC)", "USD/CAD (OTC)", "EUR/GBP (OTC)", "EUR/JPY (OTC)",
            "GBP/JPY (OTC)", "AUD/JPY (OTC)", "USD/BRL (OTC)", "USD/INR (OTC)",
            "USD/PKR (OTC)", "USD/BDT (OTC)", "EUR/AUD (OTC)", "GBP/AUD (OTC)",
            "AUD/CAD (OTC)", "NZD/USD (OTC)", "NZD/JPY (OTC)", "CAD/JPY (OTC)",
            "CHF/JPY (OTC)", "EUR/CAD (OTC)", "EUR/CHF (OTC)", "GBP/CAD (OTC)",
            "GBP/CHF (OTC)", "NZD/CAD (OTC)", "AUD/NZD (OTC)", "USD/ZAR (OTC)",
            "USD/MXN (OTC)", "USD/COP (OTC)"
    };

    private FrameLayout root;
    private LinearLayout content;
    private TextView title;
    private TextView status;
    private TextView signal;
    private TextView score;
    private TextView details;
    private ImageView preview;
    private Switch floatingSwitch;
    private Bitmap selectedBitmap;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ScreenCaptureService.ACTION_SCAN_STATUS.equals(action)) {
                if (status != null) {
                    status.setText(intent.getStringExtra("message"));
                }
            } else if (ScreenCaptureService.ACTION_RESULT.equals(action)) {
                showResult(intent);
            } else if (ScreenCaptureService.ACTION_ERROR.equals(action)) {
                String message = intent.getStringExtra("message");
                if (status != null) status.setText("ERROR: " + message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(3, 8, 15));
        getWindow().setNavigationBarColor(Color.rgb(3, 8, 15));
        buildShell();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenCaptureService.ACTION_RESULT);
        filter.addAction(ScreenCaptureService.ACTION_ERROR);
        filter.addAction(ScreenCaptureService.ACTION_SCAN_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (floatingSwitch != null) {
            floatingSwitch.setChecked(FloatingScannerService.isRunning());
        }
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(3, 8, 15));
        setContentView(root);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(8));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.md_jibon_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, lp(dp(58), dp(54)));

        title = text("MD JIBON", 21, true);
        title.setTextColor(Color.WHITE);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, lp(0, dp(54), 1));
        shell.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        shell.addView(scroll, lp(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(6), dp(5), dp(6), dp(7));
        String[] navItems = {"HOME", "SCAN", "SETTINGS"};
        for (String item : navItems) {
            Button b = button(item, false);
            b.setOnClickListener(v -> {
                if ("HOME".equals(item)) showHome();
                else if ("SCAN".equals(item)) showScan();
                else showSettings();
            });
            nav.addView(b, lp(0, dp(50), 1));
        }
        shell.addView(nav);
    }

    private void showHome() {
        title.setText("MD JIBON");
        content.removeAllViews();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        TextView heading = text("REAL SCREEN\nSCANNER", 28, true);
        heading.setGravity(Gravity.CENTER);
        heading.setTextColor(Color.WHITE);
        content.addView(heading, lp(-1, dp(100)));

        TextView note = text("CURRENT SCREEN ONLY â€¢ NO HISTORY", 13, true);
        note.setGravity(Gravity.CENTER);
        note.setTextColor(Color.rgb(70, 190, 255));
        content.addView(note, lp(-1, dp(44)));

        content.addView(label("MARKET & TIMEFRAME"));
        LinearLayout row = new LinearLayout(this);
        Button market = button("MARKET\n" + market(), false);
        market.setOnClickListener(v -> chooseMarket());
        Button timeframe = button("TIMEFRAME\n" + timeframe(), false);
        timeframe.setOnClickListener(v -> chooseTimeframe());
        row.addView(market, lp(0, dp(70), 1));
        LinearLayout.LayoutParams tfp = lp(0, dp(70), 1);
        tfp.leftMargin = dp(8);
        row.addView(timeframe, tfp);
        content.addView(row);

        Button scan = primary("START CONTINUOUS SCAN");
        scan.setOnClickListener(v -> startContinuous());
        content.addView(scan, lp(-1, dp(64)));

        Button upload = button("ANALYZE CURRENT/UPLOADED SCREENSHOT", false);
        upload.setOnClickListener(v -> pickImage());
        content.addView(upload, lp(-1, dp(58)));

        TextView info = text(
                "à¦¸à§à¦•à§à¦¯à¦¾à¦¨ à¦¶à§à¦°à§ à¦¹à¦²à§‡ à¦ªà§à¦°à§‹ à¦¡à¦¿à¦¸à¦ªà§à¦²à§‡à¦° à¦‰à¦ªà¦° à¦¨à§€à¦² translucent scan layer à¦‰à¦ªà¦° à¦¥à§‡à¦•à§‡ à¦¨à¦¿à¦šà§‡ sweep à¦•à¦°à¦¬à§‡à¥¤ " +
                "à¦¬à¦°à§à¦¤à¦®à¦¾à¦¨ screen à¦¬à¦¾à¦°à¦¬à¦¾à¦° analyse à¦¹à¦¬à§‡à¥¤ à¦¶à¦•à§à¦¤à¦¿à¦¶à¦¾à¦²à§€ agreement à¦¨à¦¾ à¦ªà¦¾à¦“à§Ÿà¦¾ à¦ªà¦°à§à¦¯à¦¨à§à¦¤ UP/DOWN à¦˜à§‹à¦·à¦£à¦¾ à¦¹à¦¬à§‡ à¦¨à¦¾à¥¤",
                13, false);
        info.setTextColor(Color.LTGRAY);
        info.setPadding(0, dp(14), 0, 0);
        content.addView(info, lp(-1, dp(120)));
    }

    private void showScan() {
        title.setText("LIVE SCAN");
        content.removeAllViews();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        status = text("READY", 17, true);
        status.setTextColor(Color.rgb(65, 240, 165));
        content.addView(status, lp(-1, dp(55)));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setBackgroundColor(Color.rgb(7, 15, 25));
        content.addView(preview, lp(-1, dp(285)));

        signal = text("FLOATING ICON", 28, true);
        signal.setGravity(Gravity.CENTER);
        signal.setTextColor(Color.rgb(70, 190, 255));
        content.addView(signal, lp(-1, dp(70)));

        score = text("FINAL SIGNAL APPEARS ONLY AFTER STRONG ANALYSIS", 13, true);
        score.setGravity(Gravity.CENTER);
        score.setTextColor(Color.rgb(120, 180, 220));
        content.addView(score, lp(-1, dp(54)));

        details = text(
                "Current screen analysis only.\nNo historical results are kept inside the app.",
                14, false);
        details.setTextColor(Color.LTGRAY);
        content.addView(details, lp(-1, dp(120)));

        Button b = primary("START / RESTART SCAN");
        b.setOnClickListener(v -> startContinuous());
        content.addView(b, lp(-1, dp(58)));
    }

    private void showSettings() {
        title.setText("SETTINGS");
        content.removeAllViews();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        content.addView(label("SCANNER"));
        floatingSwitch = new Switch(this);
        floatingSwitch.setText("Floating Icon ON / OFF");
        floatingSwitch.setTextColor(Color.WHITE);
        floatingSwitch.setTextSize(17);
        floatingSwitch.setChecked(FloatingScannerService.isRunning());
        floatingSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) enableFloating();
            else stopFloating();
        });
        content.addView(floatingSwitch, lp(-1, dp(60)));

        TextView t = text(
                "Settings-à¦ à¦¶à§à¦§à§ Floating Icon control à¦°à¦¾à¦–à¦¾ à¦¹à§Ÿà§‡à¦›à§‡à¥¤ " +
                "Market à¦à¦¬à¦‚ Timeframe Home à¦¥à§‡à¦•à§‡ à¦¨à¦¿à¦°à§à¦¬à¦¾à¦šà¦¨ à¦•à¦°à¦¾ à¦¯à¦¾à¦¬à§‡à¥¤ History, à¦ªà§à¦°à§‹à¦¨à§‹ panel à¦¬à¦¾ saved results à¦°à¦¾à¦–à¦¾ à¦¹à§Ÿ à¦¨à¦¾.",
                14, false);
        t.setTextColor(Color.LTGRAY);
        content.addView(t, lp(-1, dp(130)));
    }

    private void startContinuous() {
        showScan();
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Display over other apps first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }

        if (!ScreenCaptureService.isCaptureActive()) {
            requestCapture();
            return;
        }

        startFloatingService();
        status.setText("SCANNING LIVE â€¢ WAITING FOR STRONG SIGNAL...");
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingScannerService.class);
        intent.setAction(FloatingScannerService.ACTION_START_CONTINUOUS);
        startServiceCompat(intent);
    }

    private void enableFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Display over other apps first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            if (floatingSwitch != null) floatingSwitch.setChecked(false);
            return;
        }
        Intent intent = new Intent(this, FloatingScannerService.class);
        startServiceCompat(intent);
    }

    private void stopFloating() {
        stopService(new Intent(this, FloatingScannerService.class));
    }

    private void requestCapture() {
        android.media.projection.MediaProjectionManager manager =
                (android.media.projection.MediaProjectionManager)
                        getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent intent = new Intent(this, ScreenCaptureService.class);
                intent.setAction(ScreenCaptureService.ACTION_START_CAPTURE);
                intent.putExtra("resultCode", resultCode);
                intent.putExtra("data", data);
                startServiceCompat(intent);
                Toast.makeText(this, "Screen capture is ready.", Toast.LENGTH_SHORT).show();
                new android.os.Handler().postDelayed(this::startContinuous, 1000);
            } else {
                if (status != null) status.setText("Screen capture permission cancelled.");
            }
        } else if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            loadAndAnalyzeImage(data.getData());
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void loadAndAnalyzeImage(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (input != null) input.close();
            if (bitmap == null) throw new IllegalStateException("Image could not be decoded.");
            if (selectedBitmap != null && !selectedBitmap.isRecycled()) selectedBitmap.recycle();
            selectedBitmap = bitmap;
            analyzeSelected();
        } catch (Exception e) {
            Toast.makeText(this, "Image load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void analyzeSelected() {
        showScan();
        status.setText("ANALYZING CURRENT SCREENSHOT...");
        Bitmap source = selectedBitmap.copy(Bitmap.Config.ARGB_8888, false);
        String tf = timeframe();
        executor.execute(() -> {
            Analyzer.Result result = Analyzer.analyze(source, tf);
            source.recycle();
            runOnUiThread(() -> {
                showAnalyzerResult(result, null);
                status.setText(result.strongSignal
                        ? "STRONG SIGNAL FOUND"
                        : "ANALYZED â€¢ WAIT FOR STRONG LIVE AGREEMENT");
            });
        });
    }

    private void showResult(Intent intent) {
        showScan();
        String s = intent.getStringExtra("signal");
        float sc = intent.getFloatExtra("score", 0f);
        boolean strong = intent.getBooleanExtra("strong", false);

        signal.setText("FLOATING ICON");
        signal.setTextColor(Color.rgb(70, 190, 255));
        score.setText("FINAL SIGNAL APPEARS ON ICON");

        details.setText(
                "Market: " + market() + "\n" +
                "Timeframe: " + timeframe() + "\n" +
                "Current candle: " + intent.getStringExtra("currentColor") + "\n" +
                "Next candle may be: " + intent.getStringExtra("nextColor") + "\n" +
                "Expected size: " + intent.getStringExtra("nextSize") + "\n" +
                "Detected candles: " + intent.getIntExtra("candles", 0) + "\n" +
                "Logic checks: " + intent.getIntExtra("rules", 0) + "\n" +
                "Strong agreement: " + (strong ? "YES" : "NO")
        );
        status.setText(strong ? "STRONG SIGNAL FOUND â€¢ SCAN STOPPED" : "SCANNING â€¢ WAITING FOR STRONG AGREEMENT");
    }

    private void showAnalyzerResult(Analyzer.Result r, Bitmap ignored) {
        signal.setText("FLOATING ICON");
        signal.setTextColor(Color.rgb(70, 190, 255));
        score.setText("FINAL SIGNAL APPEARS ON ICON");
        details.setText(
                "Market: " + market() + "\n" +
                "Timeframe: " + timeframe() + "\n" +
                "Current candle: " + r.currentCandleColor + "\n" +
                "Next candle may be: " + r.nextCandleColor + "\n" +
                "Expected size: " + r.nextCandleSize + "\n" +
                "Detected candles: " + r.detectedCandles + "\n" +
                "Logic checks: " + r.evaluatedRules + "\n" +
                "Strong agreement: " + (r.strongSignal ? "YES" : "NO")
        );
    }

    private void chooseMarket() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Select Market")
                .setItems(MARKETS, (dialog, which) -> {
                    getSharedPreferences(PREF, 0).edit()
                            .putString("market", MARKETS[which]).apply();
                    showHome();
                }).show();
    }

    private void chooseTimeframe() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Select Timeframe")
                .setItems(TIMEFRAMES, (dialog, which) -> {
                    getSharedPreferences(PREF, 0).edit()
                            .putString("timeframe", TIMEFRAMES[which]).apply();
                    showHome();
                }).show();
    }

    private String market() {
        return getSharedPreferences(PREF, 0)
                .getString("market", "USD/BRL (OTC)");
    }

    private String timeframe() {
        return getSharedPreferences(PREF, 0)
                .getString("timeframe", "1 MIN");
    }

    private void startServiceCompat(Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    private TextView label(String textValue) {
        TextView t = text(textValue, 12, true);
        t.setTextColor(Color.rgb(60, 235, 160));
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    private Button primary(String textValue) {
        return button(textValue, true);
    }

    private Button button(String textValue, boolean primary) {
        Button b = new Button(this);
        b.setText(textValue);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(primary
                ? Color.rgb(0, 150, 230)
                : Color.rgb(10, 28, 42));
        return b;
    }

    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        executor.shutdownNow();
        if (selectedBitmap != null && !selectedBitmap.isRecycled()) selectedBitmap.recycle();
        super.onDestroy();
    }
}
