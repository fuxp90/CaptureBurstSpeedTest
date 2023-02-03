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
    private CaptureSolution mCaptureSolution;
    private Surface mJpegSurface;

    private boolean isTestRunning;
    private static final String TAG = "CameraController";
    private ImageReader mImageReader;
    private int mCaptureFormat = ImageFormat.RAW10;

    public void setImageFormat(int fmt) {
        mCaptureFormat = fmt;
        Log.d(TAG, "setImageFormat: " + fmt);
    }

    public int getCaptureFormat() {
        return mCaptureFormat;
    }

    public enum CaptureSolution {
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
            mCameraCallback.onReceiveImage(mImageReceivedNumber);
            Log.d(TAG, "addReceivedNumber " + mImageReceivedNumber + ","
                + image.getWidth() + "x" + image.getWidth() + ",format:" + image.getFormat());
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
        void onCameraOpened(CameraDevice cameraDevice);

        void onCameraClosed();

        void onConfigured(CameraCaptureSession session);

        void onTestStart(CaptureSolution captureSolution, long time);

        void onTestEnd(CaptureSolution captureSolution);

        void onSendRequest(int num);

        void onReceiveImage(int num);
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

    public List<Size> getJpegSupportSize(String id) {
        try {
            CameraCharacteristics characteristics = mManager.getCameraCharacteristics(id);
            StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            int timestamp_source = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
            Log.e(TAG, "timestamp_source: " + timestamp_source);
            Size[] sizes = configurationMap.getOutputSizes(ImageFormat.JPEG);
            Arrays.sort(sizes, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
            Log.d(TAG, id + " getJpegSupportSize: " + Arrays.toString(sizes));

            Size[] rawSize = configurationMap.getOutputSizes(ImageFormat.RAW_PRIVATE);
            Arrays.sort(rawSize, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
            Log.d(TAG, id + " RAW_PRIVATE: " + Arrays.toString(rawSize));

            rawSize = configurationMap.getOutputSizes(ImageFormat.RAW_SENSOR);
            Arrays.sort(rawSize, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
            Log.d(TAG, id + " RAW_SENSOR: " + Arrays.toString(rawSize));

            rawSize = configurationMap.getOutputSizes(ImageFormat.RAW10);
            Arrays.sort(rawSize, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
            Log.d(TAG, id + " RAW10: " + Arrays.toString(rawSize));

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
                builder.addTarget(mJpegSurface);
                int send = mCaptureSolution == CaptureSolution.CaptureBurst
                    ? mCaptureSolution.mCaptureSendNumber - CaptureSolution.mBurstNumber
                    : mCaptureSolution.mCaptureSendNumber;
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

    public void stopJpegBurstTest() {
        Log.e(TAG, "stopJpegBurstTest");
        isTestRunning = false;
        checkTestEndCondition();
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

    public void startJpegBurstTest(CaptureSolution solution) {
        Log.e(TAG, "startJpegBurstTest: " + solution);
        mCaptureSolution = solution;
        isTestRunning = true;
        if (mCameraSession != null) {
            mCaptureSolution.reset();
            mCameraCallback.onTestStart(solution, System.currentTimeMillis());
            try {
                switch (solution) {
                    case CaptureOneByOne:
                        mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                        solution.addRequestNumber(1);
                        break;
                    case CaptureRepeating:
                        mCameraSession.setRepeatingRequest(buildRequest(1).get(0), mCaptureCallback, mHandler);
                        break;
                    case CaptureBurst:
                        solution.addRequestNumber(solution.getBurstNumber());
                        mCameraSession.captureBurst(buildRequest(solution.getBurstNumber()), mCaptureCallback, mHandler);
                        break;
                }
                solution.startRecordTime();
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
                Log.d(TAG, "ImageReceived " + mCaptureSolution.mImageReceivedNumber + " timestamp:" + timestamp);
                if (mCaptureSolution != null) {
                    mCaptureSolution.addReceivedNumber(image);
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

        switch (mCaptureSolution) {
            case CaptureOneByOne:
                try {
                    mCameraSession.capture(buildRequest(1).get(0), mCaptureCallback, mHandler);
                    mCaptureSolution.addRequestNumber(1);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                break;
            case CaptureBurst:
                if (mCaptureSolution.isComplete()) {
                    try {
                        mCameraSession.captureBurst(buildRequest(mCaptureSolution.getBurstNumber()), mCaptureCallback, mHandler);
                        mCaptureSolution.addRequestNumber(mCaptureSolution.getBurstNumber());
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
        if (mCaptureSolution.isComplete() || mCaptureSolution == CaptureSolution.CaptureRepeating) {
            mCaptureSolution.stopRecordTime();
            switch (mCaptureSolution) {
                case CaptureRepeating:
                    mCameraCallback.onTestEnd(mCaptureSolution);
                    try {
                        mCameraSession.stopRepeating();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    break;
                case CaptureBurst:
                case CaptureOneByOne:
                    mCameraCallback.onTestEnd(mCaptureSolution);
                    break;
            }
            return true;
        } else {
            return false;
        }
    }

    public void setCameraCallback(CameraCallback cameraCallback) {
        mCameraCallback = cameraCallback;
        CaptureSolution.mCameraCallback = cameraCallback;
    }

    public void openCamera(String id, Size size) {
        if (mContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {

            mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), mCaptureFormat, 30);
            mImageReader.setOnImageAvailableListener(this::onImageReceived, mHandler);
            final Surface surface = mImageReader.getSurface();
            mJpegSurface = surface;


            mManager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    if (mCameraCallback != null) {
                        mCameraCallback.onCameraOpened(cameraDevice);
                    }

                    List<Surface> list = new ArrayList<>();
                    list.add(surface);
                    try {
                        mCameraDevice.createCaptureSession(list, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                mCameraSession = session;
                                if (mCameraCallback != null) {
                                    mCameraCallback.onConfigured(mCameraSession);
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                            }
                        }, mHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    mCameraDevice = null;
                }

                @Override
                public void onError(CameraDevice cameraDevice, int i) {

                }
            }, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    public void closeCamera(boolean callback) {
        Log.e(TAG, "closeCamera: ");
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

            if (mJpegSurface != null) {
                mJpegSurface.release();
            }

            if (mCameraCallback != null && callback) {
                mCameraCallback.onCameraClosed();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
