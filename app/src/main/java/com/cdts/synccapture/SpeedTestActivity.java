package com.cdts.synccapture;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SpeedTestActivity extends AppCompatActivity {

    private CameraController mCameraController;
    private final String mCamId = "0";
    private Button mButton;
    private TextView mTestSize;
    private TextView mTestSolution;
    private TextView mTestTime;
    private TextView mTestSend;
    private TextView mTestReceive;
    private TextView mTestSpeed;
    private TextView mTestSolutionDetail;

    private CameraController.CaptureSolution mCaptureSolution = CameraController.CaptureSolution.CaptureOneByOne;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTestReceive = findViewById(R.id.test_receive);
        mTestSpeed = findViewById(R.id.test_result);
        mTestSend = findViewById(R.id.test_send);
        mTestSize = findViewById(R.id.test_size);
        mTestSolution = findViewById(R.id.test_solution);
        mTestTime = findViewById(R.id.test_time);
        mTestSolutionDetail = findViewById(R.id.test_solution_detail);

        findViewById(R.id.test_solution_select).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
            builder.setTitle(R.string.test_choose_solution);
            final CharSequence[] charSequence = new CharSequence[]{
                CameraController.CaptureSolution.CaptureOneByOne.name(),
                CameraController.CaptureSolution.CaptureBurst.name(),
                CameraController.CaptureSolution.CaptureRepeating.name(),};

            int item = 0;
            for (int i = 0; i < charSequence.length; i++) {
                if (mCaptureSolution == CameraController.CaptureSolution.valueOf(charSequence[i] + "")) {
                    item = i;
                    break;
                }
            }

            builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                mCaptureSolution = CameraController.CaptureSolution.valueOf(charSequence[which].toString());
                mTestSolution.setText(getString(R.string.test_solution, mCaptureSolution));
                mTestSolutionDetail.setText(getResources().getStringArray(R.array.test_solution)[which]);
                if (mCameraController.isTestRunning()) {
                    Toast.makeText(getApplicationContext(), R.string.test_solution_changed, Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            });
            builder.show();
        });

        mButton = findViewById(R.id.test_button);
        mButton.setOnClickListener(v -> {
            if (!mCameraController.isTestRunning()) {
                mButton.setText(R.string.test_stop);
                mCameraController.startJpegBurstTest(mCaptureSolution);
            } else {
                mButton.setText(R.string.test_start);
                mCameraController.stopJpegBurstTest();
            }
        });
        mTestSolution.setText(getString(R.string.test_solution, mCaptureSolution));
        mTestSolutionDetail.setText(getResources().getStringArray(R.array.test_solution)[0]);
        mCameraController = new CameraController(this);
        mCameraController.setCameraCallback(new CameraController.CameraCallback() {
            @Override
            public void onCameraOpened(CameraDevice cameraDevice) {

            }

            @Override
            public void onCameraClosed() {
                openCamera();
            }

            @Override
            public void onConfigured(CameraCaptureSession session) {
                runOnUiThread(() -> mButton.setEnabled(true));
            }

            @Override
            public void onTestStart(CameraController.CaptureSolution captureSolution, long start) {
                runOnUiThread(() -> {
                    resetTestView();
                });
                runOnUiThread(new TimeUpdateRunnable(start));
            }

            @Override
            public void onTestEnd(CameraController.CaptureSolution captureSolution) {
                runOnUiThread(() -> {
                    long time = captureSolution.mEndTime - captureSolution.mStartTime;
                    int total = captureSolution.mImageReceivedNumber;
                    float fps = total / (time / 1000f);
                    mTestSpeed.setText(getString(R.string.test_result, fps));

                });
            }

            @Override
            public void onReceiveImage(int num) {
                runOnUiThread(() -> mTestReceive.setText(getString(R.string.test_receivedImage, num)));
            }

            @Override
            public void onSendRequest(int num) {
                runOnUiThread(() -> mTestSend.setText(getString(R.string.test_sendRequest, num)));
            }
        });
        if (hasCameraPermission()) {
            openCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 0xff);
        }
        resetTestView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.speed_test_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.fmt_jpeg:
                mCameraController.setImageFormat(ImageFormat.JPEG);
                break;
            case R.id.fmt_raw_10:
                mCameraController.setImageFormat(ImageFormat.RAW10);
                break;
            case R.id.fmt_raw_sensor:
                mCameraController.setImageFormat(ImageFormat.RAW_SENSOR);
                break;
        }
        updateSize();
        mCameraController.closeCamera(true);
        return super.onOptionsItemSelected(item);
    }

    void resetTestView() {
        mTestSpeed.setText(R.string.test_result_unknow);
        mTestReceive.setText(R.string.test_receivedImage_unknow);
        mTestSend.setText(R.string.test_sendRequest_unknow);
    }

    void openCamera() {
        mCameraController.openCamera(mCamId, updateSize());
    }

    @SuppressLint("SetTextI18n")
    Size updateSize() {
        List<Size> sizes = mCameraController.getJpegSupportSize(mCamId);
        Size size = sizes.get(0);
        mTestSize.setText(getString(R.string.test_image_size, size.getWidth() + "x" + size.getHeight())
            + "(" + getFmt() + ")");
        return size;
    }

    String getFmt() {
        int fmt = mCameraController.getCaptureFormat();
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

    class TimeUpdateRunnable implements Runnable {

        long mStartTime;

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("mm:ss");
        Date mDate = new Date();

        public TimeUpdateRunnable(long startTime) {
            this.mStartTime = startTime;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            if (mCameraController.isTestRunning()) {
                long time = System.currentTimeMillis() - mStartTime;
                mDate.setTime(time);
                String str = mSimpleDateFormat.format(mDate);
                mTestTime.setText(getString(R.string.test_time) + str);
                mHandler.postDelayed(this, 1000);
            }
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (hasCameraPermission()) {
            openCamera();
        } else {
            Toast.makeText(this, "Please give Permissions", Toast.LENGTH_LONG).show();
        }
    }

    boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraController != null) {
            mCameraController.closeCamera(false);
        }
    }
}