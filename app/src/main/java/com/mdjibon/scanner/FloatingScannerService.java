package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class FloatingScannerService extends Service {

    private static final String TAG = "MDJIBON";

    private static final String CHANNEL_ID =
            "md_jibon_floating_scanner";

    private static final int NOTIFICATION_ID = 9902;

    private static final String ACTION_SCAN =
            "com.mdjibon.scanner.ACTION_SCAN";

    private static final String ACTION_RESULT =
            "com.mdjibon.scanner.ACTION_RESULT";

    private static final String ACTION_PROGRESS =
            "com.mdjibon.scanner.ACTION_PROGRESS";

    private static final String EXTRA_SIGNAL =
            "signal";

    private static final String EXTRA_SCORE =
            "score";

    private WindowManager windowManager;

    private View logoView;
    private WindowManager.LayoutParams logoParams;

    private ScanView scanView;
    private WindowManager.LayoutParams scanParams;

    private ResultView resultView;
    private WindowManager.LayoutParams resultParams;

    private BroadcastReceiver receiver;

    private boolean running = false;
    private boolean scanning = false;

    private int savedX = 20;
    private int savedY = 250;

    private final Handler handler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);

        createReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        /*
         * IMPORTANT:
         * Floating service must become foreground immediately.
         */
        startFloatingForeground();

        running = true;

        if (!Settings.canDrawOverlays(this)) {

            Toast.makeText(
                    this,
                    "Overlay permission is required",
                    Toast.LENGTH_LONG
            ).show();

            try {
                Intent settingsIntent =
                        new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse(
                                        "package:" + getPackageName()
                                )
                        );

                settingsIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                );

                startActivity(settingsIntent);

            } catch (Exception ignored) {
            }

            stopSelf();
            return START_NOT_STICKY;
        }

        if (logoView == null) {
            createLogo();
        }

        return START_STICKY;
    }

    // ============================================================
    // FOREGROUND SERVICE
    // ============================================================

    private void startFloatingForeground() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    NOTIFICATION_SERVICE
                            );

            if (manager != null) {

                NotificationChannel channel =
                        new NotificationChannel(
                                CHANNEL_ID,
                                "MD JIBON Floating Scanner",
                                NotificationManager.IMPORTANCE_LOW
                        );

                channel.setDescription(
                        "Floating scanner service"
                );

                manager.createNotificationChannel(channel);
            }
        }

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder = new Notification.Builder(
                    this,
                    CHANNEL_ID
            );

        } else {

            builder = new Notification.Builder(this);
        }

        Notification notification =
                builder
                        .setContentTitle(
                                "MD JIBON Scanner"
                        )
                        .setContentText(
                                "Floating scanner is active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_view
                        )
                        .setOngoing(true)
                        .build();

        /*
         * Two-argument version is intentional.
         * The service type is declared in AndroidManifest.xml.
         */
        startForeground(
                NOTIFICATION_ID,
                notification
        );
    }

    // ============================================================
    // FLOATING LOGO
    // ============================================================

    private void createLogo() {

        if (logoView != null) {
            return;
        }

        logoView = new LogoView(this);

        int size = dp(64);

        logoParams =
                new WindowManager.LayoutParams();

        logoParams.width = size;
        logoParams.height = size;

        logoParams.gravity =
                Gravity.TOP | Gravity.START;

        logoParams.x = savedX;
        logoParams.y = savedY;

        logoParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            logoParams.type =
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        } else {

            logoParams.type =
                    WindowManager.LayoutParams.TYPE_PHONE;
        }

        logoParams.format =
                android.graphics.PixelFormat.TRANSLUCENT;

        try {

            windowManager.addView(
                    logoView,
                    logoParams
            );

        } catch (Exception e) {

            android.util.Log.e(
                    TAG,
                    "Floating logo addView failed",
                    e
            );

            logoView = null;
        }
    }

    private void removeLogo() {

        if (logoView != null) {

            try {
                windowManager.removeView(logoView);
            } catch (Exception ignored) {
            }

            logoView = null;
        }
    }

    // ============================================================
    // CLICK / DRAG
    // ============================================================

    private class LogoView extends View {

        private Bitmap logoBitmap;

        private float downX;
        private float downY;

        private int startX;
        private int startY;

        private long downTime;

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        public LogoView(Context context) {

            super(context);

            logoBitmap =
                    BitmapFactory.decodeResource(
                            getResources(),
                            R.drawable.md_jibon_logo
                    );

            setLayerType(
                    View.LAYER_TYPE_SOFTWARE,
                    null
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            if (logoBitmap != null) {

                RectF dst =
                        new RectF(
                                2,
                                2,
                                getWidth() - 2,
                                getHeight() - 2
                        );

                canvas.drawBitmap(
                        logoBitmap,
                        null,
                        dst,
                        paint
                );
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:

                    downX = event.getRawX();
                    downY = event.getRawY();

                    startX = logoParams.x;
                    startY = logoParams.y;

                    downTime =
                            System.currentTimeMillis();

                    return true;

                case MotionEvent.ACTION_MOVE:

                    float dx =
                            event.getRawX() - downX;

                    float dy =
                            event.getRawY() - downY;

                    logoParams.x =
                            startX + (int) dx;

                    logoParams.y =
                            startY + (int) dy;

                    try {

                        windowManager.updateViewLayout(
                                this,
                                logoParams
                        );

                    } catch (Exception ignored) {
                    }

                    return true;

                case MotionEvent.ACTION_UP:

                    long duration =
                            System.currentTimeMillis()
                                    - downTime;

                    float totalMove =
                            Math.abs(
                                    event.getRawX() - downX
                            )
                            +
                            Math.abs(
                                    event.getRawY() - downY
                            );

                    /*
                     * Small movement = click.
                     * Large movement = drag.
                     */
                    if (duration < 350
                            && totalMove < dp(12)) {

                        startScan();
                    }

                    return true;
            }

            return true;
        }
    }

    // ============================================================
    // START SCAN
    // ============================================================

    private void startScan() {

        if (scanning) {
            return;
        }

        if (!ScreenCaptureService.isCaptureActive()) {

            Toast.makeText(
                    this,
                    "Please turn ON Screen Capture first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        scanning = true;

        removeLogo();

        showScanView();

        Intent intent =
                new Intent(
                        this,
                        ScreenCaptureService.class
                );

        intent.setAction(
                ScreenCaptureService.ACTION_SCAN
        );

        try {

            startService(intent);

        } catch (Exception e) {

            scanning = false;

            removeScanView();
            createLogo();
        }
    }

    // ============================================================
    // SCAN VIEW
    // ============================================================

    private void showScanView() {

        scanView =
                new ScanView(this);

        scanParams =
                new WindowManager.LayoutParams();

        scanParams.width =
                WindowManager.LayoutParams.MATCH_PARENT;

        scanParams.height =
                WindowManager.LayoutParams.MATCH_PARENT;

        scanParams.gravity =
                Gravity.TOP | Gravity.START;

        scanParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            scanParams.type =
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        } else {

            scanParams.type =
                    WindowManager.LayoutParams.TYPE_PHONE;
        }

        scanParams.format =
                android.graphics.PixelFormat.TRANSLUCENT;

        try {

            windowManager.addView(
                    scanView,
                    scanParams
            );

        } catch (Exception e) {

            android.util.Log.e(
                    TAG,
                    "Scan view add failed",
                    e
            );
        }
    }

    private void removeScanView() {

        if (scanView != null) {

            try {
                windowManager.removeView(scanView);
            } catch (Exception ignored) {
            }

            scanView = null;
        }
    }

    // ============================================================
    // RESULT
    // ============================================================

    private void showResult(
            String signal,
            int score
    ) {

        removeScanView();

        scanning = false;

        createLogo();

        resultView =
                new ResultView(
                        this,
                        signal,
                        score
                );

        resultParams =
                new WindowManager.LayoutParams();

        resultParams.width =
                dp(190);

        resultParams.height =
                dp(82);

        resultParams.gravity =
                Gravity.TOP | Gravity.START;

        resultParams.x =
                logoParams != null
                        ? logoParams.x
                        : savedX;

        resultParams.y =
                (logoParams != null
                        ? logoParams.y
                        : savedY) + dp(70);

        resultParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            resultParams.type =
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        } else {

            resultParams.type =
                    WindowManager.LayoutParams.TYPE_PHONE;
        }

        resultParams.format =
                android.graphics.PixelFormat.TRANSLUCENT;

        try {

            windowManager.addView(
                    resultView,
                    resultParams
            );

        } catch (Exception e) {

            android.util.Log.e(
                    TAG,
                    "Result view add failed",
                    e
            );
        }
    }

    private void removeResult() {

        if (resultView != null) {

            try {
                windowManager.removeView(resultView);
            } catch (Exception ignored) {
            }

            resultView = null;
        }
    }

    // ============================================================
    // SCANNING ANIMATION
    // ============================================================

    private class ScanView extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap logoBitmap;

        private float lineY = 0;

        private float radarAngle = 0;

        private int progress = 0;

        public ScanView(Context context) {

            super(context);

            logoBitmap =
                    BitmapFactory.decodeResource(
                            getResources(),
                            R.drawable.md_jibon_logo
                    );

            paint.setTypeface(
                    Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                    )
            );

            post(animationRunnable);
        }

        private final Runnable animationRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        lineY += dp(8);

                        if (lineY > getHeight()) {
                            lineY = 0;
                        }

                        radarAngle += 7;

                        if (radarAngle >= 360) {
                            radarAngle = 0;
                        }

                        invalidate();

                        postDelayed(
                                this,
                                30
                        );
                    }
                };

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            int w = getWidth();
            int h = getHeight();

            // Transparent dark-blue overlay
            paint.setColor(
                    Color.argb(
                            55,
                            0,
                            90,
                            180
                    )
            );

            canvas.drawRect(
                    0,
                    0,
                    w,
                    h,
                    paint
            );

            // Scanning line
            paint.setColor(
                    Color.argb(
                            230,
                            25,
                            180,
                            255
                    )
            );

            paint.setStrokeWidth(
                    dp(3)
            );

            canvas.drawLine(
                    0,
                    lineY,
                    w,
                    lineY,
                    paint
            );

            // Radar
            float cx = w / 2f;
            float cy = h / 2f;

            float radius =
                    Math.min(w, h) * 0.20f;

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    dp(2)
            );

            paint.setColor(
                    Color.argb(
                            130,
                            25,
                            175,
                            255
                    )
            );

            canvas.drawCircle(
                    cx,
                    cy,
                    radius,
                    paint
            );

            canvas.drawCircle(
                    cx,
                    cy,
                    radius * 0.65f,
                    paint
            );

            paint.setStyle(
                    Paint.Style.FILL
            );

            canvas.save();

            canvas.rotate(
                    radarAngle,
                    cx,
                    cy
            );

            paint.setColor(
                    Color.argb(
                            130,
                            25,
                            220,
                            255
                    )
            );

            canvas.drawRect(
                    cx,
                    cy - dp(2),
                    cx + radius,
                    cy + dp(2),
                    paint
            );

            canvas.restore();

            // Logo
            if (logoBitmap != null) {

                float logoSize =
                        dp(82);

                RectF logoRect =
                        new RectF(
                                cx - logoSize / 2,
                                cy - logoSize / 2,
                                cx + logoSize / 2,
                                cy + logoSize / 2
                        );

                canvas.drawBitmap(
                        logoBitmap,
                        null,
                        logoRect,
                        paint
                );
            }

            // SCANNING text
            paint.setColor(
                    Color.WHITE
            );

            paint.setTextAlign(
                    Paint.Align.CENTER
            );

            paint.setTypeface(
                    Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                    )
            );

            paint.setTextSize(
                    dp(28)
            );

            canvas.drawText(
                    "SCANNING...",
                    cx,
                    cy + radius + dp(60),
                    paint
            );

            paint.setTextSize(
                    dp(22)
            );

            canvas.drawText(
                    progress + "/100",
                    cx,
                    cy + radius + dp(92),
                    paint
            );
        }

        public void setProgress(int value) {

            progress =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    value
                            )
                    );

            invalidate();
        }
    }

    // ============================================================
    // RESULT VIEW
    // ============================================================

    private class ResultView extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final String signal;
        private final int score;

        private Bitmap logoBitmap;

        public ResultView(
                Context context,
                String signal,
                int score
        ) {

            super(context);

            this.signal = signal;
            this.score = score;

            logoBitmap =
                    BitmapFactory.decodeResource(
                            getResources(),
                            R.drawable.md_jibon_logo
                    );

            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();

            // Card
            paint.setColor(
                    Color.argb(
                            245,
                            4,
                            18,
                            40
                    )
            );

            canvas.drawRoundRect(
                    new RectF(
                            2,
                            2,
                            w - 2,
                            h - 2
                    ),
                    dp(14),
                    dp(14),
                    paint
            );

            boolean up =
                    "UP".equalsIgnoreCase(signal);

            paint.setColor(
                    up
                            ? Color.rgb(
                                    30,
                                    220,
                                    100
                            )
                            : Color.rgb(
                                    255,
                                    70,
                                    70
                            )
            );

            paint.setTypeface(
                    Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                    )
            );

            paint.setTextAlign(
                    Paint.Align.CENTER
            );

            paint.setTextSize(
                    dp(25)
            );

            String direction =
                    up ? "↑ UP" : "↓ DOWN";

            canvas.drawText(
                    direction,
                    w / 2,
                    dp(34),
                    paint
            );

            paint.setColor(
                    Color.WHITE
            );

            paint.setTextSize(
                    dp(18)
            );

            canvas.drawText(
                    score + "%",
                    w / 2,
                    dp(61),
                    paint
            );

            paint.setTextSize(
                    dp(10)
            );

            paint.setColor(
                    Color.rgb(
                            150,
                            190,
                            220
                    )
            );

            canvas.drawText(
                    "100 TECHNICAL CHECKS",
                    w / 2,
                    dp(76),
                    paint
            );
        }

        @Override
        public boolean onTouchEvent(
                MotionEvent event
        ) {

            if (event.getAction() ==
                    MotionEvent.ACTION_UP) {

                removeResult();

                return true;
            }

            return true;
        }
    }

    // ============================================================
    // BROADCAST RECEIVER
    // ============================================================

    private void createReceiver() {

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

                        if (ACTION_PROGRESS.equals(action)) {

                            int progress =
                                    intent.getIntExtra(
                                            "progress",
                                            0
                                    );

                            if (scanView != null) {

                                scanView.setProgress(
                                        progress
                                );
                            }

                        } else if (
                                ACTION_RESULT.equals(action)
                        ) {

                            String signal =
                                    intent.getStringExtra(
                                            EXTRA_SIGNAL
                                    );

                            if (signal == null) {
                                signal = "DOWN";
                            }

                            int score =
                                    intent.getIntExtra(
                                            EXTRA_SCORE,
                                            50
                                    );

                            showResult(
                                    signal,
                                    score
                            );
                        }
                    }
                };

        IntentFilter filter =
                new IntentFilter();

        filter.addAction(
                ACTION_PROGRESS
        );

        filter.addAction(
                ACTION_RESULT
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

    // ============================================================
    // CLEANUP
    // ============================================================

    @Override
    public void onDestroy() {

        running = false;
        scanning = false;

        handler.removeCallbacksAndMessages(
                null
        );

        removeResult();
        removeScanView();
        removeLogo();

        if (receiver != null) {

            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }

            receiver = null;
        }

        if (Build.VERSION.SDK_INT >= 24) {

            stopForeground(
                    STOP_FOREGROUND_REMOVE
            );
        } else {

            stopForeground(true);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ============================================================
    // DP
    // ============================================================

    private int dp(float value) {

        return (int)
                (value *
                        getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f);
    }
}
