package com.cdts.synccapture;

import android.app.Application;
import android.util.Log;

import java.io.File;

public class CaptureApplication extends Application {

    private CameraController mCameraController;

    private Storage mStorage;
    private static CaptureApplication sCaptureApplication;
    private static final String TAG = "CaptureApplication";
    private File mDir;

    @Override
    public void onCreate() {
        super.onCreate();
        sCaptureApplication = this;
        mStorage = new Storage();
        mCameraController = new CameraController(this);
        mCameraController.setOnFmtChangedListener((fmt, fmtStr) -> {
            File dir = new File(getExternalCacheDir(), fmtStr);
            if (!dir.exists()) {
                try {
                    boolean b = dir.mkdir();
                    if (b) {
                        Log.d(TAG, "make image storage dir: " + dir.getAbsolutePath() + " successful");
                    } else {
                        Log.e(TAG, "make image storage dir: " + dir.getAbsolutePath() + " failed");
                    }
                    mDir = dir;

                    mStorage.setDir(dir);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "make image storage dir: " + dir.getAbsolutePath() + " failed");
                }
            }
        });
    }

    public File getDir() {
        return mDir;
    }

    public static CaptureApplication getCaptureApplication() {
        return sCaptureApplication;
    }

    public Storage getStorage() {
        return mStorage;
    }

    public CameraController getCameraController() {
        return mCameraController;
    }
}
