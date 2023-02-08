package com.cdts.synccapture;

import android.app.Application;

public class CaptureApplication extends Application {

    private CameraController mCameraController;

    private Storage mStorage;
    private static CaptureApplication sCaptureApplication;

    @Override
    public void onCreate() {
        super.onCreate();

        sCaptureApplication = this;
        mStorage = new Storage();
        mCameraController = new CameraController(this);
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
