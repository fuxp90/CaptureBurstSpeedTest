package com.cdts.synccapture;

import android.app.Application;

public class CaptureApplication extends Application {

    private CameraController mCameraController;

    @Override
    public void onCreate() {
        super.onCreate();

        mCameraController = new CameraController(this);
    }

    public CameraController getCameraController() {
        return mCameraController;
    }
}
