package com.cdts.synccapture;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class Storage implements CameraController.OnFmtChangedListener {

    private static final String TAG = "Storage";
    private static final boolean WRITE_TIMESTAMP = false;
    private File mDir;

    private final static Storage sStorage = new Storage();

    private SaveType mSaveType = SaveType.RAM;
    //saves frames with devicename_recordingname_size_frameNumber_frametime.xxx
    private static final String ImageNamePattern = Build.MODEL.replaceAll(" ", "") + "_%s_[%dx%d][row_stride=%d]_%s_%s.%s";

    private static int WIDTH;
    private static int HEIGHT;
    private static int ROW_STRIDE;

    public SaveType getSaveType() {
        return mSaveType;
    }

    public int getStorageNum() {
        return mSavedImageNum.get();
    }

    public enum SaveType {
        RAM, Flash;
    }


    public void setSaveType(SaveType saveType) {
        mSaveType = saveType;
        Log.d(TAG, "setSaveType: " + saveType);
    }

    private OnImageSaveDirCreateListener mImageSaveDirCreateListener;

    public interface OnImageSaveDirCreateListener {
        void onImageDirCreated(File file);
    }

    public static Storage getStorage() {
        return sStorage;
    }

    private final List<ByteBuffer> mImageBuffer = new LinkedList<>();
    private final Executor mExecutor = Executors.newFixedThreadPool(10, new ThreadFactory() {
        int index = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("storage-thread#" + index++);
            return thread;
        }
    });


    public void setImageSaveDirCreateListener(OnImageSaveDirCreateListener saveDirCreateListener) {
        mImageSaveDirCreateListener = saveDirCreateListener;
    }

    public void resetSaveNum() {
        mImageBuffer.clear();
        int last = mSavedImageNum.getAndSet(0);
        Log.d(TAG, "Reset SavedImageNum:" + last + " to 0");
    }

    private void setDir(File dir) {
        mDir = dir;
        Log.d(TAG, "setDir:" + dir.getAbsolutePath());
        if (mImageSaveDirCreateListener != null) {
            mImageSaveDirCreateListener.onImageDirCreated(dir);
        }
    }

    public File getDir() {
        return mDir;
    }

    public interface OnImageSaveCompleteListener {
        void onSaveComplete(File file, int num, boolean successful);
    }

    private final AtomicInteger mSavedImageNum = new AtomicInteger();
    private final List<OnImageSaveCompleteListener> mOnImageSaveCompleteListener = new LinkedList<>();

    public void addOnImageSaveCompleteListener(OnImageSaveCompleteListener onImageSaveCompleteListener) {
        mOnImageSaveCompleteListener.add(onImageSaveCompleteListener);
    }

    public void removeOnImageSaveCompleteListener(OnImageSaveCompleteListener onImageSaveCompleteListener) {
        mOnImageSaveCompleteListener.remove(onImageSaveCompleteListener);
    }

    public void saveImageBuffer(Image image, long baseTime) {
        boolean saveToFlash = mSaveType == SaveType.Flash;
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        WIDTH = image.getWidth();
        HEIGHT = image.getHeight();
        ROW_STRIDE = rowStride;
        Log.d(TAG, "rowStride: " + rowStride);
        long timestamp = baseTime <= 0 ? 0 : image.getTimestamp() - baseTime;
        if (saveToFlash) {
            int plans = image.getFormat() == ImageFormat.YUV_420_888 ? 2 : 1;
            ByteBuffer[] buffers = new ByteBuffer[plans];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = image.getPlanes()[i].getBuffer();
            }
            saveToFlash(buffers, mSavedImageNum, timestamp);
        } else {
            mImageBuffer.add(buffer);
            int a = mSavedImageNum.incrementAndGet();
            for (OnImageSaveCompleteListener l : mOnImageSaveCompleteListener) {
                l.onSaveComplete(null, a, true);
            }
        }
    }

    @Override
    public void OnFmtChanged(Context context, CameraController.Fmt fmt) {
        File dir = new File(context.getExternalCacheDir(), fmt.name());
        if (!dir.exists()) {
            try {
                boolean b = dir.mkdir();
                if (b) {
                    Log.d(TAG, "make image storage dir: " + dir.getAbsolutePath() + " successful");
                } else {
                    Log.e(TAG, "make image storage dir: " + dir.getAbsolutePath() + " failed");
                }
                setDir(dir);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "make image storage dir: " + dir.getAbsolutePath() + " failed");
            }
        } else {
            setDir(dir);
        }
    }

    private void saveToFlash(final byte[] data, AtomicInteger num, long timestamp) {
        mExecutor.execute(new SaveTask(data, num, mDir, timestamp, mOnImageSaveCompleteListener));
    }

    private void saveToFlash(final ByteBuffer[] data, AtomicInteger num, long timestamp) {
        mExecutor.execute(new SaveTask(data, num, mDir, timestamp, mOnImageSaveCompleteListener));
    }

    private static class SaveTask implements Runnable {
        private byte[] data;
        private final AtomicInteger num;
        private final long timestamp;
        private final File mDir;
        private ByteBuffer[] buffer;
        private final List<OnImageSaveCompleteListener> mOnImageSaveCompleteListener;


        public SaveTask(byte[] data, AtomicInteger num, File file, long timestamp, List<OnImageSaveCompleteListener> onImageSaveCompleteListener) {
            this.data = data;
            this.num = num;
            this.timestamp = timestamp;
            mDir = file;
            mOnImageSaveCompleteListener = onImageSaveCompleteListener;
        }

        public SaveTask(ByteBuffer[] data, AtomicInteger num, File file, long timestamp, List<OnImageSaveCompleteListener> onImageSaveCompleteListener) {
            this.buffer = data;
            this.num = num;
            this.timestamp = timestamp;
            mDir = file;
            mOnImageSaveCompleteListener = onImageSaveCompleteListener;
        }

        @Override
        public void run() {
            int a = num.incrementAndGet();
            boolean successful = false;
            String post = mDir.getName().toLowerCase(Locale.ROOT);
            //Pixel4_Record1_[4032x3024][row_stride=0]_5_0.jpeg
            String recordName = Utils.getSpf(Utils.KEY_RECORD_NAME, Utils.DEF_RECORD_NAME);
            String name = String.format(Locale.US, ImageNamePattern, recordName, WIDTH, HEIGHT, ROW_STRIDE,
                a, timestamp, post);
            File file = new File(mDir, name);
            Log.d(TAG, "save E, name:" + name);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                if (data != null) {
                    fileOutputStream.write(data);
                } else if (buffer != null) {
                    FileChannel channel = fileOutputStream.getChannel();
                    channel.write(buffer);
                    channel.close();
                    Arrays.stream(buffer).forEach(ByteBuffer::clear);
                }
                fileOutputStream.close();
                successful = true;
                Log.d(TAG, "save X successful: " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "save X failed: " + file.getAbsolutePath());
            } finally {
                data = null;
            }

            if (mOnImageSaveCompleteListener != null) {
                for (OnImageSaveCompleteListener l : mOnImageSaveCompleteListener) {
                    l.onSaveComplete(file, a, successful);
                }
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


}
