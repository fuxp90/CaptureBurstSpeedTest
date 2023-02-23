package com.cdts.synccapture;

import static android.hardware.camera2.CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE;
import static android.hardware.camera2.CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED;
import static android.hardware.camera2.CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

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
    private Fmt mCaptureFormat = Fmt.RAW10;

    private static final boolean NeedCheckAFState = false;
    private Status mStatus = Status.Closed;
    private Size mSize;
    private static final int MaxImagesBuffer = 50;
    private static final int CAPTURE_FPS = 8;
    private int mJpegQuality = 95;

    private Capture3AMode m3AMode = Capture3AMode.Manual;
    private final ManualParameter mManualParameter = ManualParameter.getManualParameter();

    public CaptureMode getCaptureMode() {
        return mCaptureMode;
    }

    public Fmt getFmt() {
        return mCaptureFormat;
    }

    private int mAfState = CaptureResult.CONTROL_AF_STATE_INACTIVE;
    private Surface mPreviewSurface;
    private int mRequestRate = CAPTURE_FPS;

    public void setPreviewSurface(Surface surface) {
        mPreviewSurface = surface;
    }

    public int getRequestRate() {
        return mRequestRate;
    }

    public void setRequestRate(int requestRate) {
        mRequestRate = requestRate;
        Log.d(TAG, "setRequestRate: " + mRequestRate);
    }

    public int getJpegQuality() {
        return mJpegQuality;
    }

    public void setJpegQuality(int jpegQuality) {
        mJpegQuality = jpegQuality;
        Log.d(TAG, "setJpegQuality: " + mJpegQuality);
    }

    public enum Capture3AMode {
        Auto, Manual
    }

    public enum Status {
        Closed, Closing, Opened, Opening, Configured, Configuring, Capturing, Idle, Error, AFChecking, AFChecked, Previewing
    }

    private List<OnFmtChangedListener> mOnFmtChangedListener = new LinkedList<>();
    private Timer mTimer;
    public static final long NS = 1000_000_000;

    private final Timer[] mTimerArray = new Timer[2];

    private long mRequestTime;

    private final Map<Long, Long> mRequestTimeMap = Collections.synchronizedMap(new TreeMap<>());

    public interface OnFmtChangedListener {
        void OnFmtChanged(Context context, Fmt fmt);
    }

    public void addOnFmtChangedListener(OnFmtChangedListener onFmtChangedListener) {
        if (!mOnFmtChangedListener.contains(onFmtChangedListener)) {
            mOnFmtChangedListener.add(onFmtChangedListener);
        }
    }

    public void removeOnFmtChangedListener(OnFmtChangedListener onFmtChangedListener) {
        mOnFmtChangedListener.remove(onFmtChangedListener);
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

    public void setImageFormat(Fmt fmt) {
        if (mCaptureFormat != fmt) {
            mCaptureFormat = fmt;
            mSize = getImageSupportSize().get(0);
            Log.d(TAG, "setImageFormat: " + mCaptureFormat + " mSize:" + mSize);
            config();
            mOnFmtChangedListener.forEach(onFmtChangedListener -> onFmtChangedListener.OnFmtChanged(mContext, fmt));
        }
        Log.d(TAG, "setImageFormat: " + fmt);
    }


    public void set3AMode(Capture3AMode m3AMode) {
        this.m3AMode = m3AMode;
    }

    public enum Fmt {
        JPEG(ImageFormat.JPEG), RAW10(ImageFormat.RAW10), RAW_SENSOR(ImageFormat.RAW_SENSOR), YUV(ImageFormat.YUV_420_888);
        private final int mFmt;

        private List<Size> mSupportSize;

        Fmt(int i) {
            mFmt = i;
        }

        public List<Size> getSupportSize() {
            return mSupportSize;
        }

        public int getFmt() {
            return mFmt;
        }
    }

    public enum CaptureMode {
        CaptureOneByOne(true), CaptureBurst(false), CaptureRepeating(false), CaptureFixRate(true), CaptureMultiThread(true), CaptureOnAhead(true);

        long mStartTime;
        long mEndTime;
        int mCaptureSendNumber;
        int mImageReceivedNumber;
        final boolean mSupportRecordRequestTimeDelay;

        static final int mBurstNumber = 15;

        static CameraCallback mCameraCallback;

        CaptureMode(boolean isSupportRecordRequestTimeDelay) {
            mSupportRecordRequestTimeDelay = isSupportRecordRequestTimeDelay;
        }

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

        public boolean isSupportRecordRequestTimeDelay() {
            return mSupportRecordRequestTimeDelay;
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
        initialized();
    }

    private void initialized() {
        String id = "0";
        CameraCharacteristics characteristics = null;
        try {
            characteristics = mManager.getCameraCharacteristics(id);
            if (mSize == null) {
                StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                for (Fmt value : Fmt.values()) {
                    Size[] sizes = configurationMap.getOutputSizes(value.mFmt);
                    Arrays.sort(sizes, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
                    value.mSupportSize = Arrays.asList(sizes);
                    Log.d(TAG, id + " " + value + " : " + Arrays.toString(sizes));
                }
                mSize = mCaptureFormat.mSupportSize.get(0);
            }
            mManualParameter.initialize(mContext, characteristics);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public Capture3AMode get3AMode() {
        return m3AMode;
    }

    public boolean isTestRunning() {
        return isTestRunning;
    }

    public List<Size> getImageSupportSize() {
        return mCaptureFormat.getSupportSize();
    }

    public ManualParameter getManualParameter() {
        return mManualParameter;
    }

    private List<CaptureRequest> buildRequest(int num) {
        List<CaptureRequest> requestList = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(mImageSurface);
                builder.set(CaptureRequest.JPEG_QUALITY, (byte) mJpegQuality);
                if (m3AMode == Capture3AMode.Auto) {
                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                } else {
                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mManualParameter.mExposureTime);
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, mManualParameter.mSensitivity);
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mManualParameter.mFocusDistance);

                    mManualParameter.restoreAwbParameter(builder);
                }
                int send = mCaptureMode == CaptureMode.CaptureBurst ? mCaptureMode.mCaptureSendNumber - CaptureMode.mBurstNumber : mCaptureMode.mCaptureSendNumber;
                builder.setTag(send + i);
                requestList.add(builder.build());

                if (mCaptureMode.isSupportRecordRequestTimeDelay()) {
                    recordRequestTimeDelay();
                }

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        return requestList;
    }

    public Map<Long, Long> getRequestTimeMap() {
        return mRequestTimeMap;
    }

    public void stopCaptureBurst() {
        Log.e(TAG, "stopCaptureBurst");
        if (isStatusOf(Status.Capturing)) {
            changeStatus(Status.Idle);
            mRequestTime = 0;
            mAfState = CaptureResult.CONTROL_AF_STATE_INACTIVE;
            isTestRunning = false;
            checkTestEndCondition();
        }
    }


    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            int sendNum = (int) request.getTag();
            Log.d(TAG, "onCaptureStarted sendNum " + sendNum + " timestamp:" + timestamp);

            if (mCaptureMode == CaptureMode.CaptureOnAhead && mStatus == Status.Capturing) {
                try {
                    mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                    mCaptureMode.addRequestNumber(1);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }


        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            int sendNum = (int) request.getTag();
            int q = result.get(CaptureResult.JPEG_QUALITY);
            Log.d(TAG, "onCaptureCompleted sendNum " + sendNum + " timestamp:" + result.get(CaptureResult.SENSOR_TIMESTAMP) + " JPEG_QUALITY:" + q);
        }
    };

    private boolean checkAf() {

        if (!NeedCheckAFState) return true;
        Log.d(TAG, "checkAf: mStatus:" + mStatus + " m3AMode:" + m3AMode);
        if (m3AMode == Capture3AMode.Auto) return true;

        if (isStatusOf(Status.AFChecking)) return false;

        if (isStatusOf(Status.AFChecked)) return true;
        changeStatus(Status.AFChecking);
        switch (mAfState) {
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                try {
                    mCameraSession.stopRepeating();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                return true;
            default:
                try {
                    CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    builder.addTarget(mImageSurface);
                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mManualParameter.mExposureTime);
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, mManualParameter.mSensitivity);
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                    mCameraSession.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            Log.d(TAG, "checkAf afState: " + afState);
                            if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                try {
                                    changeStatus(Status.AFChecked);
                                    mCameraSession.stopRepeating();
                                    mHandler.postDelayed(() -> {
                                        Log.d(TAG, "checkAf startCaptureBurst by AFChecked");
                                        startCaptureBurst(mCaptureMode);
                                    }, 500);

                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                return false;
        }
    }

    public interface OnManual3AChangedListener {
        void onManual3AChanged(ManualParameter parameter);
    }

    public void startPreview(OnManual3AChangedListener listener) {
        CaptureRequest.Builder builder = null;
        try {
            changeStatus(Status.Previewing);
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mPreviewSurface);
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mCameraSession.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    mManualParameter.mExposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    mManualParameter.mSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    mManualParameter.mFocusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                    mManualParameter.mAwbAdjust = 0;
                    int awb = result.get(CaptureResult.CONTROL_AWB_STATE);
                    if (awb == CaptureResult.CONTROL_AWB_STATE_CONVERGED || awb == CaptureResult.CONTROL_AWB_STATE_LOCKED) {
                        mManualParameter.saveAwbParameter(result);
                    }
                    if (listener != null) {
                        listener.onManual3AChanged(mManualParameter);
                    }

                }
            }, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void update3AParameter() {
        CaptureRequest.Builder builder = null;
        try {
            changeStatus(Status.Previewing);
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mPreviewSurface);
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mManualParameter.mExposureTime);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, mManualParameter.mSensitivity);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mManualParameter.mFocusDistance);
            mManualParameter.restoreAwbParameter(builder);
            mCameraSession.setRepeatingRequest(builder.build(), null, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startCaptureBurst(CaptureMode captureMode) {
        Log.e(TAG, "startCaptureBurst: " + captureMode);

        mRequestTimeMap.clear();
        mCaptureMode = captureMode;
        if (!checkAf()) {
            return;
        }

        changeStatus(Status.Capturing);
        isTestRunning = true;
        if (mCameraSession != null) {
            mCaptureMode.reset();
            mCameraCallback.onTestStart(captureMode, System.currentTimeMillis());
            try {
                switch (captureMode) {
                    case CaptureOneByOne:
                    case CaptureOnAhead:
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
                    case CaptureFixRate:
                        if (mTimer != null) {
                            mTimer.cancel();
                        }
                        mTimer = new Timer();
                        mTimer.scheduleAtFixedRate(new TimerTask() {
                            @Override
                            public void run() {
                                try {
                                    mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                                    mCaptureMode.addRequestNumber(1);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                        }, 0, (int) (1000f / mRequestRate));
                        break;

                    case CaptureMultiThread:
                        for (int i = 0; i < mTimerArray.length; i++) {
                            int period = (int) (1000f / mRequestRate);
                            if (mTimerArray[i] != null) {
                                mTimerArray[i].cancel();
                                mTimerArray[i] = null;
                            }
                            mTimerArray[i] = new Timer();
                            mTimerArray[i].scheduleAtFixedRate(new TimerTask() {
                                @Override
                                public void run() {
                                    try {
                                        mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                                        mCaptureMode.addRequestNumber(1);
                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }, (long) i * period / 2, period);
                        }

                        break;

                }
                captureMode.startRecordTime();
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Log.e(TAG, "startJpegBurstTest: ", e.fillInStackTrace());
            }
        }
    }

    void recordRequestTimeDelay() {
        long last = mRequestTime;
        mRequestTime = System.currentTimeMillis();
        if (last != 0) {
            long key = mRequestTime - last;
            Long count = mRequestTimeMap.get(key);
            if (count == null) {
                mRequestTimeMap.put(key, 1L);
            } else {
                mRequestTimeMap.put(key, ++count);
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
            case CaptureFixRate:
                // ignore
                break;
        }
    }

    public boolean checkTestEndCondition() {
        if (mCaptureMode.isComplete() || mCaptureMode == CaptureMode.CaptureRepeating || mCaptureMode == CaptureMode.CaptureFixRate || mCaptureMode == CaptureMode.CaptureOnAhead || mCaptureMode == CaptureMode.CaptureMultiThread) {
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


                case CaptureMultiThread:
                    for (int i = 0; i < mTimerArray.length; i++) {
                        if (mTimerArray[i] != null) {
                            mTimerArray[i].cancel();
                            mTimerArray[i] = null;
                        }
                    }
                case CaptureFixRate:
                    if (mTimer != null) {
                        mTimer.cancel();
                        mTimer = null;
                        Log.d(TAG, "checkTestEndCondition: mTimerTask.cancel() ");
                    }

                case CaptureBurst:
                case CaptureOnAhead:
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

    public Size getSize() {
        return mSize;
    }

    public void setSize(Size size) {
        if (mSize != null && !mSize.equals(size)) {
            mSize = size;
            Log.d(TAG, "setSize: " + mSize);
            config();
        }
    }

    public void openCamera(String id, Size size) {
        if (size != null) {
            mSize = size;
        }
        if (mContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        initialized();
        if (isStatusOf(Status.Opening, Status.Closing)) {
            Log.e(TAG, "openCamera refused by error status: " + mStatus);
            return;
        }
        try {
            Log.d(TAG, "openCamera: " + mSize + " fmt:" + mCaptureFormat + ", mCaptureMode:" + mCaptureMode);
            if (mOnFmtChangedListener != null) {
                mOnFmtChangedListener.forEach(onFmtChangedListener -> onFmtChangedListener.OnFmtChanged(mContext, mCaptureFormat));
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
                mImageReader = ImageReader.newInstance(mSize.getWidth(), mSize.getHeight(), mCaptureFormat.mFmt, MaxImagesBuffer);
                mImageReader.setOnImageAvailableListener(this::onImageReceived, mHandler);
                final Surface surface = mImageReader.getSurface();
                mImageSurface = surface;
                List<Surface> list = new ArrayList<>();
                if (mPreviewSurface != null) {
                    list.add(mPreviewSurface);
                } else {
                    list.add(surface);
                }
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
        if (mCameraDevice == null) {
            Log.e(TAG, "closeCamera return mCameraDevice :" + null);
            return;
        }
        mOnFmtChangedListener.clear();
        Log.e(TAG, "closeCamera E: mCameraDevice:" + mCameraDevice);
        changeStatus(Status.Closing);
        mHandler.post(() -> {
            try {
                if (mCameraSession != null) {
                    mCameraSession.close();
                    mCameraSession = null;
                }
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }

                if (mImageReader != null) {
                    mImageReader.close();
                    mImageReader = null;
                }

                if (mImageSurface != null) {
                    mImageSurface.release();
                    mImageSurface = null;
                }

                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
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

    public static class ManualParameter {
        private static final ManualParameter mManualParameter = new ManualParameter();
        public Long mExposureTime;
        public Integer mSensitivity;
        public Float mMinFocusDistance;

        public Float mFocusDistance;

        public MeteringRectangle[] mAwbMeteringRectangles;
        public int mAwbAdjust;
        private int mAwbAberrationMode;
        private RggbChannelVector mAwbColorCorrectionGains;
        private RggbChannelVector mAwbColorCorrectionGainsAdjust;
        private ColorSpaceTransform mAwbColorSpaceTransform;
        private int mCorrectionMode;

        Range<Long> mExposureTimeRange;
        Range<Integer> mSensitivityRange;
        float[] mFocalLengths;

        Integer mFocusDistanceCalibration;
        String mFocusDistanceCalibrationStr;
        boolean isInitial = false;

        public static ManualParameter getManualParameter() {
            return mManualParameter;
        }

        void saveAwbParameter(TotalCaptureResult result) {
            mAwbMeteringRectangles = result.get(CaptureResult.CONTROL_AWB_REGIONS);
            mAwbAberrationMode = result.get(CaptureResult.COLOR_CORRECTION_ABERRATION_MODE);
            mAwbColorCorrectionGains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
            mAwbColorSpaceTransform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
            mCorrectionMode = result.get(CaptureResult.COLOR_CORRECTION_MODE);
        }

        private RggbChannelVector getAwbColorCompensationRggbVector() {
            if (mAwbColorCorrectionGains == null) return null;
            float new_r_gain = mAwbColorCorrectionGains.getRed() + (mAwbAdjust * 0.005f);
            float new_g_even_gain = mAwbColorCorrectionGains.getGreenEven();
            float new_g_odd_gain = mAwbColorCorrectionGains.getGreenOdd();
            float new_b_gain = mAwbColorCorrectionGains.getBlue() - (mAwbAdjust * 0.005f);
            return new RggbChannelVector(new_r_gain, new_g_even_gain, new_g_odd_gain, new_b_gain);
        }

        void restoreAwbParameter(CaptureRequest.Builder builder) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AWB_REGIONS, mAwbMeteringRectangles);
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, mAwbAberrationMode);
            if (mAwbAdjust == 0) {
                builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, mAwbColorCorrectionGains);
            } else {
                builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, getAwbColorCompensationRggbVector());
            }
            builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, mAwbColorSpaceTransform);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, mCorrectionMode);
        }

        private void initialize(Context context, CameraCharacteristics characteristics) {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (isInitial) return;
            isInitial = true;
            mExposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            mSensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            mFocalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            mMinFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            int calibration = characteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION);
            switch (calibration) {
                case LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED:
                    mFocusDistanceCalibrationStr = "UNCALIBRATED";
                    break;
                case LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE:
                    mFocusDistanceCalibrationStr = "APPROXIMATE";
                    break;
                case LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED:
                    mFocusDistanceCalibrationStr = "CALIBRATED";
                    break;
                default:
                    mFocusDistanceCalibrationStr = "unknown";
            }

            if (mExposureTime == null) mExposureTime = CameraController.NS / 50;
            if (mSensitivity == null)
                mSensitivity = (int) (0.5f * (mSensitivityRange.getUpper() - mSensitivityRange.getLower()) + mSensitivityRange.getLower());
            if (mFocusDistance == null) mFocusDistance = (float) (0.5f * (mMinFocusDistance));
            mAwbAdjust = 0;
            Log.d(TAG, toString());
        }

        public String getCurrentDesc() {
            StringBuilder builder = new StringBuilder();
            if (mExposureTime != null) {
                @SuppressLint("DefaultLocale") String s = String.format("(%.2fs)", mExposureTime * 1f / NS);
                builder.append("ManualExposureTime(Nanoseconds):").append(mExposureTime).append(s).append("\n");
            }
            if (mSensitivity != null)
                builder.append("ManualSensitivity:").append(mSensitivity.toString()).append("\n");
            if (mFocusDistance != null)
                builder.append("ManualFocusDistance:").append(mFocusDistance).append("\n");
            builder.append("ManualAWB:").append(mAwbAdjust).append("\n");
            return builder.toString();
        }


        public String getDesc(boolean is3AAuto) {

            StringBuilder builder = new StringBuilder();
            @SuppressLint("DefaultLocale") String s = String.format("(%.2fs~%.2fs)", mExposureTimeRange.getUpper() * 1f / NS, mExposureTimeRange.getLower() * 1f / NS);

            builder.append("SensorExposureTimeRange(Nanoseconds):").append(mExposureTimeRange.toString()).append(s).append("\n");
            builder.append("SensorSensitivityRange:").append(mSensitivityRange.toString()).append("\n");
            builder.append("OpticalZoom(Millimeters):").append(Arrays.toString(mFocalLengths)).append("\n");
            builder.append("MinimumFocusDistance(").append(mFocusDistanceCalibrationStr).append("):").append(mMinFocusDistance).append("\n");

            if (!is3AAuto) {
                if (mExposureTime != null)
                    builder.append("ManualExposureTime(Nanoseconds):").append(mExposureTime).append("\n");
                if (mSensitivity != null)
                    builder.append("ManualSensitivity:").append(mSensitivity.toString()).append("\n");
                if (mFocusDistance != null)
                    builder.append("ManualFocusDistance:").append(mFocusDistance).append("\n");
            }
            return builder.toString();
        }

        @Override
        public String toString() {
            return "ManualParameter{" + "mExposureTime=" + mExposureTime + ", mSensitivity=" + mSensitivity + ", mFocusDistance=" + mFocusDistance + ", mAwbAdjust=" + mAwbAdjust + ", mExposureTimeRange=" + mExposureTimeRange + ", mSensitivityRange=" + mSensitivityRange + ", mFocalLengths=" + Arrays.toString(mFocalLengths) + ", mMinFocusDistance=" + mMinFocusDistance + '}' + "@" + Integer.toHexString(hashCode());
        }
    }

}
