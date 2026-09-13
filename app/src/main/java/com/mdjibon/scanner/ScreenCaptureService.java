package com.mdjibon.scanner;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Compatibility stub.
 *
 * The final app is screenshot-only. Direct MediaProjection/floating scanning
 * is intentionally disabled so the old scanner cannot accidentally run.
 */
public class ScreenCaptureService extends Service {

    public static final String ACTION_SCAN =
            "com.mdjibon.scanner.ACTION_SCAN";
    public static final String ACTION_STOP =
            "com.mdjibon.scanner.ACTION_STOP";
    public static final String ACTION_RESULT =
            "com.mdjibon.scanner.ACTION_RESULT";
    public static final String ACTION_PROGRESS =
            "com.mdjibon.scanner.ACTION_PROGRESS";
    public static final String ACTION_CAPTURE_STATE =
            "com.mdjibon.scanner.ACTION_CAPTURE_STATE";
    public static final String ACTION_ERROR =
            "com.mdjibon.scanner.ACTION_ERROR";

    private static volatile boolean captureActive = false;

    public static boolean isCaptureActive() {
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        captureActive = false;
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        captureActive = false;
        super.onDestroy();
    }
}
