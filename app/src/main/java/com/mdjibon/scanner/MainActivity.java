package com.mdjibon.scanner;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {

    static final int CAP = 101;

    TextView result, info;
    BroadcastReceiver rx;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(28, 40, 28, 28);
        l.setBackgroundColor(Color.rgb(16, 20, 20));

        TextView title = t(
                "MD JIBON 99%",
                26,
                Color.rgb(24, 227, 138)
        );
        l.addView(title);

        TextView market = t(
                "USD/BRL OTC • LIVE",
                16,
                Color.WHITE
        );
        l.addView(market);

        info = t(
                "প্রথমে Overlay ও Screen Capture permission দিন। তারপর Quotex খুলে ভাসমান ⚡ আইকনে চাপুন।",
                16,
                Color.LTGRAY
        );
        l.addView(info);

        Button overlay = new Button(this);
        overlay.setText("1. Enable Floating Scanner");
        overlay.setOnClickListener(v -> enableOverlay());
        l.addView(overlay);

        Button capture = new Button(this);
        capture.setText("2. Enable Screen Capture");
        capture.setOnClickListener(v -> requestCapture());
        l.addView(capture);

        result = t(
                "WAIT\nConfidence: --%",
                28,
                Color.WHITE
        );
        result.setGravity(Gravity.CENTER);
        result.setPadding(10, 60, 10, 60);

        l.addView(
                result,
                new LinearLayout.LayoutParams(-1, -2)
        );

        setContentView(l);

        rx = new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {

                String s = i.getStringExtra("signal");
                int conf = i.getIntExtra("confidence", 0);
                int q = i.getIntExtra("quality", 0);
                int n = i.getIntExtra("ruleCount", 0);
                int candles = i.getIntExtra("detectedCandles", 0);

                result.setText(
                        s +
                        "\nConfidence: " + conf + "%" +
                        "\n100 Logic Checks: " + n +
                        "\nDetected candles: " + candles +
                        "\nFrame quality: " + q + "%"
                );

                result.setTextColor(
                        "CALL".equals(s)
                                ? Color.rgb(24, 227, 138)
                                : "PUT".equals(s)
                                ? Color.rgb(255, 77, 94)
                                : Color.WHITE
                );
            }
        };

        registerReceiver(
                rx,
                new IntentFilter(ScreenCaptureService.ACTION_RESULT),
                Context.RECEIVER_NOT_EXPORTED
        );
    }

    TextView t(String s, float z, int c) {

        TextView v = new TextView(this);

        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(c);
        v.setPadding(0, 12, 0, 12);

        return v;
    }

    void enableOverlay() {

        if (!Settings.canDrawOverlays(this)) {

            startActivity(
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                    "package:" + getPackageName()
                            )
                    )
            );

            return;
        }

        // ⭐ এখানে Floating Scanner Service চালু হবে
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
                "⚡ Floating Scanner চালু হয়েছে",
                Toast.LENGTH_SHORT
        ).show();
    }

    void requestCapture() {

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
            int r,
            int c,
            Intent d
    ) {

        super.onActivityResult(r, c, d);

        if (r == CAP && c == RESULT_OK) {

            Intent s = new Intent(
                    this,
                    ScreenCaptureService.class
            );

            s.putExtra("code", c);
            s.putExtra("data", d);

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
        }
    }

    @Override
    protected void onDestroy() {

        if (rx != null) {
            unregisterReceiver(rx);
        }

        super.onDestroy();
    }
         }
