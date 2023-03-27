package com.cdts.synccapture;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.hierynomus.smbj.share.DiskShare;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import me.jingbin.smb.BySMB;
import me.jingbin.smb.OnOperationFileCallback;

public class SmbClient implements OnOperationFileCallback, Runnable {

    private BySMB mBySMB;
    private static final String TAG = "SmbClient";
    private static final SmbClient mSmbClient = new SmbClient();

    Handler mHandler;
    HandlerThread mHandlerThread;
    boolean isClientRunning;
    boolean isInitProperty;
    boolean isCheckingClient;

    private final Executor mExecutor = Executors.newFixedThreadPool(5);
    public static final String KEY_SMB_USR_NAME = "key_smb_usr_name";
    public static final String KEY_SMB_PWD = "key_smb_pwd";
    public static final String KEY_SMB_IP = "key_smb_ip";
    public static final String KEY_SMB_PORT = "key_smb_port";
    public static final String KEY_SMB_FOLDER = "key_smb_folder";

    private final List<File> mPendingFiles = new ArrayList<>();

    public void start() {
        mHandlerThread = new HandlerThread("smb-thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    public static SmbClient getSmbClient() {
        return mSmbClient;
    }

    private void checkClientRunning() {
        mHandler.post(this);
    }

    @Override
    public void run() {
        if (!isClientRunning) {
            if (!isInitProperty) {
                BySMB.initProperty("6000", "3000");
                isInitProperty = true;
            }
            try {
                String name = Utils.getSpf(KEY_SMB_USR_NAME, "");
                String pwd = Utils.getSpf(KEY_SMB_PWD, "");
                String ip = Utils.getSpf(KEY_SMB_IP, null);
                String port = Utils.getSpf(KEY_SMB_PORT, null);
                String folder = Utils.getSpf(KEY_SMB_FOLDER, "");

                Log.d(TAG, "init smb name:" + name + " pwd:" + pwd + " ip:" + ip + " folder:" + folder);
                if (ip != null) {
                    mBySMB = BySMB.with().setConfig(ip,// ip
                        name,// 用户名
                        pwd,// 密码
                        folder// 共享文件夹名
                    ).setReadTimeOut(60).setSoTimeOut(180).build();
                    isClientRunning = true;
                    Log.d(TAG, "init smb successful");


                    Log.d(TAG, "start upload pending files " + mPendingFiles.size());
                    Iterator<File> iterator = mPendingFiles.iterator();
                    while (iterator.hasNext()) {
                        File file = iterator.next();
                        iterator.remove();
                        upload(file);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                isClientRunning = false;
                Log.e(TAG, "init smb failed", e.fillInStackTrace());
            }
        }
    }

    public void upload(File file) {
        if (file == null) return;
        if (isClientRunning && mBySMB != null) {
            Log.d(TAG, "upload: " + file.getAbsolutePath());
            mExecutor.execute(() -> {
                try {
                    mBySMB.writeToFile(file, mSmbClient);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } else {
            Log.d(TAG, "upload: " + file.getAbsolutePath() + " add to pending list");
            mPendingFiles.add(file);
            checkClientRunning();
        }
    }

    @Override
    public void onFailure(@NonNull String s) {
        Log.d(TAG, "onFailure: " + s);
        isClientRunning = false;
    }

    @Override
    public void onSuccess() {
        Log.d(TAG, "onSuccess() called");
    }
}
