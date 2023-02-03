package com.cdts.synccapture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
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

import java.sql.Time;

public class MainActivity extends AppCompatActivity {

    static final int TIME_SYNC_REQ_CODE = 0xfff;
    private long mImageBaseTime;
    private TextView mBaseTime;
    CameraController mCameraController;
    private Button mButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBaseTime = findViewById(R.id.baseTime);
        mBaseTime.setText(getString(R.string.base_time, mImageBaseTime));

        mCameraController = new CameraController(this);
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
            public void onTestStart(CameraController.CaptureSolution captureSolution, long time) {

            }

            @Override
            public void onTestEnd(CameraController.CaptureSolution captureSolution) {

            }

            @Override
            public void onSendRequest(int num) {

            }

            @Override
            public void onReceiveImage(int num) {

            }
        });

        if (hasCameraPermission()) {
            openCamera();
        }

        mButton = findViewById(R.id.start_capture);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImageBaseTime == 0) {
                    Toast.makeText(getApplication(), R.string.base_time_error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (!mCameraController.isTestRunning()) {
                    mButton.setText(R.string.stop_capture);
                    mCameraController.startImageBurstTest(CameraController.CaptureSolution.CaptureRepeating);
                } else {
                    mButton.setText(R.string.start_capture);
                    mCameraController.stopImageBurstTest();
                }
            }
        });
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
}
