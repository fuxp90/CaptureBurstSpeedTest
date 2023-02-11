package com.cdts.synccapture;

import androidx.annotation.NonNull;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SpeedTestActivity extends BaseActivity {

    private CameraController mCameraController;
    private final String mCamId = "0";
    private Button mButton;
    private TextView mTestSize;
    private TextView mTestMode;
    private TextView mTestTime;
    private TextView mTestSend;
    private TextView mTestReceive;
    private TextView mTestSpeed;
    private TextView mTestModeDetail;
    private TextView mMemInfo;
    private TextView mTestFmt;
    private TextView mTestSaveNum;
    private TextView mTestStoragePath;
    private TextView mSaveType;

    private final static String TAG = "BaseActivity";
    private CameraController.CaptureMode mCaptureMode = CameraController.CaptureMode.CaptureRepeating;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Storage mStorage = Storage.getStorage();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_test);

        mTestReceive = findViewById(R.id.test_receive);
        mTestSpeed = findViewById(R.id.test_result);
        mTestSend = findViewById(R.id.test_send);
        mTestSize = findViewById(R.id.test_size);
        mTestMode = findViewById(R.id.test_solution);
        mTestTime = findViewById(R.id.test_time);
        mTestModeDetail = findViewById(R.id.test_solution_detail);
        mMemInfo = findViewById(R.id.test_mem_info);
        mTestFmt = findViewById(R.id.test_fmt);
        mTestSaveNum = findViewById(R.id.test_storage);
        mTestStoragePath = findViewById(R.id.test_storage_detail);
        mSaveType = findViewById(R.id.test_save_type);

        findViewById(R.id.test_solution_select).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
            builder.setTitle(R.string.test_choose_mode);
            final CharSequence[] charSequence = new CharSequence[]{
                    CameraController.CaptureMode.CaptureOneByOne.name(),
                    CameraController.CaptureMode.CaptureBurst.name(),
                    CameraController.CaptureMode.CaptureRepeating.name(),};

            int item = 0;
            for (int i = 0; i < charSequence.length; i++) {
                if (mCaptureMode == CameraController.CaptureMode.valueOf(charSequence[i] + "")) {
                    item = i;
                    break;
                }
            }

            builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                mCaptureMode = CameraController.CaptureMode.valueOf(charSequence[which].toString());
                mTestMode.setText(getString(R.string.test_mode, mCaptureMode));
                mTestModeDetail.setText(getResources().getStringArray(R.array.test_mode_detail)[which]);
                if (mCameraController.isTestRunning()) {
                    Toast.makeText(getApplicationContext(), R.string.test_mode_changed, Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            });
            builder.show();
        });

        findViewById(R.id.test_save_type_select).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
                builder.setTitle(R.string.test_choose_save_type);
                final CharSequence[] charSequence = new CharSequence[]{Storage.SaveType.RAM.name(), Storage.SaveType.Flash.name()};
                int item = 0;
                for (int i = 0; i < charSequence.length; i++) {
                    if (charSequence[i].equals(mStorage.getSaveType().name())) {
                        item = i;
                        break;
                    }
                }
                builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                    if (!isStorageComplete()) {
                        Toast.makeText(getApplicationContext(), R.string.test_save_type_changed, Toast.LENGTH_LONG).show();
                    } else {
                        Storage.SaveType saveType = Storage.SaveType.valueOf(charSequence[which].toString());
                        mSaveType.setText(getString(R.string.test_save_type, saveType));
                        mStorage.setSaveType(saveType);
                    }
                    dialog.dismiss();
                });
                builder.show();
            }
        });

        findViewById(R.id.test_fmt_select).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
                builder.setTitle(R.string.test_choose_fmt);
                final CharSequence[] charSequence = new CharSequence[]{"JPEG", "RAW_10", "RAW_SENSOR"};
                int item = 0;
                for (int i = 0; i < charSequence.length; i++) {
                    if (charSequence[i].equals(mCameraController.getFmt())) {
                        item = i;
                        break;
                    }
                }
                builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                    if (mCameraController.isTestRunning()) {
                        Toast.makeText(getApplicationContext(), R.string.test_fmt_changed, Toast.LENGTH_LONG).show();
                    } else {
                        mTestFmt.setText(getString(R.string.test_fmt, mCameraController.getFmt()));
                        int fmt = 0;
                        switch (which) {
                            case 0:
                                fmt = ImageFormat.JPEG;
                                break;
                            case 1:
                                fmt = ImageFormat.RAW10;
                                break;
                            case 2:
                                fmt = ImageFormat.RAW_SENSOR;
                                break;
                        }
                        mCameraController.setImageFormat(fmt);
                        updateSize();
                    }
                    dialog.dismiss();
                });
                builder.show();
            }
        });


        mButton = findViewById(R.id.test_button);
        mButton.setOnClickListener(v -> {
            if (!mCameraController.isTestRunning()) {
                mButton.setText(R.string.test_stop);
                mCameraController.startCaptureBurst(mCaptureMode);
            } else {
                mButton.setText(R.string.test_start);
                mCameraController.stopCaptureBurst();
            }
        });
        mTestMode.setText(getString(R.string.test_mode, mCaptureMode));
        mTestModeDetail.setText(getResources().getStringArray(R.array.test_mode_detail)[mCaptureMode.ordinal()]);
        mTestSaveNum.setText(getString(R.string.test_StorageImage, mStorage.getSaveType(), 0));
        setActionBarTitle(R.string.test_capture_speed);
    }

    boolean isStorageComplete() {
        if (mStorage.getSaveType() == Storage.SaveType.RAM) return true;
        return mCaptureMode.isComplete() && mCaptureMode.mImageReceivedNumber == mStorage.getStorageNum();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMemInfo.setText(getString(R.string.mem_info, getMaxMemoryInfo()));
        mSaveType.setText(getString(R.string.test_save_type, mStorage.getSaveType()));
        mCameraController = new CameraController(this);
        mCameraController.setOnFmtChangedListener(Storage.getStorage());
        mTestFmt.setText(getString(R.string.test_fmt, mCameraController.getFmt()));
        mCameraController.openCamera(mCamId, updateSize());
        mCameraController.setCameraCallback(new CameraController.CameraCallback() {
            @Override
            public void onCameraOpened(CameraController controller) {
                controller.config();
            }

            @Override
            public void onCameraClosed() {
            }

            @Override
            public void onConfigured(CameraController controller) {
                runOnUiThread(() -> mButton.setEnabled(true));
            }

            @Override
            public void onTestStart(CameraController.CaptureMode captureSolution, long start) {
                runOnUiThread(() -> {
                    resetTestView();
                });
                mStorage.resetSaveNum();
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
                mStorage.saveImageBuffer(image, 0);
                runOnUiThread(() -> {
                    mTestReceive.setText(getString(R.string.test_receivedImage, num));
                });
            }

            @Override
            public void onSendRequest(int num) {
                runOnUiThread(() -> mTestSend.setText(getString(R.string.test_sendRequest, num)));
            }
        });
        resetTestView();
        mStorage.setOnImageSaveCompleteListener((file, a, successful) -> {
            runOnUiThread(() -> mTestSaveNum.setText(getString(R.string.test_StorageImage, mStorage.getSaveType(), a)));
        });

        mStorage.setImageSaveDirCreateListener(file -> runOnUiThread(() -> {
            mTestStoragePath.setText(getString(R.string.test_StoragePath, file.getAbsolutePath()));
        }));

        File file = mStorage.getDir();
        String path = file == null ? "-" : file.getAbsolutePath();
        mTestStoragePath.setText(getString(R.string.test_StoragePath, path));
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraController.stopCaptureBurst();
        mCameraController.closeCamera();
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
            case R.id.clear_current_fmt_image:
                File file = mStorage.getDir();
                if (file != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
                    builder.setTitle(R.string.clear_image);
                    builder.setMessage("Delete:" + file.getAbsolutePath() + "?");
                    builder.setPositiveButton("Delete", (dialog, which) -> {

                        dialog.dismiss();

                        final AlertDialog alertDialog = new AlertDialog.Builder(SpeedTestActivity.this).setTitle(R.string.clear_image).
                                setMessage("Deleting:" + file.getAbsolutePath()).show();

                        new Thread(() -> {
                            File[] files = file.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    if (f.isFile()) {
                                        boolean b = f.delete();
                                        runOnUiThread(() -> alertDialog.setMessage("Deleting " + f.getAbsolutePath()));
                                        Log.d(TAG, "Delete : " + f.getAbsolutePath());
                                    }
                                }
                            }
                            Log.d(TAG, "Delete : " + file.getAbsolutePath() + " complete");
                            runOnUiThread(alertDialog::dismiss);
                        }).start();
                    });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                    builder.show();
                }

                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void resetTestView() {
        mTestSpeed.setText(R.string.test_result_unknow);
        mTestReceive.setText(R.string.test_receivedImage_unknow);
        mTestSend.setText(R.string.test_sendRequest_unknow);
        updateSize();
        if (mCameraController.isStatusOf(CameraController.Status.Idle, CameraController.Status.Configured)) {
            mButton.setEnabled(true);
        }
    }

    @SuppressLint("SetTextI18n")
    Size updateSize() {
        List<Size> sizes = mCameraController.getImageSupportSize(mCamId);
        Size size = sizes.get(0);
        mTestSize.setText(getString(R.string.test_image_size, size.getWidth() + "x" + size.getHeight()) + "(" + mCameraController.getFmt() + ")");
        mTestFmt.setText(getString(R.string.test_fmt, mCameraController.getFmt()));
        return size;
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
                mMemInfo.setText(getString(R.string.mem_info, getMaxMemoryInfo()));
                mHandler.postDelayed(this, 1000);
            }
        }

    }
}