package com.mdjibon.scanner;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {

    public static final String ACTION_START_CAPTURE="com.mdjibon.scanner.START_CAPTURE";
    public static final String ACTION_SCAN="com.mdjibon.scanner.ACTION_SCAN";
    public static final String ACTION_RESULT="com.mdjibon.scanner.ACTION_RESULT";
    public static final String ACTION_ERROR="com.mdjibon.scanner.ACTION_ERROR";
    public static final String ACTION_CAPTURE_STATE="com.mdjibon.scanner.ACTION_CAPTURE_STATE";

    private static final int NOTIFICATION_ID=9902;
    private static volatile boolean captureActive=false;

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Object lock=new Object();
    private Bitmap latest;
    private Handler main;

    public static boolean isCaptureActive(){return captureActive;}

    @Override public void onCreate(){
        super.onCreate(); main=new Handler(Looper.getMainLooper()); startForegroundCompat();
    }

    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null)return START_STICKY;
        String a=intent.getAction();
        if(ACTION_START_CAPTURE.equals(a)){
            int code=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);
            Intent data=intent.getParcelableExtra("data");
            startCapture(code,data);
        } else if(ACTION_SCAN.equals(a)){
            scan();
        } else if("STOP".equals(a)){
            stopCapture();
            stopSelf();
        }
        return START_STICKY;
    }

    private void startForegroundCompat(){
        String channel="md_jibon_capture";
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            nm.createNotificationChannel(new NotificationChannel(channel,"MD JIBON Screen Capture",NotificationManager.IMPORTANCE_LOW));
        }
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification n=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?channel:null)
                .setContentTitle("MD JIBON Scanner")
                .setContentText("Screen capture is ready")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi).build();
        if(Build.VERSION.SDK_INT>=29){
            startForeground(NOTIFICATION_ID,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        }else startForeground(NOTIFICATION_ID,n);
    }

    private void startCapture(int resultCode,Intent data){
        if(resultCode!=Activity.RESULT_OK || data==null){sendError("Screen capture permission was not granted");return;}
        try{
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=m.getMediaProjection(resultCode,data);
            DisplayMetrics dm=getResources().getDisplayMetrics();
            int w=dm.widthPixels, h=dm.heightPixels, d=dm.densityDpi;
            reader=ImageReader.newInstance(w,h,android.graphics.PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r->{
                Image img=null;
                try{
                    img=r.acquireLatestImage(); if(img==null)return;
                    Image.Plane p=img.getPlanes()[0]; ByteBuffer buf=p.getBuffer();
                    int pixelStride=p.getPixelStride(), rowStride=p.getRowStride();
                    int rowPadding=rowStride-pixelStride*w;
                    Bitmap b=Bitmap.createBitmap(w+rowPadding/pixelStride,h,Bitmap.Config.ARGB_8888);
                    b.copyPixelsFromBuffer(buf);
                    Bitmap cropped=Bitmap.createBitmap(b,0,0,w,h);
                    b.recycle();
                    synchronized(lock){
                        if(latest!=null && !latest.isRecycled())latest.recycle();
                        latest=cropped;
                    }
                }catch(Exception ignored){} finally{if(img!=null)img.close();}
            },main);
            display=projection.createVirtualDisplay("MDJIBON",w,h,d,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
            captureActive=true;
            sendBroadcast(new Intent(ACTION_CAPTURE_STATE).putExtra("active",true));
        }catch(Exception e){sendError("Capture start failed: "+e.getMessage());}
    }

    private void scan(){
        Bitmap b=null;
        synchronized(lock){if(latest!=null && !latest.isRecycled())b=latest.copy(Bitmap.Config.ARGB_8888,false);}
        if(b==null){sendError("No current screen frame available yet");return;}
        final Bitmap frame=b;
        executor.execute(()->{
            Analyzer.Result r=Analyzer.analyze(frame);
            if(!frame.isRecycled())frame.recycle();
            Intent i=new Intent(ACTION_RESULT);
            i.putExtra("signal",r.signal);
            i.putExtra("score",(float)r.confidence);
            i.putExtra("nextColor",r.nextCandleColor);
            i.putExtra("nextSize",r.nextCandleSize);
            i.putExtra("body",(float)r.nextBodyRatio);
            i.putExtra("candles",r.detectedCandles);
            i.putExtra("rules",r.evaluatedRules);
            sendBroadcast(i);
        });
    }

    private void stopCapture(){
        captureActive=false;
        try{if(display!=null)display.release();}catch(Exception ignored){}
        try{if(reader!=null)reader.close();}catch(Exception ignored){}
        try{if(projection!=null)projection.stop();}catch(Exception ignored){}
        synchronized(lock){if(latest!=null&&!latest.isRecycled())latest.recycle();latest=null;}
        display=null;reader=null;projection=null;
        sendBroadcast(new Intent(ACTION_CAPTURE_STATE).putExtra("active",false));
    }

    private void sendError(String s){sendBroadcast(new Intent(ACTION_ERROR).putExtra("message",s));}

    @Override public void onDestroy(){stopCapture();executor.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
