package com.mzp.capturesdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public final class CaptureSdkImpl implements CaptureSdk {

    private static final String TAG = "CaptureSdkImpl";

    private ScreenRecorder mRecorder;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private CaptureImageConfig mConfig;

    @Override
    public void init(CaptureImageConfig config) {
        mConfig = config;
    }

    @Override
    public void start(AppCompatActivity activity, OnCaptureStartListener listener) {
        if (mConfig == null) {
            throw new RuntimeException("Please invoke init() first !");
        }

        final int REQUEST_CAPTURE = 1000;
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, HiddenFragment.newInstance(REQUEST_CAPTURE, new OnCapturePermissionListener() {
                    @Override
                    public void onGranted(int resultCode, Intent data) {
                        MediaProjectionManager mgr = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                        MediaProjection mediaProjection = mgr.getMediaProjection(resultCode, data);
                        if (listener != null) {
                            if (mediaProjection != null) {
                                listener.onSuccess();
                            } else {
                                listener.onFailed(500, "初始化截屏失败");
                            }
                        }
                        mMediaProjection = mediaProjection;
                        mRecorder = newRecorder(mMediaProjection, mConfig);
                        startRecorder();
                    }

                    @Override
                    public void onDenied() {
                        if (listener != null) {
                            listener.onFailed(400, "截屏权限未授权");
                        }
                    }
                }))
                .commit();
    }

    @Override
    public void destroy() {
        stopRecorder();
    }

    @Override
    public void requestCapture(CaptureSource source, UploadCategory category, LocalFormat format, OnCaptureResultListener listener) {
        checkParameters(source, category, format);
        if (listener == null) {
            return;
        }

        mRecorder.requestCapture(new ScreenRecorder.OnRecordScreenListener() {
            @Override
            public void onSuccess(int type, String path) {
                listener.onCaptureSuccess(CaptureSource.Screen, LocalFormat.JPG, path);
            }

            @Override
            public void onError(int code, String message) {
                listener.onCaptureError(code, message);
            }
        });
    }

    @Override
    public void requestCapture(CaptureSource source, LocalFormat format, OnCaptureResultListener listener) {
        requestCapture(source, UploadCategory.File, format, listener);
    }

    @Override
    public void requestCapture(LocalFormat format, OnCaptureResultListener listener) {
        requestCapture(CaptureSource.Screen, format, listener);
    }

    @Override
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

    private ScreenRecorder newRecorder(MediaProjection mediaProjection, CaptureImageConfig config) {
        final VirtualDisplay display = getOrCreateVirtualDisplay(mediaProjection, config);
        ScreenRecorder r = new ScreenRecorder(config, display);
        r.setCallback(new ScreenRecorder.Callback() {
            @Override
            public void onStop(Throwable error) {
                stopRecorder();
            }

            @Override
            public void onStart() {
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                Log.d(TAG, "onRecording");
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

    private VirtualDisplay getOrCreateVirtualDisplay(MediaProjection mediaProjection, CaptureImageConfig config) {
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

    private void startRecorder() {
        mRecorder.start();
    }

    private void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.quit();
        }
        mRecorder = null;
    }

    @SuppressLint("ValidFragment")
    public static class HiddenFragment extends Fragment {
        private final int requestCode;
        private final OnCapturePermissionListener listener;

        private HiddenFragment(int code, OnCapturePermissionListener listener) {
            requestCode = code;
            this.listener = listener;
        }

        public static HiddenFragment newInstance(int requestCode, OnCapturePermissionListener listener) {
            return new HiddenFragment(requestCode, listener);
        }

        @Override
        public void onAttach(Context context) {
            Log.d(TAG, "onAttach");
            super.onAttach(context);
            CaptureSdkHelper.requestMediaProjection(this, requestCode);
        }

        @Override
        public void onResume() {
            Log.d(TAG, "onResume");
            super.onResume();
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            Log.d(TAG, "HiddenFragment:onActivityResult");
            super.onActivityResult(requestCode, resultCode, data);
            if (this.requestCode == requestCode && listener != null) {
                if (resultCode == Activity.RESULT_OK) {
                    listener.onGranted(resultCode, data);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    listener.onDenied();
                }
            }

            try {
                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .remove(this)
                        .commit();
            } catch (Exception e) {
                Log.d(TAG, "onActivityResult:" + e.getMessage());
            }
        }
    }
}
