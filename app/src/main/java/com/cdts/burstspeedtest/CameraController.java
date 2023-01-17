package com.cdts.burstspeedtest;

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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

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
            Size[] sizes = configurationMap.getOutputSizes(ImageFormat.JPEG);
            Arrays.sort(sizes, Comparator.comparingInt(o -> -o.getHeight() * o.getWidth()));
            Log.d(TAG, id + " getJpegSupportSize: " + Arrays.toString(sizes));
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
                builder.setTag(mCaptureSolution.mCaptureSendNumber);
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
                        mCameraSession.capture(buildRequest(1).get(0), null, mHandler);
                        solution.addRequestNumber(1);
                        break;
                    case CaptureRepeating:
                        mCameraSession.setRepeatingRequest(buildRequest(1).get(0), null, mHandler);
                        break;
                    case CaptureBurst:
                        solution.addRequestNumber(solution.getBurstNumber());
                        mCameraSession.captureBurst(buildRequest(solution.getBurstNumber()), null, mHandler);
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
                    mCameraSession.capture(buildRequest(1).get(0), null, mHandler);
                    mCaptureSolution.addRequestNumber(1);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                break;
            case CaptureBurst:
                if (mCaptureSolution.isComplete()) {
                    try {
                        mCameraSession.captureBurst(buildRequest(mCaptureSolution.getBurstNumber()), null, mHandler);
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
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {

            mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 30);
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


    public void closeCamera() {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
