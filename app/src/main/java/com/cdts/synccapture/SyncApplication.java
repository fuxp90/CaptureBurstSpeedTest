package com.cdts.synccapture;

import android.app.Application;

public class SyncApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        UdpClient.getInstance(getApplicationContext()).start();
    }
}
