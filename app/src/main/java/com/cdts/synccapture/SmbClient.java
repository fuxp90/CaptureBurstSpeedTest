package com.cdts.synccapture;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.hierynomus.smbj.share.DiskShare;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import me.jingbin.smb.BySMB;
import me.jingbin.smb.OnOperationFileCallback;

public class SmbClient implements Runnable {

    private BySMB mBySMB;
    private static final String TAG = "SmbClient";
    private static final SmbClient mSmbClient = new SmbClient();

    private final Executor mExecutor = Executors.newFixedThreadPool(5);
    public static final String KEY_SMB_USR_NAME = "key_smb_usr_name";
    public static final String KEY_SMB_PWD = "key_smb_pwd";
    public static final String KEY_SMB_IP = "key_smb_ip";
    public static final String KEY_SMB_PORT = "key_smb_port";
    public static final String KEY_SMB_FOLDER = "key_smb_folder";

    private OnUploadSmbListener mListener;
    private final AtomicInteger mUploadCount = new AtomicInteger();

    private final LinkedBlockingQueue<File> mFileQueue = new LinkedBlockingQueue<>();

    private final Object mLock = new Object();

    public int getUploadCount() {
        return mUploadCount.get();
    }

    public interface OnUploadSmbListener {
        void onUploadComplete(int count);
    }

    public void start() {
        new Thread(this).start();
    }

    public static SmbClient getSmbClient() {
        return mSmbClient;
    }

    public void setListener(OnUploadSmbListener listener) {
        mListener = listener;
    }

    @Override
    public void run() {
        BySMB.initProperty("6000", "3000");
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
                ).setReadTimeOut(120).setSoTimeOut(180).setWriteTimeOut(120).build();
                Log.d(TAG, "init smb successful");
            }

            while (true) {
                File file = mFileQueue.take();
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.d(TAG, "take : " + mFileQueue.size() + ", " + file);
                            mBySMB.writeToFile(file, new MyOnOperationFileCallback(file));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }


    }

    public void upload(File file) {
        if (file != null) {
            mFileQueue.offer(file);
            Log.d(TAG, "offer : " + mFileQueue.size() + ", " + file);
        }
    }


    class MyOnOperationFileCallback implements OnOperationFileCallback {

        File mFile;

        MyOnOperationFileCallback(File f) {
            mFile = f;
        }

        @Override
        public void onFailure(String s) {
            Log.d(TAG, "onFailure: " + s);

            assert mBySMB.getConnectShare() != null;
            if (!mBySMB.getConnectShare().isConnected()) {
                mBySMB.init();
                upload(mFile);
            }
        }

        @Override
        public final void onSuccess() {
            onSuccess(mFile);
        }

        public void onSuccess(File file) {
            int a = mUploadCount.incrementAndGet();
            Log.d(TAG, "onSuccess() called " + a + ", " + file.getAbsolutePath());
            if (mListener != null) {
                mListener.onUploadComplete(a);
            }
        }

    }

    public void resetUploadCount() {
        mUploadCount.set(0);
    }

}
