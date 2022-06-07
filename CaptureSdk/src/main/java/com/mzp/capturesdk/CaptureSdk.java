package com.mzp.capturesdk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

public final class CaptureSdk {
    public enum CaptureSource {
        Screen,
        Camera,
        Mic,
        Screen_Mic,
        Camera_Mic,
    }

    public enum UploadCategory {
        File,
        Stream,
    }

    // Camera or Screen local storage format
    public enum LocalFormat {
        JPG,
        MP4,
    }

    public interface OnCaptureResultListener {
        void onCaptureSuccess(CaptureSource source, LocalFormat format, String path);

        void onCaptureError(int code, String message);
    }

    private static final String TAG = "CaptureSdk";
    private static final CaptureSdk _instance = new CaptureSdk();

    private ScreenRecorder mRecorder;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private Notifications mNotifications;

    private Handler handler = new Handler(Looper.getMainLooper());

    private CaptureSdk() {

    }

    public static CaptureSdk getInstance() {
        return _instance;
    }

    public void initialize(Activity flutterActivity) {

    }

    public void destroy() {
        stopRecorder();
    }

    public void requestCapture(CaptureSource source, UploadCategory category, LocalFormat format, OnCaptureResultListener listener) {
        checkParameters(source, category, format);
        if (listener == null) {
            return;
        }

        startRecorder();
    }

    public void requestCapture(CaptureSource source, LocalFormat format, OnCaptureResultListener listener) {
        requestCapture(source, UploadCategory.File, format, listener);
    }

    public void requestCapture(LocalFormat format, OnCaptureResultListener listener) {
        requestCapture(CaptureSource.Screen, format, listener);
    }

    public void requestCapture(OnCaptureResultListener listener) {
        requestCapture(LocalFormat.JPG, listener);
    }

    private void checkParameters(CaptureSource source, UploadCategory category, LocalFormat format) {
        if (source != CaptureSource.Screen) {
            throw new RuntimeException("Just Capture Screen supported now !");
        }

        if (category != UploadCategory.File) {
            throw new RuntimeException("Just Upload file supported now !");
        }

        if (format != LocalFormat.JPG) {
            throw new RuntimeException("Just JPG format supported now !");
        }
    }

    private ScreenRecorder newRecorder(MediaProjection mediaProjection, VideoEncodeConfig video,
                                       AudioEncodeConfig audio, File output) {
        final VirtualDisplay display = getOrCreateVirtualDisplay(mediaProjection, video);
        ScreenRecorder r = new ScreenRecorder(video, audio, display, output.getAbsolutePath());
        r.setCallback(new ScreenRecorder.Callback() {
            long startTime = 0;

            @Override
            public void onStop(Throwable error) {
                handler.post(() -> stopRecorder());
                if (error != null) {
                    error.printStackTrace();
                    boolean status = output.delete();
                } else {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setData(Uri.fromFile(output));
                    // sendBroadcast(intent);
                }
            }

            @Override
            public void onStart() {
                mNotifications.recording(0);
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }
                long time = (presentationTimeUs - startTime) / 1000;
                mNotifications.recording(time);
            }
        });
        return r;
    }

    private VirtualDisplay getOrCreateVirtualDisplay(MediaProjection mediaProjection, VideoEncodeConfig config) {
        if (mVirtualDisplay == null) {
            mVirtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecorder-display0",
                    config.width, config.height, 1 /*dpi*/,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    null /*surface*/, null, null);
        } else {
            // resize if size not matched
            Point size = new Point();
            mVirtualDisplay.getDisplay().getSize(size);
            if (size.x != config.width || size.y != config.height) {
                mVirtualDisplay.resize(config.width, config.height, 1);
            }
        }
        return mVirtualDisplay;
    }

    private void stopRecorder() {
        mNotifications.clear();
        if (mRecorder != null) {
            mRecorder.quit();
        }
        mRecorder = null;
        try {
            // unregisterReceiver(mStopActionReceiver);
        } catch (Exception e) {
            //ignored
        }
    }

    private void startRecorder() {
        if (mRecorder == null) {
            return;
        }
        mRecorder.start();
        // registerReceiver(mStopActionReceiver, new IntentFilter(Constants.ACTION_STOP));
    }

    private void onActivityResult(int requestCode, int resultCode, Intent data) {
        MediaProjectionManager mMediaProjectionManager = null; // (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);

        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null");
            return;
        }

        mMediaProjection = mediaProjection;
        mMediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                stopRecorder();
            }
        }, handler);

        startRecorder();
    }
}
