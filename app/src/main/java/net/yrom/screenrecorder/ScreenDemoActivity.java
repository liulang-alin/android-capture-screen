package net.yrom.screenrecorder;

import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.mzp.capturesdk.CaptureImageConfig;
import com.mzp.capturesdk.CaptureSdk;
import com.mzp.capturesdk.CaptureSdkImpl;

public class ScreenDemoActivity extends AppCompatActivity {
    private static final String TAG = "ScreenDemoActivity";

    private CaptureSdk sdk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_demo);
        initView();

        sdk = new CaptureSdkImpl();
    }

    private void initView() {
        findViewById(R.id.btn_init).setOnClickListener(v -> {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            sdk.init(new CaptureImageConfig(size.x, size.y));
        });

        findViewById(R.id.btn_start).setOnClickListener(v -> sdk.start(ScreenDemoActivity.this, new CaptureSdk.OnCaptureStartListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(v.getContext(), "Start success", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailed(int code, String message) {
                Toast.makeText(v.getContext(), "Start error:" + message, Toast.LENGTH_SHORT).show();
            }
        }));

        String path = getExternalFilesDir(null).getAbsolutePath();
        findViewById(R.id.btn_capture).setOnClickListener(v -> sdk.requestCapture(new CaptureSdk.OnCaptureResultListener() {
            @Override
            public void onCaptureSuccess(CaptureSdk.CaptureSource source, CaptureSdk.LocalFormat format, String path) {
                Toast.makeText(v.getContext(), "capture success:" + path, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCaptureError(int code, String message) {
                Toast.makeText(v.getContext(), "capture error:" + message, Toast.LENGTH_SHORT).show();
            }
        }, path));

        findViewById(R.id.btn_stop).setOnClickListener(v -> sdk.destroy());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
    }
}