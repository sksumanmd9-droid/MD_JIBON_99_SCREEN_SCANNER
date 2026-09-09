package com.mdjibon.scanner;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ScreenCaptureService extends Service {

    public static final String ACTION_RESULT = "MDJIBON_SCAN_RESULT";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && "SCAN_NOW".equals(intent.getAction())) {

            // Temporary scan result
            Intent result = new Intent(ACTION_RESULT);
            result.putExtra("signal", "NO TRADE");
            result.putExtra("confidence", 0);
            result.putExtra("quality", 0);
            result.putExtra("ruleCount", 100);
            result.putExtra("detectedCandles", 0);

            sendBroadcast(result);
        }

        return START_NOT_STICKY;
    }
}
