package com.cdts.synccapture;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Paint;
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
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

    private Status mStatus = Status.Closed;
    private Size mSize;
    private static final int MaxImagesBuffer = 50;
    private static final int CAPTURE_FPS = 24;
    private final ManualParameter mManualParameter = new ManualParameter();
    private Capture3AMode m3AMode = Capture3AMode.Manual;

    public CaptureMode getCaptureMode() {
        return mCaptureMode;
    }

    public Fmt getFmt() {
        return mCaptureFormat;
    }

    public enum Capture3AMode {
        Auto, Manual
    }

    public enum Status {
        Closed, Closing, Opened, Opening, Configured, Configuring, Capturing, Idle, Error
    }

    private OnFmtChangedListener mOnFmtChangedListener;
    private final Timer mTimer = new Timer("CaptureFixRate");
    private TimerTask mTimerTask;

    public interface OnFmtChangedListener {
        void OnFmtChanged(Context context, Fmt fmt);
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

    public void setImageFormat(Fmt fmt) {
        if (mCaptureFormat != fmt) {
            mCaptureFormat = fmt;
            config();
            if (mOnFmtChangedListener != null) {
                mOnFmtChangedListener.OnFmtChanged(mContext, fmt);
            }
        }
        Log.d(TAG, "setImageFormat: " + fmt);
    }


    public void set3AMode(Capture3AMode m3AMode) {
        this.m3AMode = m3AMode;
    }

    public enum Fmt {
        JPEG(ImageFormat.JPEG), RAW10(ImageFormat.RAW10), RAW_SENSOR(ImageFormat.RAW_SENSOR);
        private final int mFmt;

        Fmt(int i) {
            mFmt = i;
        }

        public int getFmt() {
            return mFmt;
        }
    }

    public enum CaptureMode {
        CaptureOneByOne(), CaptureBurst(), CaptureRepeating(), CaptureFixRate();

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

    public Capture3AMode get3AMode() {
        return m3AMode;
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
            Size[] sizes = configurationMap.getOutputSizes(mCaptureFormat.mFmt);
            Arrays.sort(sizes, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
            Log.d(TAG, id + " " + mCaptureFormat + " : " + Arrays.toString(sizes));

            mManualParameter.initialize(characteristics);
            return Arrays.asList(sizes);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
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

                if (m3AMode == Capture3AMode.Auto) {
                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                } else {
                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mManualParameter.mExposureTime);
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, mManualParameter.mSensitivity);
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mManualParameter.mFocusDistance);
                }
                int send = mCaptureMode == CaptureMode.CaptureBurst ? mCaptureMode.mCaptureSendNumber - CaptureMode.mBurstNumber : mCaptureMode.mCaptureSendNumber;
                builder.setTag(send + i);
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


    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
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
                    case CaptureFixRate:

                        if (mTimerTask != null) {
                            mTimerTask.cancel();
                            mTimerTask = null;
                        }
                        mTimerTask = new TimerTask() {
                            @Override
                            public void run() {
                                try {
                                    mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                                    mCaptureMode.addRequestNumber(1);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        mTimer.schedule(mTimerTask, 0, (int) (1000f / CAPTURE_FPS));
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
            case CaptureFixRate:
                // ignore
                break;
        }
    }

    public boolean checkTestEndCondition() {
        if (mCaptureMode.isComplete() || mCaptureMode == CaptureMode.CaptureRepeating ||
            mCaptureMode == CaptureMode.CaptureFixRate) {
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
                case CaptureFixRate:
                    if (mTimerTask != null) {
                        mTimerTask.cancel();
                        mTimerTask = null;
                        Log.d(TAG, "checkTestEndCondition: mTimerTask.cancel() ");
                    }

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

    public Size getSize() {
        return mSize;
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
                mOnFmtChangedListener.OnFmtChanged(mContext, mCaptureFormat);
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
        if (mCameraDevice == null) {
            Log.e(TAG, "closeCamera return mCameraDevice :" + null);
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

    static class ManualParameter {

        public Long mExposureTime;
        public Integer mSensitivity;
        public Float mFocusDistance;

        Range<Long> mExposureTimeRange;
        Range<Integer> mSensitivityRange;
        float[] mFocalLengths;
        Float mMinFocusDistance;

        void initialize(CameraCharacteristics characteristics) {
            mExposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            mSensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            mFocalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            mMinFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);


            Log.d(TAG, toString());
        }


        public String getDesc(boolean is3AAuto) {
            StringBuilder builder = new StringBuilder();
            builder.append("SensorExposureTimeRange(Nanoseconds):").append(mExposureTimeRange.toString()).append("\n");
            builder.append("SensorSensitivityRange:").append(mSensitivityRange.toString()).append("\n");
            builder.append("OpticalZoom(Millimeters):").append(Arrays.toString(mFocalLengths)).append("\n");
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
            return "ManualParameter{" +
                    "mExposureTime=" + mExposureTime +
                    ", mSensitivity=" + mSensitivity +
                    ", mFocusDistance=" + mFocusDistance +
                    ", mExposureTimeRange=" + mExposureTimeRange +
                    ", mSensitivityRange=" + mSensitivityRange +
                    ", mFocalLengths=" + Arrays.toString(mFocalLengths) +
                    ", mMinFocusDistance=" + mMinFocusDistance +
                    '}';
        }

        public void setupViewRange(View view) {
            int[] ints = {R.id.exposure_time_desc, R.id.sensitivity_desc, R.id.focus_distance_desc};
            int[] descId = {R.string.exposure_time_desc, R.string.sensitivity_desc, R.string.focus_distance_desc};
            String[] desc = {mExposureTimeRange.toString(), mSensitivityRange.toString(), "[0-" + mMinFocusDistance + "]"};

            for (int i = 0; i < ints.length; i++) {
                TextView tv = view.findViewById(ints[i]);
                tv.setText(view.getResources().getString(descId[i], desc[i]));
            }

        }

        public boolean saveInputParameter(View view) {
            int[] ints = {R.id.exposure_time, R.id.sensitivity, R.id.focus_distance};
            String[] values = new String[ints.length];
            for (int i = 0; i < ints.length; i++) {
                EditText tv = view.findViewById(ints[i]);
                values[i] = tv.getText().toString();
                Log.d(TAG, "saveInputParameter " + i + ":" + values[i]);
            }

            try {
                mExposureTime = Long.parseLong(values[0]);
                mSensitivity = Integer.parseInt(values[1]);
                mFocusDistance = Float.parseFloat(values[2]);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }
    }

}
