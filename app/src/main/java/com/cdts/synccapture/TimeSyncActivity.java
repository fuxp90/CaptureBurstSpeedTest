package com.cdts.synccapture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.math.BigDecimal;
import java.text.NumberFormat;

public class TimeSyncActivity extends AppCompatActivity {

    private Button mStartStopBtn;
    private TextView mListenedCount;
    private TextView mFirstListenedTime;
    private TextView mCurrentListened;
    private TextView mTimeGap;
    private boolean isAudioRecording = false;

    private AudioRecordController mAudioRecordController;
    private final String[] PERMISSION = {Manifest.permission.RECORD_AUDIO};

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_sync);

        findViewById(R.id.time_sync_finish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAudioRecordController.getFirstListenedTime() == 0) {
                    Toast.makeText(getApplicationContext(), R.string.base_time_error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (mAudioRecordController.getListenedIndex() < 2) {
                    Toast.makeText(getApplicationContext(), R.string.need_listened_count_more, Toast.LENGTH_LONG).show();
                    return;
                }

                mAudioRecordController.stopRecording();

                long baseTime = mAudioRecordController.getFirstListenedTime();
                Intent intent = new Intent();
                intent.putExtra("base_time", baseTime);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        mStartStopBtn = findViewById(R.id.start_time_record);
        mStartStopBtn.setOnClickListener(v -> {
            if (!hasAudioPermission()) {
                ActivityCompat.requestPermissions(this, PERMISSION, 0xff);
                return;
            }

            if (isAudioRecording) {
                mStartStopBtn.setText(R.string.start_time_record);
                mAudioRecordController.stopRecording();
            } else {
                mAudioRecordController.startRecording((index, timestamp, rateInHz, maxAmplitude) -> runOnUiThread(() -> {
                    long firstTime = mAudioRecordController.getFirstListenedTime();
                    mListenedCount.setText(getString(R.string.listened_count, index));
                    mFirstListenedTime.setText(getString(R.string.first_listened_time, firstTime));
                    long gap = timestamp - firstTime;
                    String stringBuilder = getString(R.string.time_gap) + "\nNanosecond:" + gap + "\nMicrosecond:" + new BigDecimal(String.valueOf(gap / 1000f)) + "\nMillisecond:" + new BigDecimal(String.valueOf(gap / 1000_000f));
                    mTimeGap.setText(stringBuilder);
                    mCurrentListened.setText(getString(R.string.listen_current, timestamp + "", rateInHz + "", maxAmplitude + ""));
                }));
                mStartStopBtn.setText(R.string.stop_time_record);
            }
            isAudioRecording = !isAudioRecording;
        });
        mListenedCount = findViewById(R.id.listen_count);
        mFirstListenedTime = findViewById(R.id.listen_first_time);
        mTimeGap = findViewById(R.id.listen_gap);
        mCurrentListened = findViewById(R.id.listen_current);
        mCurrentListened.setText(getString(R.string.listen_current, "-", "-", "-"));

        mAudioRecordController = new AudioRecordController(this);

        mListenedCount.setText(getString(R.string.listened_count, 0));
        mTimeGap.setText(getString(R.string.time_gap) + "-");
        mFirstListenedTime.setText(getString(R.string.first_listened_time, 0));


        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(this, PERMISSION, 0xff);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        mAudioRecordController.stopRecording();
    }

    boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
}
