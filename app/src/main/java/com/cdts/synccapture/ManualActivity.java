package com.cdts.synccapture;

import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ManualActivity extends BaseActivity implements MySeekBar.OnSeekBarChangeListener, CameraController.OnManual3AChangedListener {

    private CameraController mCameraController;
    private SurfaceView mSurfaceView;

    private MySeekBar mExpTimeSeek;
    private MySeekBar mSensitivitySeek;
    private MySeekBar mFocusSeek;
    private MySeekBar mAwbSeek;

    private static final String TAG = "ManualActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);
        mCameraController = new CameraController(this);

        mExpTimeSeek = findViewById(R.id.exposure_time_seek);
        mSensitivitySeek = findViewById(R.id.sensitivity_seek);
        mFocusSeek = findViewById(R.id.focus_seek);
        mAwbSeek = findViewById(R.id.awb_seek);
        mFocusSeek.setOnSeekBarChangeListener(this);
        mSensitivitySeek.setOnSeekBarChangeListener(this);
        mExpTimeSeek.setOnSeekBarChangeListener(this);
        mAwbSeek.setOnSeekBarChangeListener(this);

        mAwbSeek.setTitle("AWB");
        mAwbSeek.setIntRange(new Range<>(-50, 50));
        mAwbSeek.setValue(0);

        findViewById(R.id.save_3a_parameter).setOnClickListener(v -> {

            CameraController.ManualParameter p = CameraController.ManualParameter.getManualParameter();

            p.mExposureTime = mExpTimeSeek.getLongValue();
            p.mFocusDistance = mFocusSeek.getFloatValue();
            p.mSensitivity = mSensitivitySeek.getIntValue();

            Log.d(TAG, "save_3a_parameter: " + p);
            finish();
        });

        findViewById(R.id.reset_to_auto).setOnClickListener(v -> {
            mAwbSeek.setValue(0);
            mCameraController.startPreview(ManualActivity.this);
        });

        mSurfaceView = findViewById(R.id.preview);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                mCameraController.setPreviewSurface(holder.getSurface());
                mCameraController.openCamera("0", new Size(1440, 1080));

                mExpTimeSeek.setTitle(getString(R.string.exposure_time));
                mSensitivitySeek.setTitle(getString(R.string.sensitivity));
                mFocusSeek.setTitle(getString(R.string.focus_distance));

                CameraController.ManualParameter parameter = mCameraController.getManualParameter();
                mExpTimeSeek.setLongRange(new Range<>(parameter.mExposureTimeRange.getLower(), CameraController.NS / 5));
                mSensitivitySeek.setIntRange(parameter.mSensitivityRange);
                mFocusSeek.setFloatRange(new Range<>(0f, parameter.mMinFocusDistance));

            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

        setActionBarTitle(R.string.manual_title);
    }

    @Override
    public void onManual3AChanged(CameraController.ManualParameter parameter) {
        runOnUiThread(() -> {
            mExpTimeSeek.setValue(parameter.mExposureTime);
            mSensitivitySeek.setValue(parameter.mSensitivity);
            mFocusSeek.setValue(parameter.mFocusDistance);
        });

    }

    @Override
    public void onSeek(MySeekBar seekBar) {
        CameraController.ManualParameter parameter = mCameraController.getManualParameter();
        if (seekBar == mAwbSeek) {
            int v = seekBar.getIntValue();
            parameter.getAwbColorCompensationRggbVector(v);
        } else {
            float f1 = seekBar.getFloatValue();
            long l1 = seekBar.getLongValue();
            int i1 = seekBar.getIntValue();
            if (f1 > 0) parameter.mFocusDistance = f1;
            if (l1 > 0) parameter.mExposureTime = seekBar.getLongValue();
            if (i1 > 0) parameter.mSensitivity = seekBar.getIntValue();
        }
        Log.d(TAG, "onSeek: " + parameter);
        mCameraController.update3AParameter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraController.setCameraCallback(new CameraController.CameraCallback() {
            @Override
            public void onCameraOpened(CameraController controller) {

            }

            @Override
            public void onCameraClosed() {

            }

            @Override
            public void onConfigured(CameraController controller) {
                controller.startPreview(ManualActivity.this);
            }

            @Override
            public void onTestStart(CameraController.CaptureMode captureSolution, long time) {

            }

            @Override
            public void onTestEnd(CameraController.CaptureMode captureSolution) {

            }

            @Override
            public void onSendRequest(int num) {

            }

            @Override
            public void onReceiveImage(int num, Image image) {

            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraController.closeCamera();
    }
}
