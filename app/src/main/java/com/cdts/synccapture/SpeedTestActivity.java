package com.cdts.synccapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.cdts.beans.Command;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

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
    private TextView mManualParameter;
    private TextView mManualCurrent;
    private TextView m3AMode;
    private TimeStaticsView mTimeStaticsView;
    private TextView mModeRateView;
    private TextView mTestJpegQuality;
    private TextView mDeviceName;

    private final static String TAG = "BaseActivity";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Storage mStorage = Storage.getStorage();
    private CameraController.CaptureMode mCaptureMode = CameraController.CaptureMode.CaptureFixRate;
    private static final int TIME_SYNC_REQ_CODE = 0xfff;
    private long mImageBaseTime;
    private TextView mBaseTime;

    private PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.initSpf(this);
        setContentView(R.layout.activity_speed_test);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
        mWakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);

        mTimeStaticsView = findViewById(R.id.time_statics_view);
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
        mButton = findViewById(R.id.test_button);
        mManualParameter = findViewById(R.id.manual_parameter_range);
        m3AMode = findViewById(R.id.test_3a_mode);
        mManualCurrent = findViewById(R.id.manual_current);
        mModeRateView = findViewById(R.id.test_solution_param);
        mTestJpegQuality = findViewById(R.id.test_fmt_param);
        mBaseTime = findViewById(R.id.test_base_time);
        mDeviceName = findViewById(R.id.device_name);

        findViewById(R.id.test_solution_select).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
            builder.setTitle(R.string.test_choose_mode);
            final String[] charSequence = Utils.toStringArray(CameraController.CaptureMode.values());
            int item = Utils.indexOf(CameraController.CaptureMode.values(), mCaptureMode);
            builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                CameraController.CaptureMode captureMode = CameraController.CaptureMode.valueOf(charSequence[which]);
                mTestMode.setText(getString(R.string.test_mode, captureMode));
                mTestModeDetail.setText(getResources().getStringArray(R.array.test_mode_detail)[which]);
                if (mCameraController.isTestRunning()) {
                    Toast.makeText(getApplicationContext(), R.string.test_mode_changed, Toast.LENGTH_LONG).show();
                } else {
                    mCaptureMode = captureMode;
                    mModeRateView.setVisibility(mCaptureMode.isSupportRecordRequestTimeDelay() ? View.VISIBLE : View.INVISIBLE);
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
                final String[] charSequence = Utils.toStringArray(Storage.SaveType.values());
                int item = Utils.indexOf(Storage.SaveType.values(), mStorage.getSaveType());
                builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                    if (!isStorageComplete()) {
                        Toast.makeText(getApplicationContext(), R.string.test_save_type_changed, Toast.LENGTH_LONG).show();
                    } else {
                        Storage.SaveType saveType = Storage.SaveType.valueOf(charSequence[which]);
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
                final String[] charSequence = Utils.toStringArray(CameraController.Fmt.values());
                int item = Utils.indexOf(CameraController.Fmt.values(), mCameraController.getFmt());
                builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                    if (mCameraController.isTestRunning()) {
                        Toast.makeText(getApplicationContext(), R.string.test_fmt_changed, Toast.LENGTH_LONG).show();
                    } else {
                        CameraController.Fmt fmt = CameraController.Fmt.valueOf(charSequence[which]);
                        mCameraController.setImageFormat(fmt);
                        resetView();
                    }
                    dialog.dismiss();
                });
                builder.show();
            }
        });

        findViewById(R.id.test_size_select).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
                builder.setTitle(R.string.test_image_size_choose);
                final String[] charSequence = mCameraController.getImageSupportSize().stream().map(size -> size.getWidth() + "x" + size.getHeight()).collect(Collectors.toList()).toArray(new String[1]);
                int item = mCameraController.getImageSupportSize().indexOf(mCameraController.getSize());
                builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                    if (mCameraController.isTestRunning()) {
                        Toast.makeText(getApplicationContext(), R.string.test_fmt_changed, Toast.LENGTH_LONG).show();
                    } else {
                        String[] s = charSequence[which].split("x");
                        Size size = new Size(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
                        mCameraController.setSize(size);
                        mTestSize.setText(getString(R.string.test_image_size, size.getWidth() + "x" + size.getHeight()));

                    }
                    dialog.dismiss();
                });
                builder.show();
            }
        });

        findViewById(R.id.test_3a_mode_select).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
                builder.setTitle(R.string.test_choose_3a_mode);
                final String[] charSequence = Utils.toStringArray(CameraController.Capture3AMode.values());
                int item = Utils.indexOf(CameraController.Capture3AMode.values(), mCameraController.get3AMode());
                builder.setSingleChoiceItems(charSequence, item, (dialog, which) -> {
                    if (mCameraController.isTestRunning()) {
                        Toast.makeText(getApplicationContext(), R.string.test_fmt_changed, Toast.LENGTH_LONG).show();
                    } else {
                        CameraController.Capture3AMode mode = CameraController.Capture3AMode.valueOf(charSequence[which]);

                        if (mode == CameraController.Capture3AMode.Manual) {

                            Intent intent = new Intent(getApplication(), ManualActivity.class);
                            startActivityForResult(intent, 0x123);

                        } else {
                            mCameraController.set3AMode(mode);
                            resetView();
                        }
                    }
                    dialog.dismiss();
                });
                builder.show();
            }
        });

        mButton.setOnClickListener(v -> {
            if (!mCameraController.isTestRunning()) {
                mButton.setText(R.string.test_stop);
                mCameraController.startCaptureBurst(mCaptureMode);
            } else {
                mButton.setText(R.string.test_start);
                mCameraController.stopCaptureBurst();
            }
        });

        mModeRateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraController.isTestRunning()) {
                    Toast.makeText(getApplicationContext(), R.string.test_fmt_changed, Toast.LENGTH_LONG).show();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
                    builder.setTitle(R.string.test_mode_rate);
                    MySeekBar seek = new MySeekBar(getApplicationContext());
                    seek.setSeekRange(1, 30);
                    seek.setSeekProgress(mCameraController.getRequestRate());
                    seek.setTitle(getString(R.string.test_fix_request_rate));
                    seek.setOnSeekBarChangeListener(seekBar -> {
                        mModeRateView.setText(getString(R.string.test_fix_rate_fpx, seekBar.getSeekProgress()));
                        mCameraController.setRequestRate(seekBar.getSeekProgress());
                    });
                    builder.setView(seek);
                    builder.show();
                }


            }
        });

        mTestJpegQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraController.isTestRunning()) {
                    Toast.makeText(getApplicationContext(), R.string.test_fmt_changed, Toast.LENGTH_LONG).show();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(SpeedTestActivity.this);
                    builder.setTitle(R.string.test_fmt_jpeg_quality);
                    MySeekBar seek = new MySeekBar(getApplicationContext());
                    seek.setSeekRange(50, 100);
                    seek.setSeekProgress(mCameraController.getJpegQuality());
                    seek.setTitle(getString(R.string.test_fmt_jpeg_quality));
                    seek.setOnSeekBarChangeListener(seekBar -> {
                        mTestJpegQuality.setText(getString(R.string.test_fmt_jpeg_qu, seekBar.getSeekProgress()));
                        mCameraController.setJpegQuality(seekBar.getSeekProgress());
                    });
                    builder.setView(seek);
                    builder.show();
                }
            }
        });
        setActionBarTitle(R.string.test_capture_speed);

    }


    boolean isStorageComplete() {
        if (mStorage.getSaveType() == Storage.SaveType.RAM) return true;
        CameraController.CaptureMode captureMode = mCameraController.getCaptureMode();
        return captureMode.isComplete() && captureMode.mImageReceivedNumber == mStorage.getStorageNum();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (hasCameraPermission()) {
            // onResume();
        } else {
            Toast.makeText(this, "Please give Permissions", Toast.LENGTH_LONG).show();
            finish();
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    public void onCommandReceived(Command command) {
        switch (command.getCmd()) {
            case Command.audio_sync_start:
                Intent intent = new Intent(getApplicationContext(), TimeSyncActivity.class);
                startActivityForResult(intent, TIME_SYNC_REQ_CODE);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 0xff);
            return;
        }

        mCameraController = new CameraController(this);
        mCameraController.addOnFmtChangedListener(Storage.getStorage());
        mCameraController.addOnFmtChangedListener(new CameraController.OnFmtChangedListener() {
            @Override
            public void OnFmtChanged(Context context, CameraController.Fmt fmt) {
                Size size = mCameraController.getSize();
                mTestSize.setText(getString(R.string.test_image_size, size.getWidth() + "x" + size.getHeight()));
            }
        });
        mCameraController.openCamera(mCamId, null);
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
                    resetView();
                    mTimeStaticsView.setTimeStatics(null, 0);
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

                    mTimeStaticsView.setTimeStatics(mCameraController.getRequestTimeMap(), 1000f / mCameraController.getRequestRate());

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
        mStorage.setOnImageSaveCompleteListener((file, a, successful) -> {
            runOnUiThread(() -> mTestSaveNum.setText(getString(R.string.test_StorageImage, mStorage.getSaveType(), a)));
        });
        mStorage.setImageSaveDirCreateListener(file -> runOnUiThread(() -> {
            mTestStoragePath.setText(getString(R.string.test_StoragePath, file.getAbsolutePath()));
        }));

        resetView();
    }

    @SuppressLint("SetTextI18n")
    private void resetView() {
        mMemInfo.setText(getString(R.string.mem_info, getMaxMemoryInfo()));
        mSaveType.setText(getString(R.string.test_save_type, mStorage.getSaveType()));
        mTestMode.setText(getString(R.string.test_mode, mCaptureMode));
        mTestModeDetail.setText(getResources().getStringArray(R.array.test_mode_detail)[mCaptureMode.ordinal()]);
        mTestSaveNum.setText(getString(R.string.test_StorageImage, mStorage.getSaveType(), 0));
        mTestFmt.setText(getString(R.string.test_fmt, mCameraController.getFmt()));
        File dir = mStorage.getDir();
        String path = dir == null ? "-" : dir.getAbsolutePath();
        mTestStoragePath.setText(getString(R.string.test_StoragePath, path));
        mTestSpeed.setText(R.string.test_result_unknow);
        mTestReceive.setText(R.string.test_receivedImage_unknow);
        mTestSend.setText(R.string.test_sendRequest_unknow);
        Size size = mCameraController.getSize();
        mTestSize.setText(getString(R.string.test_image_size, size.getWidth() + "x" + size.getHeight()));
        mTestFmt.setText(getString(R.string.test_fmt, mCameraController.getFmt()));
        if (mCameraController.isStatusOf(CameraController.Status.Idle, CameraController.Status.Configured)) {
            mButton.setEnabled(true);
        }
        mTestJpegQuality.setText(getString(R.string.test_fmt_jpeg_qu, mCameraController.getJpegQuality()));
        mTestJpegQuality.setVisibility(mCameraController.getFmt() == CameraController.Fmt.JPEG ? View.VISIBLE : View.INVISIBLE);
        mModeRateView.setText(getString(R.string.test_fix_rate_fpx, mCameraController.getRequestRate()));
        m3AMode.setText(getString(R.string.test_3a_mode, mCameraController.get3AMode()));
        CameraController.ManualParameter parameter = mCameraController.getManualParameter();
        mManualParameter.setText(parameter.getDesc(true));
        if (mCameraController.get3AMode() == CameraController.Capture3AMode.Auto) {
            mManualCurrent.setVisibility(View.GONE);
        } else {
            mManualCurrent.setVisibility(View.VISIBLE);
            mManualCurrent.setText(parameter.getCurrentDesc());
        }
        mBaseTime.setText(getString(R.string.base_time, mImageBaseTime));
        mDeviceName.setText(getString(R.string.device_name, Utils.getSpf(Utils.KEY_DEVICE_NAME, Utils.DEF_DEVICE_NAME)));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraController != null) {
            mCameraController.stopCaptureBurst();
            mCameraController.closeCamera();
        }
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

                        final AlertDialog alertDialog = new AlertDialog.Builder(SpeedTestActivity.this).setTitle(R.string.clear_image).setMessage("Deleting:" + file.getAbsolutePath()).show();

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
            case R.id.set_device_name: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.set_device_name);
                EditText editText = new EditText(this);
                editText.setText(Utils.getSpf(Utils.KEY_DEVICE_NAME, Utils.DEF_DEVICE_NAME));
                builder.setView(editText);
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = editText.getText().toString();
                        if (!TextUtils.isEmpty(name)) {
                            Utils.putSpf(Utils.KEY_DEVICE_NAME, name);
                            mDeviceName.setText(getString(R.string.device_name, name));
                        }
                    }
                }).show();
            }
            break;
        }
        return super.onOptionsItemSelected(item);
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
                //mMemInfo.setText(getString(R.string.mem_info, getMaxMemoryInfo()));
                mHandler.postDelayed(this, 1000);
            }
        }

    }
}