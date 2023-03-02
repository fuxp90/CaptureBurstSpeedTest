package com.cdts.synccapture;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

import me.jingbin.smb.BySMB;
import me.jingbin.smb.OnOperationFileCallback;

public class SmbClient implements OnOperationFileCallback {

    private static BySMB mBySMB;
    private static final String TAG = "SmbClient";
    private static final SmbClient mSmbClient = new SmbClient();

    public static void init() {
        BySMB.initProperty("6000", "3000");
        mBySMB = BySMB.with()
            .setConfig(
                "172.20.10.2",       // ip
                "",// 用户名
                "",// 密码
                "smb"// 共享文件夹名
            )
            .setReadTimeOut(60)
            .setSoTimeOut(180)
            .build();

    }

    public static void upload(File file) {
        mBySMB.writeToFile(file, mSmbClient);
    }

    @Override
    public void onFailure(@NonNull String s) {
        Log.d(TAG, "onFailure: " + s);
    }

    @Override
    public void onSuccess() {
        Log.d(TAG, "onSuccess() called");
    }
}
