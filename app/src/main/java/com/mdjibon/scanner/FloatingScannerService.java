package com.mdjibon.scanner;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class FloatingScannerService extends Service {

    public static final String ACTION_RESULT="com.mdjibon.scanner.ACTION_RESULT";
    private WindowManager wm;
    private View bubble;
    private TextView badge;
    private boolean scanning=false;
    private Handler handler;

    @Override public void onCreate(){
        super.onCreate(); handler=new Handler(Looper.getMainLooper()); startForegroundNotification(); createBubble();
        IntentFilter f=new IntentFilter(ScreenCaptureService.ACTION_RESULT);
        registerReceiver(receiver,f);
    }

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(!ScreenCaptureService.ACTION_RESULT.equals(i.getAction()))return;
            scanning=false;
            String signal=i.getStringExtra("signal");
            float score=i.getFloatExtra("score",0);
            String color=i.getStringExtra("nextColor");
            String size=i.getStringExtra("nextSize");
            if(signal==null)signal="NO TRADE";
            showBadge(signal,score);
            Toast.makeText(FloatingScannerService.this,
                    signal+" â€¢ "+String.format(java.util.Locale.US,"%.1f%%",score)+
                    "\nNext: "+(color==null?"UNKNOWN":color)+" â€¢ "+(size==null?"UNKNOWN":size),
                    Toast.LENGTH_SHORT).show();
        }
    };

    private void startForegroundNotification(){
        String ch="md_jibon_floating";
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"MD JIBON Floating Scanner",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?ch:null)
                .setContentTitle("MD JIBON Floating Scanner")
                .setContentText("Floating scan button is active")
                .setSmallIcon(android.R.drawable.ic_menu_search).build();
        startForeground(9903,n);
    }

    private void createBubble(){
        if(!Settings.canDrawOverlays(this))return;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon=new TextView(this);
        icon.setText("âœ¦");
        icon.setTextSize(27);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.WHITE);
        GradientDrawable gd=new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.rgb(0,185,95));
        gd.setStroke(dp(2),Color.rgb(100,255,170));
        icon.setBackground(gd);
        box.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));

        badge=new TextView(this);
        badge.setText("SCAN");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(5),0,dp(5),0);
        GradientDrawable bd=new GradientDrawable();
        bd.setColor(Color.rgb(8,18,28));
        bd.setStroke(dp(1),Color.rgb(0,220,120));
        bd.setCornerRadius(dp(8));
        badge.setBackground(bd);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(62),dp(32));
        bp.leftMargin=dp(3);
        box.addView(badge,bp);

        final WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                dp(125),dp(70),
                Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;p.x=15;p.y=280;

        icon.setOnClickListener(v->{
            if(scanning)return;
            if(!ScreenCaptureService.isCaptureActive()){
                Toast.makeText(this,"Turn ON Screen Capture first",Toast.LENGTH_SHORT).show();return;
            }
            scanning=true; badge.setText("SCAN...");
            Intent i=new Intent(this,ScreenCaptureService.class);i.setAction(ScreenCaptureService.ACTION_SCAN);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        });

        box.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY,startX,startY;long time;
            public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX=e.getRawX();downY=e.getRawY();startX=p.x;startY=p.y;time=System.currentTimeMillis();return true;
                    case MotionEvent.ACTION_MOVE:
                        p.x=(int)(startX+e.getRawX()-downX);p.y=(int)(startY+e.getRawY()-downY);
                        try{wm.updateViewLayout(box,p);}catch(Exception ignored){}return true;
                    case MotionEvent.ACTION_UP:
                        if(Math.abs(e.getRawX()-downX)<dp(10)&&Math.abs(e.getRawY()-downY)<dp(10)
                                &&System.currentTimeMillis()-time<350)icon.performClick();
                        return true;
                }return false;
            }
        });

        bubble=box;wm.addView(bubble,p);
    }

    private void showBadge(String signal,float score){
        if(badge==null)return;
        badge.setText(signal+" "+String.format(java.util.Locale.US,"%.0f%%",score));
        badge.setTextColor("UP".equals(signal)?Color.rgb(70,255,140):
                "DOWN".equals(signal)?Color.rgb(255,90,90):Color.YELLOW);
    }

    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}

    @Override public void onDestroy(){
        try{unregisterReceiver(receiver);}catch(Exception ignored){}
        if(wm!=null&&bubble!=null)try{wm.removeView(bubble);}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
