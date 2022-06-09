package com.mzp.capturesdk;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;

public interface CaptureSdk {
    enum CaptureSource {
        Screen,
        Camera,
        Mic,
        Screen_Mic,
        Camera_Mic,
    }

    enum UploadCategory {
        File,
        Stream,
    }

    // Camera or Screen local storage format
    enum LocalFormat {
        JPG,
        MP4,
    }

    interface OnCapturePermissionListener {
        void onGranted(int resultCode, Intent data);

        void onDenied();
    }

    interface OnCaptureStartListener {
        void onSuccess();

        void onFailed(int code, String message);
    }

    interface OnCaptureResultListener {
        void onCaptureSuccess(CaptureSource source, LocalFormat format, String path);

        void onCaptureError(int code, String message);
    }

    void init(CaptureImageConfig config);

    void start(AppCompatActivity activity, OnCaptureStartListener listener);

    void destroy();

    void requestCapture(CaptureSource source, UploadCategory category, LocalFormat format, OnCaptureResultListener listener);

    void requestCapture(CaptureSource source, LocalFormat format, OnCaptureResultListener listener);

    void requestCapture(LocalFormat format, OnCaptureResultListener listener);

    void requestCapture(OnCaptureResultListener listener);
}
