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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int CAPTURE_REQUEST = 501;
    private static final int NOTIFICATION_REQUEST = 502;

    private TextView statusText;
    private TextView infoText;

    private Button overlayButton;
    private Button captureButton;
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUI();
        registerScannerReceiver();
        requestNotificationPermissionIfNeeded();
    }

    private void registerScannerReceiver() {

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent
            ) {

                if (intent == null) {
                    return;
                }

                String action = intent.getAction();

                if (ScreenCaptureService.ACTION_RESULT.equals(action)) {

                    String signal =
                            intent.getStringExtra("signal");

                    int confidence =
                            intent.getIntExtra(
                                    "confidence",
                                    0
                            );

                    if (signal == null) {
                        signal = "NO TRADE";
                    }

                    if ("UP".equals(signal)) {

                        statusText.setText(
                                "SCAN COMPLETE • UP " +
                                        confidence +
                                        "%"
                        );

                        statusText.setTextColor(
                                Color.rgb(
                                        30,
                                        235,
                                        135
                                )
                        );

                    } else if ("DOWN".equals(signal)) {

                        statusText.setText(
                                "SCAN COMPLETE • DOWN " +
                                        confidence +
                                        "%"
                        );

                        statusText.setTextColor(
                                Color.rgb(
                                        255,
                                        70,
                                        85
                                )
                        );

                    } else {

                        statusText.setText(
                                "SCAN COMPLETE • WAIT"
                        );

                        statusText.setTextColor(
                                Color.WHITE
                        );
                    }
                }

                if (ScreenCaptureService.ACTION_CAPTURE_STATE
                        .equals(action)) {

                    boolean active =
                            intent.getBooleanExtra(
                                    "active",
                                    false
                            );

                    updateCaptureButton(active);
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

        root.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        root.setPadding(
                28,
                40,
                28,
                30
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

        title.setGravity(
                Gravity.CENTER
        );

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        TextView subtitle =
                text(
                        "SCREEN SCANNER",
                        17,
                        Color.WHITE
                );

        subtitle.setGravity(
                Gravity.CENTER
        );

        root.addView(
                subtitle,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        statusText =
                text(
                        "READY",
                        16,
                        Color.LTGRAY
                );

        statusText.setGravity(
                Gravity.CENTER
        );

        statusText.setPadding(
                0,
                25,
                0,
                25
        );

        root.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        overlayButton =
                new Button(this);

        overlayButton.setText(
                "FLOATING SCANNER ON"
        );

        overlayButton.setAllCaps(false);

        overlayButton.setOnClickListener(
                v -> toggleOverlay()
        );

        LinearLayout.LayoutParams overlayParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        overlayParams.setMargins(
                0,
                8,
                0,
                8
        );

        root.addView(
                overlayButton,
                overlayParams
        );

        captureButton =
                new Button(this);

        captureButton.setText(
                "SCREEN CAPTURE ON"
        );

        captureButton.setAllCaps(false);

        captureButton.setOnClickListener(
                v -> toggleCapture()
        );

        LinearLayout.LayoutParams captureParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        captureParams.setMargins(
                0,
                8,
                0,
                8
        );

        root.addView(
                captureButton,
                captureParams
        );

        timeframeButton =
                new Button(this);

        timeframeButton.setText(
                "TIMEFRAME: " +
                        getTimeframe()
        );

        timeframeButton.setAllCaps(false);

        timeframeButton.setOnClickListener(
                v -> cycleTimeframe()
        );

        LinearLayout.LayoutParams timeframeParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        timeframeParams.setMargins(
                0,
                8,
                0,
                8
        );

        root.addView(
                timeframeButton,
                timeframeParams
        );

        infoText =
                text(
                        "Quotex/Cortex chart খুলে\n" +
                                "Floating Scanner icon-এ চাপুন।\n\n" +
                                "Icon drag করে যেকোনো জায়গায় নেওয়া যাবে।",
                        14,
                        Color.LTGRAY
                );

        infoText.setGravity(
                Gravity.CENTER
        );

        infoText.setPadding(
                0,
                30,
                0,
                20
        );

        root.addView(
                infoText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        TextView modeText =
                text(
                        "100 LOGIC CHECKS",
                        13,
                        Color.GRAY
                );

        modeText.setGravity(
                Gravity.CENTER
        );

        root.addView(
                modeText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

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

        t.setGravity(
                Gravity.CENTER_VERTICAL
        );

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

            Intent settingsIntent =
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                    "package:" +
                                            getPackageName()
                            )
                    );

            startActivity(settingsIntent);

            Toast.makeText(
                    this,
                    "Overlay permission দিন",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent serviceIntent =
                new Intent(
                        this,
                        FloatingScannerService.class
                );

        if (FloatingScannerService.isRunning()) {

            try {

                stopService(
                        serviceIntent
                );

            } catch (Exception ignored) {
            }

            overlayButton.setText(
                    "FLOATING SCANNER ON"
            );

            statusText.setText(
                    "FLOATING SCANNER OFF"
            );

            statusText.setTextColor(
                    Color.LTGRAY
            );

        } else {

            try {

                if (Build.VERSION.SDK_INT >= 26) {

                    startForegroundService(
                            serviceIntent
                    );

                } else {

                    startService(
                            serviceIntent
                    );
                }

                overlayButton.setText(
                        "FLOATING SCANNER OFF"
                );

                statusText.setText(
                        "FLOATING SCANNER ON"
                );

                statusText.setTextColor(
                        Color.rgb(
                                40,
                                220,
                                255
                        )
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

            Intent stopIntent =
                    new Intent(
                            this,
                            ScreenCaptureService.class
                    );

            stopIntent.setAction(
                    ScreenCaptureService.ACTION_STOP
            );

            try {

                startService(
                        stopIntent
                );

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

        statusText.setText(
                "SCREEN CAPTURE PERMISSION..."
        );

        statusText.setTextColor(
                Color.LTGRAY
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
                    "SCREEN CAPTURE ON ✓"
            );

            statusText.setText(
                    "SCREEN CAPTURE READY"
            );

            statusText.setTextColor(
                    Color.rgb(
                            30,
                            235,
                            135
                    )
            );

        } else {

            captureButton.setText(
                    "SCREEN CAPTURE ON"
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

        for (
                int i = 0;
                i < TIMEFRAMES.length;
                i++
        ) {

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
                "TIMEFRAME: " +
                        next
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

            statusText.setText(
                    "SCREEN CAPTURE CANCELLED"
            );

            statusText.setTextColor(
                    Color.rgb(
                            255,
                            70,
                            85
                    )
            );

            Toast.makeText(
                    this,
                    "Screen Capture অনুমতি দেওয়া হয়নি",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent serviceIntent =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        serviceIntent.putExtra(
                "resultCode",
                resultCode
        );

        serviceIntent.putExtra(
                "data",
                data
        );

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                startForegroundService(
                        serviceIntent
                );

            } else {

                startService(
                        serviceIntent
                );
            }

            statusText.setText(
                    "STARTING SCREEN CAPTURE..."
            );

            statusText.setTextColor(
                    Color.LTGRAY
            );

        } catch (Exception e) {

            statusText.setText(
                    "SCREEN CAPTURE START FAILED"
            );

            statusText.setTextColor(
                    Color.rgb(
                            255,
                            70,
                            85
                    )
            );
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (Settings.canDrawOverlays(this)) {

            if (FloatingScannerService.isRunning()) {

                overlayButton.setText(
                        "FLOATING SCANNER OFF"
                );

            } else {

                overlayButton.setText(
                        "FLOATING SCANNER ON"
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
