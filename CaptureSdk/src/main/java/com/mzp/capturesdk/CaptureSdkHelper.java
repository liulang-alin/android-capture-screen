package com.mzp.capturesdk;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.support.annotation.NonNull;

final class CaptureSdkHelper {
    public static void requestMediaProjection(@NonNull Fragment context, int requestCode) {
        MediaProjectionManager mgr = (MediaProjectionManager) context.getActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mgr.createScreenCaptureIntent();
        context.startActivityForResult(captureIntent, requestCode);
    }
}