package com.mdjibon.scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PICK_IMAGE =
            5001;

    private static final int REQUEST_CAPTURE =
            5002;

    private static final String PREF =
            "scanner_settings";

    private static final String KEY_MARKET =
            "market";

    private static final String KEY_TIMEFRAME =
            "timeframe";

    private TextView status;
    private TextView resultText;

    private Spinner marketSpinner;
    private Spinner timeframeSpinner;

    private Switch floatingSwitch;

    private Bitmap selectedBitmap;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private static final String[] MARKETS = {

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

    private static final String[] TIMEFRAMES = {

            "1 MIN",
            "5 MIN",
            "15 SEC",
            "30 SEC"
    };

    @Override
    protected void onCreate(
            Bundle state
    ) {

        super.onCreate(state);

        getWindow().setStatusBarColor(
                Color.rgb(
                        4,
                        9,
                        18
                )
        );

        getWindow().setNavigationBarColor(
                Color.rgb(
                        3,
                        7,
                        14
                )
        );

        buildUi();

        restoreSettings();
    }

    // ============================================================
    // UI
    // ============================================================

    private void buildUi() {

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(
                true
        );

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(18),
                dp(14),
                dp(18),
                dp(22)
        );

        root.setBackgroundColor(
                Color.rgb(
                        4,
                        9,
                        18
                )
        );

        scroll.addView(root);

        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

        TextView title =
                text(
                        "MD JIBON SCREEN SCANNER",
                        26,
                        true
                );

        title.setTextColor(
                Color.WHITE
        );

        root.addView(title);

        TextView subtitle =
                text(
                        "20,000 MARKET LOGIC CHECKS",
                        14,
                        false
                );

        subtitle.setTextColor(
                Color.rgb(
                        80,
                        190,
                        255
                )
        );

        root.addView(subtitle);

        // --------------------------------------------------------
        // MARKET + TIMEFRAME
        // --------------------------------------------------------

        LinearLayout selectors =
                new LinearLayout(this);

        selectors.setGravity(
                Gravity.CENTER_VERTICAL
        );

        marketSpinner =
                spinner(MARKETS);

        timeframeSpinner =
                spinner(TIMEFRAMES);

        selectors.addView(
                marketSpinner,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                )
        );

        LinearLayout.LayoutParams timeframeParams =
                new LinearLayout.LayoutParams(
                        dp(125),
                        dp(50)
                );

        timeframeParams.leftMargin =
                dp(8);

        selectors.addView(
                timeframeSpinner,
                timeframeParams
        );

        root.addView(
                selectors
        );

        marketSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView
                        .OnItemSelectedListener() {

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {}

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        saveSettings();
                    }
                }
        );

        timeframeSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView
                        .OnItemSelectedListener() {

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {}

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        saveSettings();
                    }
                }
        );

        // --------------------------------------------------------
        // INFORMATION
        // --------------------------------------------------------

        TextView info =
                text(
                        "OPEN A MARKET CHART FIRST.\n"
                                + "Then press the Floating Icon to scan.",
                        15,
                        false
                );

        info.setTextColor(
                Color.LTGRAY
        );

        info.setGravity(
                Gravity.CENTER
        );

        root.addView(info);

        // --------------------------------------------------------
        // SCREEN CAPTURE
        // --------------------------------------------------------

        Button capture =
                button(
                        "ENABLE SCREEN CAPTURE",
                        Color.rgb(
                                20,
                                115,
                                220
                        )
                );

        capture.setOnClickListener(
                v ->
                        requestScreenCapture()
        );

        root.addView(
                capture
        );

        // --------------------------------------------------------
        // UPLOAD SCREENSHOT
        // --------------------------------------------------------

        Button upload =
                button(
                        "UPLOAD MARKET SCREENSHOT",
                        Color.rgb(
                                25,
                                100,
                                190
                        )
                );

        upload.setOnClickListener(
                v ->
                        pickImage()
        );

        root.addView(
                upload
        );

        // --------------------------------------------------------
        // ANALYZE BUTTON
        // --------------------------------------------------------

        Button analyze =
                button(
                        "ANALYZE • 20,000 CHECKS",
                        Color.rgb(
                                0,
                                170,
                                95
                        )
                );

        analyze.setOnClickListener(
                v ->
                        analyzeSelected()
        );

        root.addView(
                analyze
        );

        // --------------------------------------------------------
        // FLOATING SCANNER
        // --------------------------------------------------------

        TextView floatingTitle =
                text(
                        "FLOATING SCANNER",
                        19,
                        true
                );

        floatingTitle.setGravity(
                Gravity.CENTER
        );

        floatingTitle.setTextColor(
                Color.WHITE
        );

        root.addView(
                floatingTitle
        );

        floatingSwitch =
                new Switch(this);

        floatingSwitch.setText(
                "Floating Icon ON / OFF"
        );

        floatingSwitch.setTextColor(
                Color.WHITE
        );

        floatingSwitch.setTextSize(
                16
        );

        floatingSwitch.setOnCheckedChangeListener(
                (button, checked) -> {

                    if (checked) {

                        enableFloating();

                    } else {

                        stopFloating();
                    }
                }
        );

        root.addView(
                floatingSwitch
        );

        // --------------------------------------------------------
        // STATUS
        // --------------------------------------------------------

        status =
                text(
                        "READY • OPEN MARKET CHART",
                        15,
                        false
                );

        status.setTextColor(
                Color.LTGRAY
        );

        status.setGravity(
                Gravity.CENTER
        );

        root.addView(
                status
        );

        // --------------------------------------------------------
        // RESULT
        // --------------------------------------------------------

        resultText =
                text(
                        "ANALYSIS RESULT\n\n"
                                + "No result yet.\n\n"
                                + "The scanner will only return UP or DOWN "
                                + "when strong market evidence is detected.",
                        16,
                        false
                );

        resultText.setTextColor(
                Color.WHITE
        );

        resultText.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        root.addView(
                resultText,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(420)
                )
        );

        TextView note =
                text(
                        "20,000 evidence checks • Real candles only • "
                                + "No synthetic candles • Evidence score is not guaranteed win rate.",
                        12,
                        false
                );

        note.setTextColor(
                Color.GRAY
        );

        root.addView(note);

        setContentView(
                scroll
        );
    }

    // ============================================================
    // SCREEN CAPTURE
    // ============================================================

    private void requestScreenCapture() {

        MediaProjectionManager manager =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        try {

            startActivityForResult(
                    manager.createScreenCaptureIntent(),
                    REQUEST_CAPTURE
            );

        } catch (Exception e) {

            toast(
                    "Screen capture request failed"
            );
        }
    }

    @Override
    protected void onActivityResult(
            int request,
            int result,
            Intent data
    ) {

        super.onActivityResult(
                request,
                result,
                data
        );

        if (
                request == REQUEST_CAPTURE
                        && result == RESULT_OK
                        && data != null
        ) {

            Intent service =
                    new Intent(
                            this,
                            ScreenCaptureService.class
                    );

            service.setAction(
                    ScreenCaptureService
                            .ACTION_START_CAPTURE
            );

            service.putExtra(
                    "resultCode",
                    result
            );

            service.putExtra(
                    "data",
                    data
            );

            startServiceCompat(
                    service
            );

            status.setText(
                    "SCREEN CAPTURE READY • OPEN MARKET"
            );

            if (
                    floatingSwitch != null
            ) {

                floatingSwitch.setChecked(
                        true
                );
            }

        } else if (
                request == REQUEST_CAPTURE
        ) {

            toast(
                    "Screen capture permission cancelled"
            );
        }
    }

    // ============================================================
    // FLOATING
    // ============================================================

    private void enableFloating() {

        if (
                !Settings.canDrawOverlays(
                        this
                )
        ) {

            toast(
                    "Allow Display over other apps"
            );

            try {

                startActivity(
                        new Intent(
                                Settings
                                        .ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse(
                                        "package:"
                                                + getPackageName()
                                )
                        )
                );

            } catch (Exception ignored) {}

            floatingSwitch.setChecked(
                    false
            );

            return;
        }

        if (
                !ScreenCaptureService
                        .isCaptureActive()
        ) {

            requestScreenCapture();

            floatingSwitch.setChecked(
                    false
            );

            return;
        }

        startFloatingService();
    }

    private void startFloatingService() {

        Intent intent =
                new Intent(
                        this,
                        FloatingScannerService.class
                );

        startServiceCompat(
                intent
        );

        status.setText(
                "FLOATING ICON READY • OPEN MARKET"
        );
    }

    private void stopFloating() {

        stopService(
                new Intent(
                        this,
                        FloatingScannerService.class
                )
        );

        status.setText(
                "FLOATING SCANNER OFF"
        );
    }

    // ============================================================
    // UPLOAD
    // ============================================================

    private void pickImage() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.setType(
                "image/*"
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        try {

            startActivityForResult(
                    intent,
                    PICK_IMAGE
            );

        } catch (Exception e) {

            intent.setAction(
                    Intent.ACTION_GET_CONTENT
            );

            startActivityForResult(
                    intent,
                    PICK_IMAGE
            );
        }
    }

    private void loadImage(
            Uri uri
    ) {

        status.setText(
                "LOADING MARKET SCREEN..."
        );

        executor.execute(
                () -> {

                    Bitmap bitmap = null;

                    try {

                        InputStream input =
                                getContentResolver()
                                        .openInputStream(
                                                uri
                                        );

                        bitmap =
                                BitmapFactory
                                        .decodeStream(
                                                input
                                        );

                        if (input != null) {
                            input.close();
                        }

                    } catch (Exception ignored) {}

                    final Bitmap result =
                            bitmap;

                    runOnUiThread(
                            () -> {

                                if (
                                        result == null
                                ) {

                                    toast(
                                            "Could not open image"
                                    );

                                    status.setText(
                                            "IMAGE ERROR"
                                    );

                                    return;
                                }

                                if (
                                        selectedBitmap != null
                                                && !selectedBitmap
                                                .isRecycled()
                                ) {

                                    selectedBitmap
                                            .recycle();
                                }

                                selectedBitmap =
                                        result;

                                status.setText(
                                        "MARKET SCREENSHOT READY"
                                );

                                analyzeSelected();
                            }
                    );
                }
        );
    }

    // ============================================================
    // MANUAL ANALYSIS
    // ============================================================

    private void analyzeSelected() {

        if (
                selectedBitmap == null
                        || selectedBitmap.isRecycled()
        ) {

            toast(
                    "UPLOAD MARKET SCREENSHOT FIRST"
            );

            return;
        }

        status.setText(
                "RUNNING 20,000 LOGIC CHECKS..."
        );

        final Bitmap copy =
                selectedBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                );

        final String market =
                selectedMarket();

        final String timeframe =
                selectedTimeframe();

        executor.execute(
                () -> {

                    Analyzer.Result result =
                            Analyzer.analyze(
                                    copy,
                                    timeframe
                            );

                    if (!copy.isRecycled()) {
                        copy.recycle();
                    }

                    runOnUiThread(
                            () ->
                                    showResult(
                                            result,
                                            market
                                    )
                    );
                }
        );
    }

    private void showResult(
            Analyzer.Result result,
            String market
    ) {

        StringBuilder text =
                new StringBuilder();

        text.append(
                "ANALYSIS RESULT\n\n"
        );

        if (
                result.strongSignal
                        && (
                        "UP".equals(
                                result.signal
                        )
                                || "DOWN".equals(
                                result.signal
                        )
                )
        ) {

            text.append(
                    result.signal
            );

            text.append(
                    "  |  "
            );

            text.append(
                    String.format(
                            Locale.US,
                            "%.0f%% EVIDENCE",
                            result.confidence
                    )
            );

        } else {

            text.append(
                    "NO STRONG SIGNAL"
            );
        }

        text.append(
                "\n\nMARKET: "
        );

        text.append(
                market
        );

        text.append(
                "\nTIMEFRAME: "
        );

        text.append(
                result.timeframe
        );

        text.append(
                "\n\nCHART DETECTED: "
        );

        text.append(
                result.chartDetected
                        ? "YES"
                        : "NO"
        );

        text.append(
                "\nREAL CANDLES: "
        );

        text.append(
                result.detectedCandles
        );

        text.append(
                "\nLOGIC CHECKS: "
        );

        text.append(
                result.evaluatedRules
        );

        text.append(
                "\nQUALITY: "
        );

        text.append(
                String.format(
                        Locale.US,
                        "%.1f%%",
                        result.quality
                )
        );

        text.append(
                "\n\nCURRENT CANDLE: "
        );

        text.append(
                result.currentCandleColor
        );

        text.append(
                "\nCURRENT BODY: "
        );

        text.append(
                String.format(
                        Locale.US,
                        "%.1f%%",
                        result.currentBodyRatio
                )
        );

        text.append(
                "\n\nNEXT CANDLE: "
        );

        text.append(
                result.nextCandleColor
        );

        text.append(
                "\nSIZE: "
        );

        text.append(
                result.nextCandleSize
        );

        text.append(
                "\nBODY: "
        );

        text.append(
                String.format(
                        Locale.US,
                        "%.1f%%",
                        result.nextBodyRatio
                )
        );

        text.append(
                "\n\nCHECKS\n"
        );

        for (
                String check :
                        result.checks
        ) {

            text.append(
                    "✓ "
            );

            text.append(
                    check
            );

            text.append(
                    "\n"
            );
        }

        resultText.setText(
                text.toString()
        );

        status.setText(
                result.strongSignal
                        ? "STRONG SIGNAL READY"
                        : "NO STRONG SIGNAL"
        );
    }

    // ============================================================
    // SETTINGS
    // ============================================================

    private String selectedMarket() {

        if (
                marketSpinner == null
        ) {

            return MARKETS[0];
        }

        return String.valueOf(
                marketSpinner.getSelectedItem()
        );
    }

    private String selectedTimeframe() {

        if (
                timeframeSpinner == null
        ) {

            return "1 MIN";
        }

        return String.valueOf(
                timeframeSpinner.getSelectedItem()
        );
    }

    private void saveSettings() {

        if (
                marketSpinner == null
                        || timeframeSpinner == null
        ) {
            return;
        }

        getSharedPreferences(
                PREF,
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        KEY_MARKET,
                        selectedMarket()
                )
                .putString(
                        KEY_TIMEFRAME,
                        selectedTimeframe()
                )
                .apply();
    }

    private void restoreSettings() {

        android.content.SharedPreferences preferences =
                getSharedPreferences(
                        PREF,
                        MODE_PRIVATE
                );

        String market =
                preferences.getString(
                        KEY_MARKET,
                        MARKETS[0]
                );

        String timeframe =
                preferences.getString(
                        KEY_TIMEFRAME,
                        TIMEFRAMES[0]
                );

        for (
                int i = 0;
                i < MARKETS.length;
                i++
        ) {

            if (
                    MARKETS[i].equals(
                            market
                    )
            ) {

                marketSpinner
                        .setSelection(i);

                break;
            }
        }

        for (
                int i = 0;
                i < TIMEFRAMES.length;
                i++
        ) {

            if (
                    TIMEFRAMES[i].equals(
                            timeframe
                    )
            ) {

                timeframeSpinner
                        .setSelection(i);

                break;
            }
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Spinner spinner(
            String[] values
    ) {

        Spinner spinner =
                new Spinner(this);

        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout
                                .simple_spinner_item,
                        values
                ) {

                    @Override
                    public View getView(
                            int position,
                            View convertView,
                            ViewGroup parent
                    ) {

                        TextView view =
                                (TextView)
                                        super.getView(
                                                position,
                                                convertView,
                                                parent
                                        );

                        view.setTextColor(
                                Color.WHITE
                        );

                        view.setTextSize(
                                15
                        );

                        view.setPadding(
                                dp(8),
                                0,
                                dp(8),
                                0
                        );

                        return view;
                    }
                };

        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );

        spinner.setAdapter(
                adapter
        );

        return spinner;
    }

    private Button button(
            String label,
            int color
    ) {

        Button button =
                new Button(this);

        button.setText(
                label
        );

        button.setTextColor(
                Color.WHITE
        );

        button.setTextSize(
                15
        );

        button.setAllCaps(
                false
        );

        button.setBackgroundColor(
                color
        );

        return button;
    }

    private TextView text(
            String label,
            float size,
            boolean bold
    ) {

        TextView view =
                new TextView(this);

        view.setText(
                label
        );

        view.setTextSize(
                size
        );

        if (bold) {

            view.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }

        view.setPadding(
                0,
                dp(8),
                0,
                dp(8)
        );

        return view;
    }

    private void startServiceCompat(
            Intent intent
    ) {

        if (Build.VERSION.SDK_INT >= 26) {

            startForegroundService(
                    intent
            );

        } else {

            startService(
                    intent
            );
        }
    }

    private int dp(
            int value
    ) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private void toast(
            String message
    ) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onDestroy() {

        executor.shutdownNow();

        if (
                selectedBitmap != null
                        && !selectedBitmap.isRecycled()
        ) {

            selectedBitmap.recycle();
        }

        super.onDestroy();
    }
}
