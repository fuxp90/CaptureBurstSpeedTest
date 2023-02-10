package com.cdts.synccapture;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class CameraController {

    private CameraDevice mCameraDevice;
    private final CameraManager mManager;
    private final Context mContext;
    private final Handler mHandler;
    private CameraCallback mCameraCallback;
    private CameraCaptureSession mCameraSession;
    private CaptureMode mCaptureMode;
    private Surface mImageSurface;

    private boolean isTestRunning;
    private static final String TAG = "CameraController";
    private ImageReader mImageReader;
    private int mCaptureFormat = ImageFormat.RAW10;

    private Status mStatus = Status.Closed;
    private Size mSize;
    private static final int MaxImagesBuffer = 50;

    public enum Status {
        Closed, Closing, Opened, Opening, Configured, Configuring, Capturing, Idle, Error
    }

    private OnFmtChangedListener mOnFmtChangedListener;

    public interface OnFmtChangedListener {
        void OnFmtChanged(Context context, int fmt, String fmtStr);
    }

    public void setOnFmtChangedListener(OnFmtChangedListener onFmtChangedListener) {
        mOnFmtChangedListener = onFmtChangedListener;
    }

    private void changeStatus(Status status) {
        synchronized (this) {
            if (mStatus != status) {
                Log.e(TAG, "changeStatus: " + mStatus + "->" + status);
                mStatus = status;
            }
        }
    }

    public boolean isStatusOf(Status... statuses) {
        synchronized (this) {
            for (Status status : statuses) {
                if (status == mStatus) {
                    return true;
                }
            }
            return false;
        }
    }

    public void setImageFormat(int fmt) {
        if (mCaptureFormat != fmt) {
            mCaptureFormat = fmt;
            config();
            if (mOnFmtChangedListener != null) {
                mOnFmtChangedListener.OnFmtChanged(mContext, fmt, getFmt());
            }
        }
        Log.d(TAG, "setImageFormat: " + fmt);
    }

    public int getCaptureFormat() {
        return mCaptureFormat;
    }

    public enum CaptureMode {
        CaptureOneByOne(), CaptureRepeating(), CaptureBurst();

        long mStartTime;
        long mEndTime;
        int mCaptureSendNumber;
        int mImageReceivedNumber;

        static final int mBurstNumber = 15;

        static CameraCallback mCameraCallback;

        synchronized void addRequestNumber(int num) {
            mCaptureSendNumber += num;
            mCameraCallback.onSendRequest(mCaptureSendNumber);
            Log.d(TAG, "addRequestNumber " + mCaptureSendNumber + ", num:" + num);
        }

        synchronized void addReceivedNumber(Image image) {
            mImageReceivedNumber++;
            mCameraCallback.onReceiveImage(mImageReceivedNumber, image);
            Log.d(TAG, "addReceivedNumber " + mImageReceivedNumber + "," + image.getWidth() + "x" + image.getWidth() + ",format:" + image.getFormat());
        }

        synchronized void startRecordTime() {
            if (mStartTime == 0) {
                mStartTime = System.currentTimeMillis();
                Log.d(TAG, "startRecordTime");
            }
        }

        synchronized void stopRecordTime() {
            mEndTime = System.currentTimeMillis();
            Log.d(TAG, "stopRecordTime");
        }

        synchronized void reset() {
            mStartTime = 0;
            mEndTime = 0;
            mCaptureSendNumber = 0;
            mImageReceivedNumber = 0;
            Log.d(TAG, "CaptureSolution reset");
        }


        synchronized boolean isComplete() {
            return mCaptureSendNumber == mImageReceivedNumber;
        }

        public int getBurstNumber() {
            return mBurstNumber;
        }
    }

    public interface CameraCallback {
        void onCameraOpened(CameraController controller);

        void onCameraClosed();

        void onConfigured(CameraController controller);

        void onTestStart(CaptureMode captureSolution, long time);

        void onTestEnd(CaptureMode captureSolution);

        void onSendRequest(int num);

        void onReceiveImage(int num, Image image);
    }

    public CameraController(Context context) {
        mContext = context;
        mManager = context.getSystemService(CameraManager.class);
        HandlerThread thread = new HandlerThread("CameraHandler");
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }


    public boolean isTestRunning() {
        return isTestRunning;
    }

    public List<Size> getImageSupportSize(String id) {
        try {
            CameraCharacteristics characteristics = mManager.getCameraCharacteristics(id);
            StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);


            int timestamp_source = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
            Log.e(TAG, "timestamp_source: " + timestamp_source);
            Size[] sizes = configurationMap.getOutputSizes(mCaptureFormat);
            Arrays.sort(sizes, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
            Log.d(TAG, id + " " + getFmt() + " : " + Arrays.toString(sizes));

            return Arrays.asList(sizes);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }


    private List<CaptureRequest> buildRequest(int num) {
        List<CaptureRequest> requestList = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(mImageSurface);
                int send = mCaptureMode == CaptureMode.CaptureBurst ? mCaptureMode.mCaptureSendNumber - CaptureMode.mBurstNumber : mCaptureMode.mCaptureSendNumber;
                builder.setTag(send + i);
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                requestList.add(builder.build());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        return requestList;
    }

    public void stopCaptureBurst() {
        Log.e(TAG, "stopCaptureBurst");
        if (isStatusOf(Status.Capturing)) {
            changeStatus(Status.Idle);
            isTestRunning = false;
            checkTestEndCondition();
        }
    }


    CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            int sendNum = (int) request.getTag();
            Log.d(TAG, "onCaptureStarted sendNum " + sendNum + " timestamp:" + timestamp);
        }


        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            int sendNum = (int) request.getTag();
            Log.d(TAG, "onCaptureCompleted sendNum " + sendNum + " timestamp:" + result.get(CaptureResult.SENSOR_TIMESTAMP));
        }
    };

    public void startCaptureBurst(CaptureMode captureMode) {
        Log.e(TAG, "startCaptureBurst: " + captureMode);
        changeStatus(Status.Capturing);
        mCaptureMode = captureMode;
        isTestRunning = true;
        if (mCameraSession != null) {
            mCaptureMode.reset();
            mCameraCallback.onTestStart(captureMode, System.currentTimeMillis());
            try {
                switch (captureMode) {
                    case CaptureOneByOne:
                        mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                        captureMode.addRequestNumber(1);
                        break;
                    case CaptureRepeating:
                        mCameraSession.setRepeatingRequest(buildRequest(1).get(0), mCaptureCallback, mHandler);
                        break;
                    case CaptureBurst:
                        captureMode.addRequestNumber(captureMode.getBurstNumber());
                        mCameraSession.captureBurst(buildRequest(captureMode.getBurstNumber()), mCaptureCallback, mHandler);
                        break;
                }
                captureMode.startRecordTime();
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Log.e(TAG, "startJpegBurstTest: ", e.fillInStackTrace());
            }
        }
    }

    void onImageReceived(ImageReader reader) {
        try {
            Image image = reader.acquireNextImage();
            if (image != null) {
                long timestamp = image.getTimestamp();
                Log.d(TAG, "ImageReceived " + mCaptureMode.mImageReceivedNumber + " timestamp:" + timestamp);
                if (mCaptureMode != null) {
                    mCaptureMode.addReceivedNumber(image);
                }
                image.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!isTestRunning) {
            boolean b = checkTestEndCondition();
            Log.e(TAG, "checkTestEndCondition " + b);
            return;
        }

        switch (mCaptureMode) {
            case CaptureOneByOne:
                try {
                    mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                    mCaptureMode.addRequestNumber(1);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                break;
            case CaptureBurst:
                if (mCaptureMode.isComplete()) {
                    try {
                        mCameraSession.captureBurst(buildRequest(mCaptureMode.getBurstNumber()), mCaptureCallback, mHandler);
                        mCaptureMode.addRequestNumber(mCaptureMode.getBurstNumber());
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;

            case CaptureRepeating:
                // ignore
                break;
        }
    }

    boolean checkTestEndCondition() {
        if (mCaptureMode.isComplete() || mCaptureMode == CaptureMode.CaptureRepeating) {
            mCaptureMode.stopRecordTime();
            switch (mCaptureMode) {
                case CaptureRepeating:
                    mCameraCallback.onTestEnd(mCaptureMode);
                    try {
                        mCameraSession.stopRepeating();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    break;
                case CaptureBurst:
                case CaptureOneByOne:
                    mCameraCallback.onTestEnd(mCaptureMode);
                    break;
            }
            return true;
        } else {
            return false;
        }
    }

    public void setCameraCallback(CameraCallback cameraCallback) {
        mCameraCallback = cameraCallback;
        CaptureMode.mCameraCallback = cameraCallback;
    }

    public void openCamera(String id, Size size) {
        mSize = size;
        if (mContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (isStatusOf(Status.Opening, Status.Closing)) {
            Log.e(TAG, "openCamera refused by error status: " + mStatus);
            return;
        }
        try {

            if (mOnFmtChangedListener != null) {
                mOnFmtChangedListener.OnFmtChanged(mContext, mCaptureFormat, getFmt());
            }
            changeStatus(Status.Opening);
            mManager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    changeStatus(Status.Opened);
                    mCameraDevice = cameraDevice;
                    Log.e(TAG, "onOpened " + cameraDevice);
                    if (mCameraCallback != null) {
                        mCameraCallback.onCameraOpened(CameraController.this);
                    }
                    config();
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    Log.e(TAG, "onDisconnected " + cameraDevice);
                    mCameraDevice = null;
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                    super.onClosed(camera);
                    Log.e(TAG, "onClosed " + camera);
                }

                @Override
                public void onError(CameraDevice cameraDevice, int i) {
                    changeStatus(Status.Error);
                    Log.e(TAG, "onError " + cameraDevice + " err:" + i);
                }
            }, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void config() {
        if (isStatusOf(Status.Opened, Status.Capturing, Status.Idle, Status.Configured)) {
            changeStatus(Status.Configuring);
            try {
                if (mImageReader != null) {
                    mImageReader.close();
                }
                mImageReader = ImageReader.newInstance(mSize.getWidth(), mSize.getHeight(), mCaptureFormat, MaxImagesBuffer);
                mImageReader.setOnImageAvailableListener(this::onImageReceived, mHandler);
                final Surface surface = mImageReader.getSurface();
                mImageSurface = surface;
                List<Surface> list = new ArrayList<>();
                list.add(surface);
                mCameraDevice.createCaptureSession(list, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        changeStatus(Status.Configured);
                        mCameraSession = session;
                        Log.e(TAG, "onConfigured " + mCameraSession);
                        if (mCameraCallback != null) {
                            mCameraCallback.onConfigured(CameraController.this);
                        }
                        changeStatus(Status.Idle);
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        changeStatus(Status.Error);
                    }
                }, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public void closeCamera() {
        if (isStatusOf(Status.Opening, Status.Closing)) {
            Log.e(TAG, "closeCamera return by error status:" + mStatus);
            return;
        }
        Log.e(TAG, "closeCamera E: mCameraDevice:" + mCameraDevice);
        changeStatus(Status.Closing);
        mHandler.post(() -> {
            try {
                if (mCameraSession != null) {
                    mCameraSession.close();
                }
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                }

                if (mImageReader != null) {
                    mImageReader.close();
                }

                if (mImageSurface != null) {
                    mImageSurface.release();
                }
                changeStatus(Status.Closed);
                Log.e(TAG, "closeCamera X");
                if (mCameraCallback != null) {
                    mCameraCallback.onCameraClosed();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String getFmt() {
        int fmt = mCaptureFormat;
        switch (fmt) {
            case ImageFormat.JPEG:
                return "JPEG";
            case ImageFormat.RAW10:
                return "RAW10";
            case ImageFormat.RAW_SENSOR:
                return "RAW_SENSOR";
        }
        return "";
    }

}
