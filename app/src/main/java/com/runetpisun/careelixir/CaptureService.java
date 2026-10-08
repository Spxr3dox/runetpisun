package com.runetpisun.careelixir;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public final class CaptureService extends Service {
    private static final String CHANNEL_ID = "capture_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int CAPTURE_FPS = 3;
    private static final long FRAME_INTERVAL_MS = 1000 / CAPTURE_FPS;

    private static CaptureService instance;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private Handler mainHandler;
    private boolean capturing;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    static CaptureService getInstance() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mainHandler = new Handler(getMainLooper());
        createNotificationChannel();

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        ScreenAnalyzer analyzer = ElixirBridge.getAnalyzer();
        if (analyzer != null) {
            analyzer.setScreenSize(screenWidth, screenHeight);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());

        if (intent != null && intent.hasExtra("resultCode")) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("data");
            if (data != null) {
                startCapture(resultCode, data);
            }
        }
        return START_STICKY;
    }

    private void startCapture(int resultCode, Intent data) {
        if (capturing) return;

        MediaProjectionManager mpm =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) return;

        int scale = 2;
        int w = screenWidth / scale;
        int h = screenHeight / scale;

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay("CareElixir",
            w, h, screenDensity / scale,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, null);

        captureThread = new HandlerThread("capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        capturing = true;
        captureHandler.post(frameGrabber);
    }

    private final Runnable frameGrabber = new Runnable() {
        @Override
        public void run() {
            if (!capturing) return;
            grabFrame();
            captureHandler.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    private void grabFrame() {
        if (imageReader == null) return;
        Image image = imageReader.acquireLatestImage();
        if (image == null) return;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();
            int bitmapWidth = image.getWidth() + rowPadding / pixelStride;

            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
            if (cropped != bitmap) bitmap.recycle();

            final Bitmap frame = cropped;
            final long ts = System.currentTimeMillis();

            ScreenAnalyzer analyzer = ElixirBridge.getAnalyzer();
            if (analyzer != null && analyzer.isActive()) {
                analyzer.analyzeFrame(frame, ts);
            }
            frame.recycle();
        } finally {
            image.close();
        }
    }

    void stopCapture() {
        capturing = false;
        if (captureHandler != null) captureHandler.removeCallbacksAndMessages(null);
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        if (captureThread != null) { captureThread.quitSafely(); captureThread = null; }
        stopForeground(true);
        stopSelf();
        instance = null;
    }

    boolean isCapturing() { return capturing; }

    @Override
    public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, notifIntent,
            PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }
}
