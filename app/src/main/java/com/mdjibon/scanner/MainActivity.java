package com.mdjibon.scanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
 * MD JIBON screenshot-only analyzer.
 *
 * No floating scanner and no direct market screen capture are used.
 * User selects a market name, uploads a screenshot from Gallery, and runs
 * the local real-pixel analyzer.
 */
public class MainActivity extends Activity {

    private static final int PICK_IMAGE = 4101;

    // Names only. No payout/percentage is shown in the market picker.
    private static final String[] MARKETS = new String[] {
            "EUR/AUD (OTC)",
            "GBP/JPY (OTC)",
            "AUD/CHF (OTC)",
            "CAD/JPY (OTC)",
            "NZD/JPY (OTC)",
            "USD/COP (OTC)",
            "USD/EGP (OTC)",
            "USD/PHP (OTC)",
            "EUR/JPY (OTC)",
            "USD/CHF (OTC)",
            "EUR/NZD (OTC)",
            "USD/MXN (OTC)",
            "CHF/JPY (OTC)",
            "USD/CAD (OTC)",
            "EUR/GBP (OTC)",
            "NZD/USD (OTC)",
            "AUD/CAD (OTC)",
            "GBP/AUD (OTC)",
            "NZD/CHF (OTC)",
            "AUD/JPY (OTC)",
            "USD/IDR (OTC)",
            "USD/PKR (OTC)",
            "GBP/CAD (OTC)",
            "GBP/CHF (OTC)",
            "USD/INR (OTC)",
            "AUD/USD (OTC)",
            "USD/BRL (OTC)",
            "CAD/CHF (OTC)",
            "EUR/CAD (OTC)",
            "EUR/CHF (OTC)",
            "EUR/USD (OTC)",
            "USD/ARS (OTC)",
            "USD/BDT (OTC)",
            "USD/DZD (OTC)",
            "USD/JPY (OTC)",
            "GBP/NZD (OTC)",
            "NZD/CAD (OTC)",
            "AUD/NZD (OTC)",
            "USD/ZAR (OTC)",
            "GBP/USD (OTC)"
    };

    private ImageView preview;
    private Button uploadButton;
    private Button analyzeButton;
    private TextView marketButton;
    private TextView status;
    private TextView resultTitle;
    private TextView resultDetails;

