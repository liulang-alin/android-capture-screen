package com.mzp.capturesdk;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

public final class CaptureSdk {
    private static final CaptureSdk _instance = new CaptureSdk();

    private CaptureSdk() {

    }

    public static CaptureSdk getInstance() {
        return _instance;
    }

    public boolean checkPermissions(Context context) {

        return context.checkSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL) == PackageManager.PERMISSION_GRANTED;
    }
}
