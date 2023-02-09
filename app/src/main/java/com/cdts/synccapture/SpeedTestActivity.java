package com.cdts.synccapture;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.media.Image;
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
    private TextView mMemInfo;

    private CameraController.CaptureMode mCaptureSolution = CameraController.CaptureMode.CaptureRepeating;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Storage mStorage = CaptureApplication.getCaptureApplication().getStorage();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_test);

        mTestReceive = findViewById(R.id.test_receive);
        mTestSpeed = findViewById(R.id.test_result);
        mTestSend = findViewById(R.id.test_send);
        mTestSize = findViewById(R.id.test_size);
        mTestSolution = findViewById(R.id.test_solution);
        mTestTime = findViewById(R.id.test_time);
        mTestSolutionDetail = findViewById(R.id.test_solution_detail);
        mMemInfo = findViewById(R.id.test_mem_info);
        mMemInfo.setText(getString(R.string.mem_info, Storage.getMaxMemoryInfo()));

        findViewById(R.id.test_solution_select).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
            builder.setTitle(R.string.test_choose_mode);
            final CharSequence[] charSequence = new CharSequence[]{
                CameraController.CaptureMode.CaptureOneByOne.name(),
                CameraController.CaptureMode.CaptureBurst.name(),
                CameraController.CaptureMode.CaptureRepeating.name(),};

            int item = 0;
            for (int i = 0; i < charSequence.length; i++) {
                if (mCaptureSolution == CameraController.CaptureMode.valueOf(charSequence[i] + "")) {
                    item = i;
                    break;
                }
            }

            builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                mCaptureSolution = CameraController.CaptureMode.valueOf(charSequence[which].toString());
                mTestSolution.setText(getString(R.string.test_mode, mCaptureSolution));
                mTestSolutionDetail.setText(getResources().getStringArray(R.array.test_mode)[which]);
                if (mCameraController.isTestRunning()) {
                    Toast.makeText(getApplicationContext(), R.string.test_mode_changed, Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            });
            builder.show();
        });

        mButton = findViewById(R.id.test_button);
        mButton.setOnClickListener(v -> {
            if (!mCameraController.isTestRunning()) {
                mButton.setText(R.string.test_stop);
                mCameraController.startCaptureBurst(mCaptureSolution);
            } else {
                mButton.setText(R.string.test_start);
                mCameraController.stopCaptureBurst();
            }
        });
        mTestSolution.setText(getString(R.string.test_mode, mCaptureSolution));
        mTestSolutionDetail.setText(getResources().getStringArray(R.array.test_mode)[0]);
        mCameraController = ((CaptureApplication) getApplication()).getCameraController();
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
            public void onTestStart(CameraController.CaptureMode captureSolution, long start) {
                runOnUiThread(() -> {
                    resetTestView();
                });
                runOnUiThread(new TimeUpdateRunnable(start));
            }

            @Override
            public void onTestEnd(CameraController.CaptureMode captureMode) {
                runOnUiThread(() -> {
                    long time = captureMode.mEndTime - captureMode.mStartTime;
                    int total = captureMode.mImageReceivedNumber;
                    float fps = total / (time / 1000f);
                    mTestSpeed.setText(getString(R.string.test_result, fps));

                });
            }

            @Override
            public void onReceiveImage(int num, Image image) {
                runOnUiThread(() -> mTestReceive.setText(getString(R.string.test_receivedImage, num)));
            }

            @Override
            public void onSendRequest(int num) {
                runOnUiThread(() -> mTestSend.setText(getString(R.string.test_sendRequest, num)));
            }
        });
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
        if (mCameraController.isTestRunning()) {
            Toast.makeText(getApplicationContext(), R.string.test_fmt_changed, Toast.LENGTH_LONG).show();
            return true;
        }
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

        updateSize();
        if (mCameraController.isStatusOf(CameraController.Status.Idle)) {
            mButton.setEnabled(true);
        }
    }

    void openCamera() {
        mCameraController.openCamera(mCamId, updateSize());
    }

    @SuppressLint("SetTextI18n")
    Size updateSize() {
        List<Size> sizes = mCameraController.getImageSupportSize(mCamId);
        Size size = sizes.get(0);
        mTestSize.setText(getString(R.string.test_image_size, size.getWidth() + "x" + size.getHeight())
            + "(" + mCameraController.getFmt() + ")");
        return size;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraController.stopCaptureBurst();
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
}