    private Bitmap selectedBitmap;
    private String selectedMarket = "EUR/JPY (OTC)";
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        buildUi();
    }

    private void buildUi() {
        final int bg = Color.rgb(7, 12, 22);
        final int panel = Color.rgb(14, 24, 39);
        final int panel2 = Color.rgb(18, 31, 49);
        final int white = Color.rgb(240, 246, 255);
        final int muted = Color.rgb(160, 176, 198);
        final int green = Color.rgb(20, 210, 103);
        final int blue = Color.rgb(40, 128, 245);
        final int yellow = Color.rgb(255, 193, 55);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(24));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView title = tv("MD JIBON 99%", 29, white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, lp(-1, -2));

        TextView sub = tv("ACCURACY SIGNAL AI", 13, green);
        sub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams subLp = lp(-1, -2);
        subLp.topMargin = dp(3);
        header.addView(sub, subLp);

        TextView live = tv("â—  SCREENSHOT ANALYSIS MODE", 12, green);
        live.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams liveLp = lp(-1, -2);
        liveLp.topMargin = dp(8);
        header.addView(live, liveLp);
        root.addView(header, lp(-1, -2));

        // Market selector.
        LinearLayout marketCard = card(panel);
        marketCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams marketLp = lp(-1, -2);
        marketLp.topMargin = dp(14);

        TextView marketLabel = tv("SELECT TRADE PAIR", 11, muted);
        marketLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        marketCard.addView(marketLabel, lp(-1, -2));

        marketButton = tv(selectedMarket, 17, white);
        marketButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        marketButton.setGravity(Gravity.CENTER_VERTICAL);
        marketButton.setPadding(dp(14), 0, dp(14), 0);
        marketButton.setBackground(round(panel2, 1f));
        LinearLayout.LayoutParams mbLp = lp(-1, dp(50));
        mbLp.topMargin = dp(7);
        marketCard.addView(marketButton, mbLp);
        root.addView(marketCard, marketLp);

        marketButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMarketPicker();
            }
        });

        // Screenshot preview.
        LinearLayout imageCard = card(panel);
        imageCard.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout.LayoutParams imageLp = lp(-1, dp(390));
        imageLp.topMargin = dp(14);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(4, 8, 15));
        imageCard.addView(preview, lp(-1, -1));
        root.addView(imageCard, imageLp);

        // Upload button: direct image picker/gallery.
        uploadButton = button("UPLOAD PHOTO", blue);
        LinearLayout.LayoutParams uploadLp = lp(-1, dp(62));
        uploadLp.topMargin = dp(14);
        root.addView(uploadButton, uploadLp);

        analyzeButton = button("ANALYZE MARKET", green);
        LinearLayout.LayoutParams analyzeLp = lp(-1, dp(62));
        analyzeLp.topMargin = dp(10);
        analyzeButton.setEnabled(false);
        root.addView(analyzeButton, analyzeLp);

        status = tv("SELECT A MARKET AND UPLOAD A SCREENSHOT", 13, muted);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = lp(-1, dp(46));
        statusLp.topMargin = dp(5);
        root.addView(status, statusLp);

        // Result panel.
        LinearLayout resultCard = card(panel);
        resultCard.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout.LayoutParams resultLp = lp(-1, -2);
        resultLp.topMargin = dp(6);

        resultTitle = tv("ANALYSIS RESULT", 23, white);
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resultCard.addView(resultTitle, lp(-1, -2));

        resultDetails = tv(
                "No analysis yet.\n\n"
                        + "The result will show signal, evidence score, real candle count, "
                        + "1000 analytical probes, next-candle color, size and body ratio.",
                14, muted);
        resultDetails.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams detailsLp = lp(-1, -2);
        detailsLp.topMargin = dp(10);
        resultCard.addView(resultDetails, detailsLp);
        root.addView(resultCard, resultLp);

        TextView disclaimer = tv(
                "REAL-DATA MODE: no synthetic candle fallback. "
                        + "Evidence score is not a guaranteed win rate.",
                12, muted);
        disclaimer.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams disLp = lp(-1, -2);
        disLp.topMargin = dp(14);
        root.addView(disclaimer, disLp);

        uploadButton.setOnClickListener(new View.OnClickListener() {
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

    private void showMarketPicker() {
        int selected = 0;
        for (int i = 0; i < MARKETS.length; i++) {
            if (MARKETS[i].equals(selectedMarket)) {
                selected = i;
                break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select trade pair")
                .setSingleChoiceItems(MARKETS, selected, null)
                .create();

        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                android.widget.ListView list = dialog.getListView();
                list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        selectedMarket = MARKETS[position];
                        marketButton.setText(selectedMarket);
                        status.setText("MARKET SELECTED: " + selectedMarket);
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
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

        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            Bitmap b = decodeBitmap(uri);
            if (b == null) throw new Exception("Unable to decode image");

            selectedBitmap = b;
            preview.setImageBitmap(b);
            analyzeButton.setEnabled(true);
            status.setText("SCREENSHOT READY  |  " + b.getWidth() + " x " + b.getHeight());
            resultTitle.setText("READY TO ANALYZE");
            resultTitle.setTextColor(Color.rgb(240, 246, 255));
            resultTitle.setBackground(null);
            resultDetails.setText(
                    "Market: " + selectedMarket + "\n\n"
                            + "Tap ANALYZE MARKET to detect real candles and evaluate the screenshot.");
        } catch (Exception e) {
            analyzeButton.setEnabled(false);
            status.setText("IMAGE READ ERROR");
            Toast.makeText(this, "Could not read screenshot", Toast.LENGTH_LONG).show();
        }
    }

    /** Decode at a bounded size to avoid unnecessary memory use on Android phones. */
    private Bitmap decodeBitmap(Uri uri) throws Exception {
        InputStream boundsIn = getContentResolver().openInputStream(uri);
        if (boundsIn == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeStream(boundsIn, null, bounds);
        } finally {
            boundsIn.close();
        }

        int width = Math.max(1, bounds.outWidth);
        int height = Math.max(1, bounds.outHeight);
        int maxDim = 1600;
        int sample = 1;
        while (width / sample > maxDim || height / sample > maxDim) sample *= 2;

        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeStream(in, null, opts);
        } finally {
            in.close();
        }
    }

    private void analyzeSelected() {
        final Bitmap input = selectedBitmap;
        if (input == null || input.isRecycled()) {
            Toast.makeText(this, "Upload a screenshot first", Toast.LENGTH_SHORT).show();
            return;
        }

        uploadButton.setEnabled(false);
        analyzeButton.setEnabled(false);
        marketButton.setEnabled(false);
        status.setText("ANALYZING " + selectedMarket + "...");
        resultTitle.setText("ANALYSIS IN PROGRESS");
        resultTitle.setTextColor(Color.rgb(20, 210, 103));
        resultTitle.setBackground(round(Color.rgb(12, 65, 45), 1f));
        resultDetails.setText(
                "Detecting real candle bodies...\n"
                        + "Ordering candles from oldest to newest...\n"
                        + "Evaluating 1000 parameterized analytical probes...");

        final String marketAtStart = selectedMarket;

        executor.submit(new Runnable() {
            @Override
            public void run() {
                final Analyzer.Result result = Analyzer.analyze(input);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showResult(result, marketAtStart);
                        uploadButton.setEnabled(true);
                        analyzeButton.setEnabled(true);
                        marketButton.setEnabled(true);
                    }
                });
            }
        });
    }

    private void showResult(Analyzer.Result r, String market) {
        int color;
        if ("UP".equals(r.signal)) {
            color = Color.rgb(20, 210, 103);
        } else if ("DOWN".equals(r.signal)) {
            color = Color.rgb(238, 72, 65);
        } else {
            color = Color.rgb(255, 193, 55);
        }

        resultTitle.setText(r.signal + "  |  "
                + String.format(Locale.US, "%.1f%%", r.confidence));
        resultTitle.setTextColor(color);
        resultTitle.setBackground(round(Color.argb(45,
                Color.red(color), Color.green(color), Color.blue(color)), 1f));
        resultTitle.setPadding(dp(10), dp(8), dp(10), dp(8));

        String details =
                "Market: " + market + "\n"
                + "Evidence score: " + String.format(Locale.US, "%.1f%%", r.confidence) + "\n"
                + "Chart quality: " + String.format(Locale.US, "%.1f%%", r.quality) + "\n"
                + "Real candles detected: " + r.detectedCandles + "\n"
                + "Rules/probes evaluated: " + r.evaluatedRules + "\n"
                + "Bull evidence: " + r.bullishCount + "\n"
                + "Bear evidence: " + r.bearishCount + "\n"
                + "Neutral: " + r.neutralCount + "\n"
                + "Timeframe: " + r.timeframe + "\n\n"
                + "NEXT CANDLE\n"
                + "Color: " + r.nextCandleColor + "\n"
                + "Size: " + r.nextCandleSize + "\n"
                + "Body ratio: " + String.format(Locale.US, "%.1f%%", r.nextBodyRatio * 100.0)
                + "\n\n"
                + "Candle order: oldest to newest.\n"
                + "No artificial candle fallback was used.";

        resultDetails.setText(details);
        resultDetails.setTextColor(Color.rgb(215, 224, 238));
        status.setText("ANALYSIS COMPLETE  |  " + market);
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
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(color, 1f));
        return b;
    }

    private LinearLayout card(int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(color, 1f));
        return l;
    }

    private GradientDrawable round(int color, float alpha) {
        int a;
        if (Color.alpha(color) != 255) {
            a = Color.alpha(color);
        } else {
            a = Math.round(255f * Math.max(0f, Math.min(1f, alpha)));
        }
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)));
        g.setCornerRadius(dp(15));
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
        if (executor != null) executor.shutdownNow();
        // Do not recycle selectedBitmap here: a worker may still be reading it.
        selectedBitmap = null;
    }
}
