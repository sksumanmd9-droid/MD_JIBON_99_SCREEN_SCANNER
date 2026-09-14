package com.mdjibon.scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class FloatingScannerService extends Service {
    public static final String ACTION_AUTO_SCAN = "com.mdjibon.scanner.ACTION_AUTO_SCAN";
    public static final String ACTION_AUTO_SCAN_NOW = "com.mdjibon.scanner.ACTION_AUTO_SCAN_NOW";
    private static volatile boolean running;
    private WindowManager wm;
    private LinearLayout bubble;
    private TextView badge;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private boolean autoScan;
    private Runnable autoRunnable;

    public static boolean isRunning(){return running;}

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(!ScreenCaptureService.ACTION_RESULT.equals(i.getAction()))return;
            String signal=i.getStringExtra("signal"); float score=i.getFloatExtra("score",0);
            showBadge(signal,score);
            Toast.makeText(FloatingScannerService.this,signal+"  "+String.format(Locale.US,"%.1f%%",score),Toast.LENGTH_SHORT).show();
        }
    };

    @Override public void onCreate(){super.onCreate();handler=new Handler(Looper.getMainLooper());startForegroundNotification();registerReceiverSafe();if(Settings.canDrawOverlays(this))createBubble();running=true;autoScan=getSharedPreferences("scanner_settings",0).getBoolean("autoScan",false);if(autoScan)scheduleAuto();}

    private void registerReceiverSafe(){IntentFilter f=new IntentFilter(ScreenCaptureService.ACTION_RESULT);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);}
    private void startForegroundNotification(){String ch="md_jibon_floating";NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel(ch,"MD JIBON Floating Scanner",NotificationManager.IMPORTANCE_LOW));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this);startForeground(9903,b.setContentTitle("MD JIBON Scanner").setContentText("Floating scanner is active").setSmallIcon(android.R.drawable.ic_menu_search).build());}

    private void createBubble(){if(bubble!=null||!Settings.canDrawOverlays(this))return;wm=(WindowManager)getSystemService(WINDOW_SERVICE);bubble=new LinearLayout(this);bubble.setGravity(Gravity.CENTER_VERTICAL);TextView icon=new TextView(this);icon.setText("MD");icon.setTextSize(17);icon.setGravity(Gravity.CENTER);icon.setTextColor(Color.rgb(80,255,170));GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(Color.rgb(6,27,36));bg.setStroke(dp(2),Color.rgb(45,235,150));icon.setBackground(bg);bubble.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));badge=new TextView(this);badge.setText("SCAN");badge.setTextColor(Color.WHITE);badge.setTextSize(10);badge.setGravity(Gravity.CENTER);badge.setSingleLine();GradientDrawable bb=new GradientDrawable();bb.setColor(Color.rgb(8,18,28));bb.setStroke(dp(1),Color.rgb(45,235,150));bb.setCornerRadius(dp(8));badge.setBackground(bb);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(62),dp(32));bp.leftMargin=dp(3);bubble.addView(badge,bp);
        params=new WindowManager.LayoutParams(dp(125),dp(68),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;params.x=dp(10);params.y=dp(260);
        icon.setOnClickListener(v->requestScan());
        bubble.setOnTouchListener(new View.OnTouchListener(){float dx,dy;float sx,sy;long time;public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:dx=e.getRawX();dy=e.getRawY();sx=params.x;sy=params.y;time=System.currentTimeMillis();return true;case MotionEvent.ACTION_MOVE:int maxX=Math.max(0,wm.getDefaultDisplay().getWidth()-params.width);int maxY=Math.max(0,wm.getDefaultDisplay().getHeight()-params.height);params.x=Math.max(0,Math.min(maxX,(int)(sx+e.getRawX()-dx)));params.y=Math.max(0,Math.min(maxY,(int)(sy+e.getRawY()-dy)));try{wm.updateViewLayout(bubble,params);}catch(Exception ignored){}return true;case MotionEvent.ACTION_UP:if(Math.abs(e.getRawX()-dx)<dp(10)&&Math.abs(e.getRawY()-dy)<dp(10)&&System.currentTimeMillis()-time<450)requestScan();return true;}return true;}});
        wm.addView(bubble,params);
    }

    private void requestScan(){if(!ScreenCaptureService.isCaptureActive()){Toast.makeText(this,"Open MD JIBON and allow screen capture first.",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(this,ScreenCaptureService.class);i.setAction(ScreenCaptureService.ACTION_SCAN);i.putExtra("excludeX",params.x);i.putExtra("excludeY",params.y);i.putExtra("excludeW",params.width);i.putExtra("excludeH",params.height);startServiceCompat(i);showBadge("SCAN",0);}
    private void showBadge(String signal,float score){if(badge==null)return;if(signal==null)signal="NO TRADE";badge.setText("SCAN".equals(signal)?"SCANNING":signal+" "+String.format(Locale.US,"%.0f%%",score));GradientDrawable bg=new GradientDrawable();bg.setColor("UP".equals(signal)?Color.rgb(0,120,70):"DOWN".equals(signal)?Color.rgb(125,25,35):Color.rgb(8,18,28));bg.setStroke(dp(1),Color.rgb(45,235,150));bg.setCornerRadius(dp(8));badge.setBackground(bg);}
    private void scheduleAuto(){if(autoRunnable!=null)handler.removeCallbacks(autoRunnable);autoRunnable=()->{requestScan();scheduleAuto();};long ms=timeframeMs();handler.postDelayed(autoRunnable,ms);}
    private long timeframeMs(){String tf=getSharedPreferences("scanner_settings",0).getString("timeframe","1 MIN");if("15 SEC".equals(tf))return 15000;if("30 SEC".equals(tf))return 30000;if("5 MIN".equals(tf))return 300000;return 60000;}
    private void setAuto(boolean e){autoScan=e;if(e)scheduleAuto();else if(autoRunnable!=null)handler.removeCallbacks(autoRunnable);}
    private void startServiceCompat(Intent i){if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&ACTION_AUTO_SCAN.equals(i.getAction()))setAuto(i.getBooleanExtra("enabled",false));if(bubble==null&&Settings.canDrawOverlays(this))createBubble();return START_STICKY;}
    @Override public void onDestroy(){running=false;if(autoRunnable!=null)handler.removeCallbacks(autoRunnable);try{unregisterReceiver(receiver);}catch(Exception ignored){}try{if(bubble!=null)wm.removeView(bubble);}catch(Exception ignored){}bubble=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
