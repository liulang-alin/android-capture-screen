package com.mzp.capturesdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public final class CaptureSdkImpl implements CaptureSdk {

    private static final String TAG = "CaptureSdkImpl";

    private static final int CMD_NOP = 0x00;
    private static final int CMD_START = 0x10;
    private static final int CMD_CAPTURE = 0x20;
    private static final int CMD_STOP = 0x30;

    private CaptureImageConfig mConfig;
    private Context appContext;
    private OnCaptureResultListener captureResultListener;

    @Override
    public void init(CaptureImageConfig config) {
        mConfig = config;
        EventBus.getDefault().register(this);
    }

    @Override
    public void start(Activity activity, OnCaptureStartListener listener) {
        if (mConfig == null) {
            throw new RuntimeException("Please invoke init() first !");
        }

        appContext = activity.getApplicationContext();

        final int REQUEST_CAPTURE = 1000;
        activity.getFragmentManager().beginTransaction()
                .add(android.R.id.content, HiddenFragment.newInstance(REQUEST_CAPTURE, new OnCapturePermissionListener() {
                    @Override
                    public void onGranted(int resultCode, Intent data) {
                        ExamService.requestStart(appContext, resultCode, data, mConfig);
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
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void requestCapture(CaptureSource source, UploadCategory category, LocalFormat format, String path, OnCaptureResultListener listener) {
        checkParameters(source, category, format);
        if (listener == null) {
            return;
        }
        captureResultListener = listener;
        ExamService.requestCapture(appContext, path);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSaveFileEvent(SaveFileEvent event) {
        Log.d(TAG, "onSaveFileEvent() event:" + event.toString());
        if (captureResultListener != null) {
            if (event.success) {
                captureResultListener.onCaptureSuccess(CaptureSource.Screen, LocalFormat.JPG, event.path);
            } else {
                captureResultListener.onCaptureError(event.code, event.message);
            }
        }
        captureResultListener = null;
    }

    @Override
    public void requestCapture(CaptureSource source, LocalFormat format, String path, OnCaptureResultListener listener) {
        requestCapture(source, UploadCategory.File, format, path, listener);
    }

    @Override
    public void requestCapture(LocalFormat format, String path, OnCaptureResultListener listener) {
        requestCapture(CaptureSource.Screen, format, path, listener);
    }

    @Override
    public void requestCapture(OnCaptureResultListener listener, String path) {
        requestCapture(LocalFormat.JPG, path, listener);
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
                getActivity()
                        .getFragmentManager()
                        .beginTransaction()
                        .remove(this)
                        .commit();
            } catch (Exception e) {
                Log.d(TAG, "onActivityResult:" + e.getMessage());
            }
        }
    }

    public static class ExamService extends Service {
        private static final String TAG = "ExamService";

        private ScreenRecorder mRecorder;
        private VirtualDisplay mVirtualDisplay;

        public static void requestStart(Context context, int resultCode, Intent resultData, CaptureImageConfig config) {
            Intent intent = new Intent(context, ExamService.class);
            intent.putExtra("command", CMD_START);
            intent.putExtra("resultCode", resultCode);
            intent.putExtra("resultData", resultData);
            intent.putExtra("config", config);
            context.startService(intent);
        }

        public static void requestCapture(Context context, String path) {
            Intent intent = new Intent(context, ExamService.class);
            intent.putExtra("command", CMD_CAPTURE);
            intent.putExtra("path", path);
            context.startService(intent);
        }

        @Override
        public void onCreate() {
            Log.d(TAG, "onCreate");
            super.onCreate();
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.d(TAG, "onStartCommand");
            int cmd = intent.getIntExtra("command", 0);
            Log.d(TAG, "onStartCommand command = " + cmd);
            switch (cmd) {
                case CMD_NOP:
                    break;

                case CMD_START:
                    int resultCode = intent.getIntExtra("resultCode", -1);
                    Intent resultData = intent.getParcelableExtra("resultData");
                    sendNotification();
                    MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    MediaProjection mMediaProjection = mgr.getMediaProjection(resultCode, resultData);
                    CaptureImageConfig config = (CaptureImageConfig) intent.getSerializableExtra("config");
                    mRecorder = newRecorder(mMediaProjection, config);
                    startRecorder();
                    break;

                case CMD_CAPTURE:
                    String path = intent.getStringExtra("path");
                    Log.d(TAG, "capture path = " + path);
                    mRecorder.requestCapture(path, new ScreenRecorder.OnRecordScreenListener() {
                        @Override
                        public void onSuccess(int type, String path) {
                            Log.d(TAG, "capture success: path = " + path);
                            EventBus.getDefault().post(new SaveFileEvent(path));
                        }

                        @Override
                        public void onError(int code, String message) {
                            Log.d(TAG, "capture error: message = " + message);
                            EventBus.getDefault().post(new SaveFileEvent(code, message));
                        }
                    });
                    break;

                case CMD_STOP:
                    stopRecorder();
                    break;
            }
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy");
            stopRecorder();
            super.onDestroy();
        }

        private void sendNotification() {
            Log.d(TAG, "sendNotification");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(this, ExamService.class);
                PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "100")
                        .setSmallIcon(R.drawable.ic_stat_recording)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_stat_recording))
                        .setContentTitle("考试服务")
                        .setContentText("考试服务运行中")
                        .setTicker("ticker")
                        .setContentIntent(pIntent);

                Notification notification = builder.build();
                NotificationChannel channel = new NotificationChannel("100", "c_name", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("考试服务，请不要关闭");
                NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mgr.createNotificationChannel(channel);
                startForeground(200, notification);
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
    }

    public static class SaveFileEvent {
        private final boolean success;
        private final String path;
        private final int code;
        private final String message;

        public SaveFileEvent(int code, String message) {
            success = false;
            path = null;
            this.code = code;
            this.message = message;
        }

        public SaveFileEvent(String path) {
            success = true;
            this.path = path;
            code = 0;
            message = null;
        }

        @Override
        public String toString() {
            return "success:" + success + ", path:" + path + ",code:" + code + ",message:" + message;
        }
    }
}
