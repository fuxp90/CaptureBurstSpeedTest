package com.cdts.synccapture;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.text.format.Formatter;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class Storage {

    static final String TAG = "Storage";
    static final boolean WRITE_TIMESTAMP = false;
    private File mDir;

    private final List<byte[]> mImageBytes = new LinkedList<>();
    private final Executor mExecutor = Executors.newFixedThreadPool(10, new ThreadFactory() {
        int index = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("storage-thread#" + index++);
            return thread;
        }
    });

    public void setDir(File dir) {
        mDir = dir;
        Log.d(TAG, "setDir:" + dir.getAbsolutePath());
    }

    public interface OnImageSaveCompleteListener {
        void onSaveComplete(File file, int num, boolean successful);
    }

    private final AtomicInteger mSavedImageNum = new AtomicInteger();
    private OnImageSaveCompleteListener mOnImageSaveCompleteListener;

    public void setOnImageSaveCompleteListener(OnImageSaveCompleteListener onImageSaveCompleteListener) {
        mOnImageSaveCompleteListener = onImageSaveCompleteListener;
    }

    public void saveImageBuffer(Image image, long baseTime, boolean saveToFlash) {
        switch (image.getFormat()) {
            case ImageFormat.JPEG:
            case ImageFormat.RAW10:
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer();
                int len = buffer.remaining();
                long timestamp = image.getTimestamp() - baseTime;

                int timeLen = WRITE_TIMESTAMP ? 8 : 0;
                byte[] bytes = new byte[len + timeLen];
                buffer.get(bytes, timeLen, len);
                if (WRITE_TIMESTAMP) {
                    writeLong(bytes, 0, timestamp);
                }
                if (saveToFlash) {
                    saveToFlash(bytes, mSavedImageNum, timestamp);
                } else {
                    mImageBytes.add(bytes);
                }
                break;
            case ImageFormat.RAW_SENSOR:
                break;
        }
    }


    private void saveToFlash(final byte[] data, AtomicInteger num, long timestamp) {
        mExecutor.execute(new SaveTask(data, num, mDir, timestamp, mOnImageSaveCompleteListener));
    }

    private static class SaveTask implements Runnable {
        private byte[] data;
        private final AtomicInteger num;
        private final long timestamp;
        private final File mDir;
        private final OnImageSaveCompleteListener mOnImageSaveCompleteListener;


        public SaveTask(byte[] data, AtomicInteger num, File file, long timestamp, OnImageSaveCompleteListener onImageSaveCompleteListener) {
            this.data = data;
            this.num = num;
            this.timestamp = timestamp;
            mDir = file;
            mOnImageSaveCompleteListener = onImageSaveCompleteListener;
        }

        @Override
        public void run() {
            int a = num.incrementAndGet();
            boolean successful = false;
            File file = new File(mDir, "image_" + a + "_" + +timestamp + ".raw");
            Log.d(TAG, "save E, num:" + a);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(data);
                fileOutputStream.close();
                successful = true;
            } catch (IOException e) {
                e.printStackTrace();
                successful = false;
                Log.e(TAG, "save failed: " + file.getAbsolutePath());
            } finally {
                data = null;
            }
            Log.d(TAG, "save successful: " + file.getAbsolutePath());
            if (mOnImageSaveCompleteListener != null) {
                mOnImageSaveCompleteListener.onSaveComplete(file, a, successful);
            }
        }
    }

    private void writeLong(byte[] writeBuffer, int offset, long v) {
        writeBuffer[0 + offset] = (byte) (v >>> 56);
        writeBuffer[1 + offset] = (byte) (v >>> 48);
        writeBuffer[2 + offset] = (byte) (v >>> 40);
        writeBuffer[3 + offset] = (byte) (v >>> 32);
        writeBuffer[4 + offset] = (byte) (v >>> 24);
        writeBuffer[5 + offset] = (byte) (v >>> 16);
        writeBuffer[6 + offset] = (byte) (v >>> 8);
        writeBuffer[7 + offset] = (byte) (v >>> 0);
    }

    private long readLong(byte[] readBuffer, int offset) {
        return (((long) readBuffer[0 + offset] << 56) + ((long) (readBuffer[1 + offset] & 255) << 48) + ((long) (readBuffer[2 + offset] & 255) << 40) + ((long) (readBuffer[3 + offset] & 255) << 32) + ((long) (readBuffer[4 + offset] & 255) << 24) + ((readBuffer[5 + offset] & 255) << 16) + ((readBuffer[6 + offset] & 255) << 8) + ((readBuffer[7 + offset] & 255) << 0));
    }

    private final static long MemMB = 1024 * 1024;

    public static String getMaxMemoryInfo() {
        StringBuilder stringBuilder = new StringBuilder();
        Runtime rt = Runtime.getRuntime();
        long freeMemory = rt.freeMemory();
        long totalMemory = rt.totalMemory();
        long maxMemory = rt.maxMemory();
        stringBuilder.append("Dalvik MaxMemory:").append(maxMemory / MemMB).append(" MB\n");
        stringBuilder.append("Dalvik FreeMemory:").append(freeMemory / MemMB).append(" MB\n");
        stringBuilder.append("Dalvik TotalMemory:").append(totalMemory / MemMB).append(" MB\n");

        ActivityManager activityManager = (ActivityManager) CaptureApplication.getCaptureApplication().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);
        stringBuilder.append("Device RAM:").append(info.availMem / MemMB).append(" MB\n");
        stringBuilder.append("Device Free:").append(info.availMem / MemMB).append(" MB\n");
        Log.e(TAG, stringBuilder.toString());
        return stringBuilder.toString();
    }

    public static class MemInfo {
        int mTotal;
        int mUsed;
    }
}
