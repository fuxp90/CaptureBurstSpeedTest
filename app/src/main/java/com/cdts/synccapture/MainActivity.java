package com.cdts.synccapture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int TIME_SYNC_REQ_CODE = 0xfff;
    private long mImageBaseTime;
    private TextView mBaseTime;
    private TextView mCaptureNumber;
    private TextView mSaveNumber;
    private CameraController mCameraController;
    private Button mButton;
    private static final String TAG = "MainActivity";
    private TextView mCaptureTime;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Storage mStorage = CaptureApplication.getCaptureApplication().getStorage();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBaseTime = findViewById(R.id.baseTime);
        mBaseTime.setText(getString(R.string.base_time, mImageBaseTime));

        mCaptureNumber = findViewById(R.id.capture_number);
        mCaptureNumber.setText(getString(R.string.capture_number, 0));
        mCaptureTime = findViewById(R.id.time_esc);
        mCaptureTime.setText(getString(R.string.time_esc, "0"));

        mSaveNumber = findViewById(R.id.save_number);
        mSaveNumber.setText(getString(R.string.save_number, 0));
        mCameraController = ((CaptureApplication) getApplication()).getCameraController();
        mButton = findViewById(R.id.start_capture);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImageBaseTime == 0) {
                    Toast.makeText(getApplication(), R.string.need_set_base_time, Toast.LENGTH_LONG).show();
                    return;
                }
                if (!mCameraController.isTestRunning()) {
                    mButton.setText(R.string.stop_capture);
                    mCameraController.startCaptureBurst(CameraController.CaptureMode.CaptureRepeating);
                } else {
                    mButton.setText(R.string.start_capture);
                    mCameraController.stopCaptureBurst();
                }
            }
        });

        if (hasCameraPermission()) {
            openCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 0xff);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        mStorage.setOnImageSaveCompleteListener((file, a,successful) -> {
            runOnUiThread(() -> mSaveNumber.setText(getString(R.string.save_number, a)));
        });
        mCameraController.setCameraCallback(new CameraController.CameraCallback() {
            @Override
            public void onCameraOpened(CameraDevice cameraDevice) {

            }

            @Override
            public void onCameraClosed() {

            }

            @Override
            public void onConfigured(CameraCaptureSession session) {

            }

            @Override
            public void onTestStart(CameraController.CaptureMode captureSolution, long time) {
                runOnUiThread(new TimeUpdateRunnable(time));
            }

            @Override
            public void onTestEnd(CameraController.CaptureMode captureSolution) {

            }

            @Override
            public void onSendRequest(int num) {

            }

            @Override
            public void onReceiveImage(int num, Image image) {
                Log.d(TAG, "onReceiveImage: " + num + "," + image);
                runOnUiThread(() -> mCaptureNumber.setText(getString(R.string.capture_number, num)));
                mStorage.saveImageBuffer(image, mImageBaseTime, true);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraController.stopCaptureBurst();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraController.closeCamera(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (hasCameraPermission()) {
            openCamera();
        } else {
            Toast.makeText(this, "Please give Permissions", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    void openCamera() {
        Size size = mCameraController.getImageSupportSize("0").get(0);
        mCameraController.openCamera("0", size);
    }

    boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.test_capture_speed: {
                Intent intent = new Intent(this, SpeedTestActivity.class);
                startActivity(intent);
            }
            break;
            case R.id.test_audio_time_sync: {
                Intent intent = new Intent(this, TimeSyncActivity.class);
                startActivityForResult(intent, TIME_SYNC_REQ_CODE);
            }
            break;
            case R.id.about: {
                AlertDialog aboutDialog = new AlertDialog.Builder(this).create();
                aboutDialog.setTitle(R.string.app_name);
                aboutDialog.setMessage("Build date:" + BuildConfig.VERSION_NAME);
                aboutDialog.show();
            }
            break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TIME_SYNC_REQ_CODE && resultCode == RESULT_OK) {
            assert data != null;
            mImageBaseTime = data.getLongExtra("base_time", 0);
            mBaseTime.setText(getString(R.string.base_time, mImageBaseTime));
        }
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
                mCaptureTime.setText(getString(R.string.time_esc, str));
                mHandler.postDelayed(this, 1000);
            }
        }

    }
}
