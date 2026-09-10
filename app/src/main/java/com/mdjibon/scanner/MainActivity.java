package com.mdjibon.scanner;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    private static final int CAP = 101;

    private TextView result;
    private TextView info;
    private Button overlayButton;
    private Button captureButton;
    private BroadcastReceiver rx;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(28, 40, 28, 28);
        l.setBackgroundColor(Color.rgb(8, 12, 18));

        TextView title = t(
                "MD JIBON 99%",
                26,
                Color.rgb(24, 227, 138)
        );
        l.addView(title);

        TextView market = t(
                "USD/BRL OTC • LIVE SCANNER",
                16,
                Color.WHITE
        );
        l.addView(market);

        info = t(
                "Overlay চালু করুন, Screen Capture অনুমতি দিন, তারপর Quotex খুলে Floating Scanner চাপুন।",
                15,
                Color.LTGRAY
        );
        l.addView(info);

        overlayButton = new Button(this);
        overlayButton.setText("1. Enable Floating Scanner");
        overlayButton.setOnClickListener(v -> toggleOverlay());
        l.addView(overlayButton);

        captureButton = new Button(this);
        captureButton.setText("2. Enable Screen Capture");
        captureButton.setOnClickListener(v -> requestCapture());
        l.addView(captureButton);

        result = t(
                "WAIT\nConfidence: --%",
                25,
                Color.WHITE
        );
        result.setGravity(Gravity.CENTER);
        result.setPadding(10, 55, 10, 55);

        l.addView(
                result,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        setContentView(l);

        updateButtons();

        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {

                String s = i.getStringExtra("signal");
                int conf = i.getIntExtra("confidence", 0);
                int q = i.getIntExtra("quality", 0);
                int n = i.getIntExtra("ruleCount", 0);
                int candles = i.getIntExtra("detectedCandles", 0);

                if (s == null) {
                    s = "NO TRADE";
                }

                result.setText(
                        s +
                        "\nConfidence: " + conf + "%" +
                        "\n100 Logic Checks: " + n +
                        "\nDetected candles: " + candles +
                        "\nFrame quality: " + q + "%"
                );

                if ("UP".equals(s)) {
                    result.setTextColor(
                            Color.rgb(30, 235, 90)
                    );
                } else if ("DOWN".equals(s)) {
                    result.setTextColor(
                            Color.rgb(255, 55, 65)
                    );
                } else {
                    result.setTextColor(Color.WHITE);
                }
            }
        };

        IntentFilter filter =
                new IntentFilter(
                        ScreenCaptureService.ACTION_RESULT
                );

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    rx,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(rx, filter);
        }
    }

    private TextView t(
            String s,
            float z,
            int c
    ) {

        TextView v = new TextView(this);

        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(c);
        v.setPadding(0, 12, 0, 12);

        return v;
    }

    private void toggleOverlay() {

        if (!Settings.canDrawOverlays(this)) {

            Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(
                            "package:" + getPackageName()
                    )
            );

            startActivity(i);
            return;
        }

        if (FloatingScannerService.isRunning()) {

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

            Intent service = new Intent(
                    this,
                    FloatingScannerService.class
            );

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }

            Toast.makeText(
                    this,
                    "Floating Scanner ON",
                    Toast.LENGTH_SHORT
            ).show();
        }

        updateButtons();
    }

    private void updateButtons() {

        if (overlayButton == null) {
            return;
        }

        if (FloatingScannerService.isRunning()) {
            overlayButton.setText(
                    "Floating Scanner: ON  ✓"
            );
        } else {
            overlayButton.setText(
                    "1. Enable Floating Scanner"
            );
        }

        if (ScreenCaptureService.isCaptureActive()) {
            captureButton.setText(
                    "Screen Capture: ON  ✓"
            );
        } else {
            captureButton.setText(
                    "2. Enable Screen Capture"
            );
        }
    }

    private void requestCapture() {

        if (ScreenCaptureService.isCaptureActive()) {

            Toast.makeText(
                    this,
                    "Screen Capture already চলছে",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        MediaProjectionManager m =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        startActivityForResult(
                m.createScreenCaptureIntent(),
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

        if (requestCode == CAP) {

            if (resultCode == RESULT_OK && data != null) {

                Intent s = new Intent(
                        this,
                        ScreenCaptureService.class
                );

                s.putExtra(
                        "code",
                        resultCode
                );

                s.putExtra(
                        "data",
                        data
                );

                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(s);
                } else {
                    startService(s);
                }

                Toast.makeText(
                        this,
                        "Screen Capture চালু হয়েছে",
                        Toast.LENGTH_SHORT
                ).show();

            } else {

                Toast.makeText(
                        this,
                        "Screen Capture permission দেওয়া হয়নি",
                        Toast.LENGTH_SHORT
                ).show();
            }

            updateButtons();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateButtons();

        if (Settings.canDrawOverlays(this)) {

            info.setText(
                    "সব ঠিক আছে। Quotex খুলে Floating Scanner চাপুন।"
            );
        }
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
