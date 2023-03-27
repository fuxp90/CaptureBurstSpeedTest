package com.cdts.synccapture;

import android.app.Application;

public class SyncApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.initSpf(this);
        UdpClient.getInstance(getApplicationContext()).start();
        SmbClient.getSmbClient().start();
    }
}
