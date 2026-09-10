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

import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    public static final int CAP = 101;
    public static final int NOTIFICATION_REQ = 202;

    private TextView result;
    private TextView info;
    private Button overlayButton;
    private Button captureButton;

    private BroadcastReceiver rx;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        buildUI();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_REQ
            );
        }

        registerResultReceiver();

        updateButtons();
    }

    private void buildUI() {

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(28, 40, 28, 28);
        l.setBackgroundColor(Color.rgb(6, 10, 16));

        TextView title = text(
                "MD JIBON 99%",
                28,
                Color.rgb(24, 227, 138)
        );

        l.addView(title);

        TextView market = text(
                "USD/BRL OTC • LIVE SCANNER",
                17,
                Color.WHITE
        );

        l.addView(market);

        info = text(
                "Overlay ও Screen Capture চালু করুন। তারপর Quotex খুলে Floating Scanner চাপুন।",
                15,
                Color.LTGRAY
        );

        l.addView(info);

        overlayButton = new Button(this);
        overlayButton.setText("1. ENABLE FLOATING SCANNER");
        overlayButton.setOnClickListener(v -> toggleOverlay());

        l.addView(
                overlayButton,
                new LinearLayout.LayoutParams(
                        -1,
                        65
                )
        );

        captureButton = new Button(this);
        captureButton.setText("2. ENABLE SCREEN CAPTURE");
        captureButton.setOnClickListener(v -> enableCapture());

        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(-1, 65);

        cp.topMargin = 18;

        l.addView(captureButton, cp);

        result = text(
                "WAIT\nConfidence: --%",
                27,
                Color.WHITE
        );

        result.setGravity(Gravity.CENTER);
        result.setPadding(10, 70, 10, 70);

        l.addView(
                result,
                new LinearLayout.LayoutParams(-1, -2)
        );

        setContentView(l);
    }

    private TextView text(
            String s,
            float size,
            int color
    ) {

        TextView t = new TextView(this);

        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 12, 0, 12);

        return t;
    }

    private void toggleOverlay() {

        if (!Settings.canDrawOverlays(this)) {

            Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );

            startActivity(i);

            Toast.makeText(
                    this,
                    "Overlay permission দিন",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (FloatingScannerService.isRunning) {

            stopService(
                    new Intent(
                            this,
                            FloatingScannerService.class
                    )
            );

            Toast.makeText(
                    this,
                    "Floating Scanner OFF",
                    Toast.LENGTH_SHORT
            ).show();

        } else {

            Intent s = new Intent(
                    this,
                    FloatingScannerService.class
            );

            ContextCompat.startForegroundService(
                    this,
                    s
            );

            Toast.makeText(
                    this,
                    "Floating Scanner ON",
                    Toast.LENGTH_SHORT
            ).show();
        }

        updateButtons();
    }

    private void enableCapture() {

        if (ScreenCaptureService.isCaptureRunning()) {

            Toast.makeText(
                    this,
                    "Screen Capture ইতিমধ্যে চালু আছে",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        MediaProjectionManager m =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        Intent permission =
                m.createScreenCaptureIntent();

        startActivityForResult(
                permission,
                CAP
        );
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

        if (requestCode != CAP)
            return;

        if (resultCode != RESULT_OK || data == null) {

            Toast.makeText(
                    this,
                    "Screen Capture permission দেওয়া হয়নি",
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
                "code",
                resultCode
        );

        service.putExtra(
                "data",
                data
        );

        ContextCompat.startForegroundService(
                this,
                service
        );

        Toast.makeText(
                this,
                "Screen Capture চালু হয়েছে",
                Toast.LENGTH_SHORT
        ).show();

        updateButtons();
    }

    private void registerResultReceiver() {

        rx = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context c,
                    Intent i
            ) {

                String signal =
                        i.getStringExtra("signal");

                int conf =
                        i.getIntExtra(
                                "confidence",
                                0
                        );

                int quality =
                        i.getIntExtra(
                                "quality",
                                0
                        );

                int rules =
                        i.getIntExtra(
                                "ruleCount",
                                0
                        );

                int candles =
                        i.getIntExtra(
                                "detectedCandles",
                                0
                        );

                if (signal == null)
                    signal = "NO TRADE";

                StringBuilder s =
                        new StringBuilder();

                s.append(signal)
                        .append("\n");

                s.append("Confidence: ")
                        .append(conf)
                        .append("%\n");

                s.append("100 Logic Checks: ")
                        .append(rules)
                        .append("\n");

                s.append("Detected Candles: ")
                        .append(candles)
                        .append("\n");

                s.append("Frame Quality: ")
                        .append(quality)
                        .append("%");

                result.setText(s.toString());

                if ("UP".equals(signal)) {

                    result.setTextColor(
                            Color.rgb(
                                    40,
                                    230,
                                    70
                            )
                    );

                } else if ("DOWN".equals(signal)) {

                    result.setTextColor(
                            Color.rgb(
                                    255,
                                    60,
                                    60
                            )
                    );

                } else {

                    result.setTextColor(
                            Color.WHITE
                    );
                }
            }
        };

        IntentFilter f =
                new IntentFilter(
                        ScreenCaptureService.ACTION_RESULT
                );

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    rx,
                    f,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    rx,
                    f
            );
        }
    }

    private void updateButtons() {

        if (overlayButton == null)
            return;

        if (FloatingScannerService.isRunning) {

            overlayButton.setText(
                    "1. FLOATING SCANNER — ON"
            );

        } else {

            overlayButton.setText(
                    "1. ENABLE FLOATING SCANNER"
            );
        }

        if (ScreenCaptureService.isCaptureRunning()) {

            captureButton.setText(
                    "2. SCREEN CAPTURE — ON"
            );

        } else {

            captureButton.setText(
                    "2. ENABLE SCREEN CAPTURE"
            );
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        updateButtons();
    }

    @Override
    protected void onDestroy() {

        if (rx != null) {

            try {
                unregisterReceiver(rx);
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }
}
