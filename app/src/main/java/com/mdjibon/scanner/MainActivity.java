package com.mdjibon.scanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screenshot-only main screen.
 *
 * Direct screen scanning / floating overlay controls are intentionally removed.
 * User selects a screenshot, previews it, then runs the local analyzer.
 */
public class MainActivity extends Activity {

    private static final int PICK_IMAGE = 4101;

    private ImageView preview;
    private Button chooseButton;
    private Button analyzeButton;
    private TextView status;
    private TextView resultTitle;
    private TextView resultDetails;

    private Bitmap selectedBitmap;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        executor = Executors.newSingleThreadExecutor();
        buildUi();
    }

    private void buildUi() {
        int bg = Color.rgb(12, 16, 28);
        int card = Color.rgb(25, 30, 45);
        int text = Color.rgb(238, 242, 248);
        int muted = Color.rgb(160, 169, 185);
        int green = Color.rgb(18, 190, 91);
        int blue = Color.rgb(40, 135, 235);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));

        TextView title = tv("MD JIBON", 28, text);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, lp(-1, -2));

        TextView sub = tv("SCREENSHOT ANALYZER", 13, green);
        sub.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams subLp = lp(-1, -2);
        subLp.topMargin = dp(2);
        root.addView(sub, subLp);

        TextView note = tv(
                "Upload any supported chart screenshot. "
                        + "Zoomed-in, zoomed-out and different screenshot sizes are handled "
                        + "without creating artificial candles.",
                13, muted);
        note.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams noteLp = lp(-1, -2);
        noteLp.topMargin = dp(10);
        root.addView(note, noteLp);

        LinearLayout imageCard = card(card);
        LinearLayout.LayoutParams imageLp = lp(-1, dp(440));
        imageLp.topMargin = dp(16);
        imageCard.setPadding(dp(8), dp(8), dp(8), dp(8));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(9, 12, 20));
        imageCard.addView(preview, lp(-1, -1));
        root.addView(imageCard, imageLp);

        chooseButton = button("SELECT SCREENSHOT", blue);
        LinearLayout.LayoutParams chooseLp = lp(-1, dp(52));
        chooseLp.topMargin = dp(14);
        root.addView(chooseButton, chooseLp);

        analyzeButton = button("ANALYZE SCREENSHOT", green);
        LinearLayout.LayoutParams analyzeLp = lp(-1, dp(52));
        analyzeLp.topMargin = dp(10);
        analyzeButton.setEnabled(false);
        root.addView(analyzeButton, analyzeLp);

        status = tv("WAITING FOR SCREENSHOT", 13, muted);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = lp(-1, dp(42));
        statusLp.topMargin = dp(8);
        root.addView(status, statusLp);

        LinearLayout resultCard = card(card);
        resultCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams resultLp = lp(-1, -2);
        resultLp.topMargin = dp(8);

        resultTitle = tv("RESULT", 22, text);
        resultTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        resultCard.addView(resultTitle, lp(-1, -2));

        resultDetails = tv(
                "No analysis yet.\n"
                        + "The app will show signal, evidence score, candle count, "
                        + "next-candle direction and estimated size here.",
                14, muted);
        resultDetails.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams detailLp = lp(-1, -2);
        detailLp.topMargin = dp(10);
        resultCard.addView(resultDetails, detailLp);

        root.addView(resultCard, resultLp);

        TextView disclaimer = tv(
                "REAL-DATA MODE: no synthetic candle fallback. "
                        + "Confidence is an internal evidence score, not a guaranteed win rate.",
                12, muted);
        disclaimer.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams disLp = lp(-1, -2);
        disLp.topMargin = dp(14);
        root.addView(disclaimer, disLp);

        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickScreenshot();
            }
        });

        analyzeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analyzeSelected();
            }
        });

        scroll.addView(root);
        setContentView(scroll);

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
    }

    private void pickScreenshot() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, PICK_IMAGE);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            startActivityForResult(fallback, PICK_IMAGE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            Bitmap b = decodeBitmap(uri);
            if (b == null) throw new Exception("Unable to decode image");

            if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
                selectedBitmap.recycle();
            }

            selectedBitmap = b;
            preview.setImageBitmap(selectedBitmap);
            analyzeButton.setEnabled(true);
            status.setText("SCREENSHOT READY â€¢ " + b.getWidth() + " Ã— " + b.getHeight());
            resultTitle.setText("READY TO ANALYZE");
            resultDetails.setText(
                    "The screenshot is loaded.\n"
                            + "Tap ANALYZE SCREENSHOT to start the local real-pixel candle analysis.");
        } catch (Exception e) {
            analyzeButton.setEnabled(false);
            Toast.makeText(this, "Could not read screenshot", Toast.LENGTH_LONG).show();
            status.setText("IMAGE READ ERROR");
        }
    }

    private Bitmap decodeBitmap(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) return null;
        Bitmap b;
        try {
            b = BitmapFactory.decodeStream(in);
        } finally {
            in.close();
        }
        return b;
    }

    private void analyzeSelected() {
        if (selectedBitmap == null || selectedBitmap.isRecycled()) {
            Toast.makeText(this, "Select a screenshot first", Toast.LENGTH_SHORT).show();
            return;
        }

        analyzeButton.setEnabled(false);
        chooseButton.setEnabled(false);
        status.setText("ANALYZING REAL PIXELSâ€¦");
        resultTitle.setText("ANALYZINGâ€¦");
        resultDetails.setText(
                "Detecting candle bodies â†’ ordering candles â†’ "
                        + "calculating multi-feature evidence.");

        // Copy a scaled bitmap reference safely for the worker.
        final Bitmap input = selectedBitmap;

        executor.submit(new Runnable() {
            @Override
            public void run() {
                final Analyzer.Result result = Analyzer.analyze(input);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showResult(result);
                        analyzeButton.setEnabled(true);
                        chooseButton.setEnabled(true);
                    }
                });
            }
        });
    }

    private void showResult(Analyzer.Result r) {
        String signal = r.signal;
        int color;

        if ("UP".equals(signal)) {
            color = Color.rgb(18, 205, 95);
        } else if ("DOWN".equals(signal)) {
            color = Color.rgb(235, 72, 65);
        } else {
            color = Color.rgb(245, 177, 55);
        }

        resultTitle.setText(signal + "  â€¢  " + String.format(
                Locale.US, "%.1f%%", r.confidence));
        resultTitle.setTextColor(color);

        String next = r.nextCandleColor;
        String size = r.nextCandleSize;

        String details =
                "Evidence score: " + String.format(Locale.US, "%.1f%%", r.confidence) + "\n"
                + "Chart quality: " + String.format(Locale.US, "%.1f%%", r.quality) + "\n"
                + "Real candles detected: " + r.detectedCandles + "\n"
                + "Rules/probes evaluated: " + r.evaluatedRules + "\n"
                + "Bull evidence: " + r.bullishCount + "\n"
                + "Bear evidence: " + r.bearishCount + "\n"
                + "Neutral: " + r.neutralCount + "\n"
                + "Timeframe: " + r.timeframe + "\n"
                + "Next candle: " + next + "\n"
                + "Estimated size: " + size + "\n"
                + "Estimated body ratio: " + String.format(
                        Locale.US, "%.1f%%", r.nextBodyRatio * 100.0)
                + "\n\n"
                + "No artificial candle fallback was used.\n"
                + "Candle order: oldest â†’ newest.";

        resultDetails.setText(details);
        resultDetails.setTextColor(Color.rgb(215, 222, 234));
        status.setText("ANALYSIS COMPLETE");

        // Keep result card visually neutral; only title communicates direction.
        resultTitle.setBackground(round(color, 0.12f));
        resultTitle.setPadding(dp(10), dp(8), dp(10), dp(8));
    }

    private TextView tv(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(color, 1.0f));
        return b;
    }

    private LinearLayout card(int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(color, 1.0f));
        return l;
    }

    private GradientDrawable round(int color, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (alpha >= 0.99f) a = 255;
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)));
        g.setCornerRadius(dp(14));
        return g;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (executor != null) {
            executor.shutdownNow();
        }

        if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
            selectedBitmap = null;
        }
    }
}
