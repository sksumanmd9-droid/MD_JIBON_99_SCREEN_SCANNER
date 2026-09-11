package com.mdjibon.scanner;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int CAPTURE_REQUEST = 501;
    private static final int NOTIFICATION_REQUEST = 502;

    private TextView status;
    private TextView result;

    private Button captureButton;
    private Button overlayButton;
    private Button timeframeButton;

    private BroadcastReceiver receiver;

    private final String[] TIMEFRAMES = {
            "1 MIN",
            "30 SEC",
            "15 SEC",
            "10 SEC",
            "5 SEC"
    };

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        buildUI();
        registerScannerReceiver();
        requestNotificationPermissionIfNeeded();
    }

    private void registerScannerReceiver() {

        receiver =
                new BroadcastReceiver() {

                    @Override
                    public void onReceive(
                            Context context,
                            Intent intent
                    ) {

                        if (intent == null) {
                            return;
                        }

                        String action =
                                intent.getAction();

                        if (ScreenCaptureService
                                .ACTION_RESULT
                                .equals(action)) {

                            String signal =
                                    intent.getStringExtra(
                                            "signal"
                                    );

                            double confidence =
                                    intent.getDoubleExtra(
                                            "confidence",
                                            0.0
                                    );

                            double quality =
                                    intent.getDoubleExtra(
                                            "quality",
                                            0.0
                                    );

                            int rules =
                                    intent.getIntExtra(
                                            "ruleCount",
                                            0
                                    );

                            int candles =
                                    intent.getIntExtra(
                                            "detectedCandles",
                                            0
                                    );

                            String timeframe =
                                    intent.getStringExtra(
                                            "timeframe"
                                    );

                            String candleSize =
                                    intent.getStringExtra(
                                            "candleSize"
                                    );

                            if (signal == null) {
                                signal = "NO TRADE";
                            }

                            if (!"UP".equals(signal) &&
                                    !"DOWN".equals(signal) &&
                                    !"NO TRADE".equals(signal)) {

                                signal =
                                        "NO TRADE";
                            }

                            if (timeframe == null) {
                                timeframe =
                                        getTimeframe();
                            }

                            if (candleSize == null) {
                                candleSize =
                                        "SCREEN CHART";
                            }

                            result.setText(
                                    signal +
                                            "\nScore: " +
                                            confidence +
                                            "%" +
                                            "\n" +
                                            timeframe +
                                            " • " +
                                            candleSize +
                                            "\n100 Logic Checks: " +
                                            rules +
                                            "/100" +
                                            "\nCandles: " +
                                            candles +
                                            "\nFrame Quality: " +
                                            quality +
                                            "%"
                            );

                            if ("UP".equals(signal)) {

                                result.setTextColor(
                                        Color.rgb(
                                                30,
                                                235,
                                                135
                                        )
                                );

                            } else if ("DOWN".equals(signal)) {

                                result.setTextColor(
                                        Color.rgb(
                                                255,
                                                70,
                                                85
                                        )
                                );

                            } else {

                                result.setTextColor(
                                        Color.WHITE
                                );
                            }

                            status.setText(
                                    "SCAN COMPLETE • " +
                                            signal
                            );
                        }

                        if (ScreenCaptureService
                                .ACTION_CAPTURE_STATE
                                .equals(action)) {

                            boolean active =
                                    intent.getBooleanExtra(
                                            "active",
                                            false
                                    );

                            updateCaptureButton(
                                    active
                            );
                        }
                    }
                };

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                ScreenCaptureService.ACTION_RESULT
        );

        filter.addAction(
                ScreenCaptureService.ACTION_CAPTURE_STATE
        );

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    receiver,
                    filter
            );
        }
    }

    private void requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    NOTIFICATION_REQUEST
            );
        }
    }

    private void buildUI() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                28,
                35,
                28,
                28
        );

        root.setBackgroundColor(
                Color.rgb(
                        8,
                        12,
                        20
                )
        );

        TextView title =
                text(
                        "MD JIBON",
                        30,
                        Color.rgb(
                                40,
                                220,
                                255
                        )
                );

        root.addView(title);

        TextView subtitle =
                text(
                        "SCREEN SCANNER",
                        18,
                        Color.WHITE
                );

        root.addView(subtitle);

        status =
                text(
                        "READY",
                        16,
                        Color.LTGRAY
                );

        status.setPadding(
                0,
                15,
                0,
                20
        );

        root.addView(status);

        overlayButton =
                new Button(this);

        overlayButton.setText(
                "1. FLOATING SCANNER ON"
        );

        overlayButton.setOnClickListener(
                v -> toggleOverlay()
        );

        root.addView(
                overlayButton,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        captureButton =
                new Button(this);

        captureButton.setText(
                "2. SCREEN CAPTURE ON"
        );

        captureButton.setOnClickListener(
                v -> toggleCapture()
        );

        root.addView(
                captureButton,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        timeframeButton =
                new Button(this);

        timeframeButton.setText(
                "TIMEFRAME: " +
                        getTimeframe()
        );

        timeframeButton.setOnClickListener(
                v -> cycleTimeframe()
        );

        root.addView(
                timeframeButton,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        TextView help =
                text(
                        "Cortex/Quotex খুলে Floating Scanner logo-তে চাপুন.\n" +
                                "Logo drag করে যেকোনো জায়গায় নেওয়া যাবে.",
                        15,
                        Color.LTGRAY
                );

        help.setPadding(
                0,
                25,
                0,
                10
        );

        root.addView(help);

        result =
                text(
                        "WAIT\nScore: --%",
                        25,
                        Color.WHITE
                );

        result.setGravity(
                Gravity.CENTER
        );

        result.setPadding(
                10,
                45,
                10,
                45
        );

        root.addView(
                result,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        TextView note =
                text(
                        "100 deterministic technical checks • " +
                                "screen-chart rule agreement",
                        13,
                        Color.GRAY
                );

        note.setGravity(
                Gravity.CENTER
        );

        root.addView(note);

        setContentView(root);
    }

    private TextView text(
            String value,
            float size,
            int color
    ) {

        TextView t =
                new TextView(this);

        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);

        t.setPadding(
                0,
                8,
                0,
                8
        );

        return t;
    }

    private void toggleOverlay() {

        if (!Settings.canDrawOverlays(this)) {

            Intent i =
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                    "package:" +
                                            getPackageName()
                            )
                    );

            startActivity(i);

            Toast.makeText(
                    this,
                    "Overlay permission দিন",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent service =
                new Intent(
                        this,
                        FloatingScannerService.class
                );

        if (FloatingScannerService.isRunning()) {

            stopService(service);

            overlayButton.setText(
                    "1. FLOATING SCANNER ON"
            );

            status.setText(
                    "FLOATING SCANNER OFF"
            );

        } else {

            try {

                if (Build.VERSION.SDK_INT >= 26) {

                    startForegroundService(
                            service
                    );

                } else {

                    startService(service);
                }

                overlayButton.setText(
                        "1. FLOATING SCANNER OFF"
                );

                status.setText(
                        "FLOATING SCANNER ON"
                );

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "Floating Scanner চালু করা যায়নি",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void toggleCapture() {

        if (ScreenCaptureService.isCaptureActive()) {

            Intent stop =
                    new Intent(
                            this,
                            ScreenCaptureService.class
                    );

            stop.setAction(
                    ScreenCaptureService.ACTION_STOP
            );

            try {

                startService(stop);

            } catch (Exception ignored) {
            }

            return;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        if (manager == null) {

            Toast.makeText(
                    this,
                    "Screen Capture unavailable",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        status.setText(
                "SCREEN CAPTURE PERMISSION..."
        );

        startActivityForResult(
                manager.createScreenCaptureIntent(),
                CAPTURE_REQUEST
        );
    }

    private void updateCaptureButton(
            boolean active
    ) {

        if (captureButton == null) {
            return;
        }

        if (active) {

            captureButton.setText(
                    "2. SCREEN CAPTURE ON ✓"
            );

            status.setText(
                    "SCREEN CAPTURE READY"
            );

        } else {

            captureButton.setText(
                    "2. SCREEN CAPTURE ON"
            );
        }
    }

    private String getTimeframe() {

        return getSharedPreferences(
                "scanner_settings",
                MODE_PRIVATE
        ).getString(
                "timeframe",
                "1 MIN"
        );
    }

    private void cycleTimeframe() {

        String current =
                getTimeframe();

        int index = 0;

        for (int i = 0;
             i < TIMEFRAMES.length;
             i++) {

            if (TIMEFRAMES[i].equals(current)) {

                index = i;
                break;
            }
        }

        index++;

        if (index >= TIMEFRAMES.length) {
            index = 0;
        }

        String next =
                TIMEFRAMES[index];

        getSharedPreferences(
                "scanner_settings",
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        "timeframe",
                        next
                )
                .apply();

        timeframeButton.setText(
                "TIMEFRAME: " + next
        );

        Toast.makeText(
                this,
                "Timeframe: " + next,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != CAPTURE_REQUEST) {
            return;
        }

        if (resultCode != RESULT_OK ||
                data == null) {

            status.setText(
                    "SCREEN CAPTURE CANCELLED"
            );

            Toast.makeText(
                    this,
                    "Screen Capture অনুমতি দেওয়া হয়নি",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent service =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        service.putExtra(
                "resultCode",
                resultCode
        );

        service.putExtra(
                "data",
                data
        );

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                startForegroundService(
                        service
                );

            } else {

                startService(service);
            }

            status.setText(
                    "STARTING SCREEN CAPTURE..."
            );

        } catch (Exception e) {

            status.setText(
                    "SCREEN CAPTURE START FAILED"
            );
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (Settings.canDrawOverlays(this)) {

            if (FloatingScannerService.isRunning()) {

                overlayButton.setText(
                        "1. FLOATING SCANNER OFF"
                );

            } else {

                overlayButton.setText(
                        "1. FLOATING SCANNER ON"
                );
            }
        }

        updateCaptureButton(
                ScreenCaptureService.isCaptureActive()
        );

        if (timeframeButton != null) {

            timeframeButton.setText(
                    "TIMEFRAME: " +
                            getTimeframe()
            );
        }
    }

    @Override
    protected void onDestroy() {

        if (receiver != null) {

            try {
                unregisterReceiver(
                        receiver
                );
            } catch (Exception ignored) {
            }

            receiver = null;
        }

        super.onDestroy();
    }
}
