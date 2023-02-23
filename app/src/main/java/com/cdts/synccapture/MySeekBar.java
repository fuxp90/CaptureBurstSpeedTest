package com.cdts.synccapture;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressLint("AppCompatCustomView")
public class MySeekBar extends LinearLayout {
    public MySeekBar(@NonNull Context context) {
        super(context);
        init();
    }

    public MySeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MySeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    private static final String TAG = "MySeekBar";
    private TextView mTitle;
    private SeekBar mSeekBar;
    private Range<Long> mLongRange;
    private Range<Integer> mIntRange;
    private Range<Float> mFloatRange;
    private String mTitleStr;
    private OnSeekBarChangeListener mOnSeekBarChangeListener;

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener onSeekBarChangeListener) {
        mOnSeekBarChangeListener = onSeekBarChangeListener;
    }

    @SuppressLint("SetTextI18n")
    public void setValue(long value) {
        int p = (int) ((value - mLongRange.getLower()) * 100f / (mLongRange.getUpper() - mLongRange.getLower()));
        mSeekBar.setProgress(p);
        @SuppressLint("DefaultLocale") String s = String.format("(%.2fs)", value * 1f / CameraController.NS);
        mTitle.setText(mTitleStr + ":" + value + s);
    }

    @SuppressLint("SetTextI18n")
    public void setValue(int value) {
        int p = (int) ((value - mIntRange.getLower()) * 100f / (mIntRange.getUpper() - mIntRange.getLower()));
        mSeekBar.setProgress(p);
        mTitle.setText(mTitleStr + ":" + value);
    }

    @SuppressLint("SetTextI18n")
    public void setValue(float value) {
        int p = (int) ((value - mFloatRange.getLower()) * 100 / (mFloatRange.getUpper() - mFloatRange.getLower()));
        mSeekBar.setProgress(p);
        mTitle.setText(mTitleStr + ":" + value);
    }

    public interface OnSeekBarChangeListener {
        void onSeek(MySeekBar seekBar);
    }

    public void setSeekProgress(int progress) {
        mSeekBar.setProgress(progress);
    }

    public int getSeekProgress() {
        return mSeekBar.getProgress();
    }

    public void setSeekRange(int min, int max) {
        mSeekBar.setMin(min);
        mSeekBar.setMax(max);
    }

    private void init() {
        inflate(getContext(), R.layout.layout_seek_bak, this);
        mTitle = findViewById(R.id.title);
        mSeekBar = findViewById(R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                Log.d(TAG, "onProgressChanged: " + progress);
                setTitleValue();
                if (mOnSeekBarChangeListener != null) {
                    mOnSeekBarChangeListener.onSeek(MySeekBar.this);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        findViewById(R.id.input).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Manual input " + mTitleStr);
                if (mLongRange != null) {
                    Range<Long> p = CameraController.ManualParameter.getManualParameter().mExposureTimeRange;
                    builder.setMessage("Range:" + p.getLower() + "~" + p.getUpper());
                } else if (mFloatRange != null) {
                    builder.setMessage("Range:" + mFloatRange.getLower() + "~" + mFloatRange.getUpper());
                } else {
                    builder.setMessage("Range:" + mIntRange.getLower() + "~" + mIntRange.getUpper());
                }
                final EditText inputEdit = new EditText(getContext());
                builder.setView(inputEdit);
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        String input = inputEdit.getText().toString();
                        if (mLongRange != null) {
                            setValue(Long.parseLong(input));
                        } else if (mFloatRange != null) {
                            setValue(Float.parseFloat(input));
                        } else {
                            setValue(Integer.parseInt(input));
                        }
                        if (mOnSeekBarChangeListener != null) {
                            mOnSeekBarChangeListener.onSeek(MySeekBar.this);
                        }
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
            }
        });
    }

    public int getIntValue() {
        if (mIntRange == null) return -1;
        return (int) (mSeekBar.getProgress() / 100f * (mIntRange.getUpper() - mIntRange.getLower()) + mIntRange.getLower());
    }

    public long getLongValue() {
        if (mLongRange == null) return -1;
        return (long) (mSeekBar.getProgress() / 100f * (mLongRange.getUpper() - mLongRange.getLower()) + mLongRange.getLower());
    }

    public float getFloatValue() {
        if (mFloatRange == null) return -1;
        return (mSeekBar.getProgress() / 100f * (mFloatRange.getUpper() - mFloatRange.getLower()) + mFloatRange.getLower());
    }

    public void setLongRange(Range<Long> range) {
        mLongRange = range;
    }

    public void setIntRange(Range<Integer> range) {
        mIntRange = range;
    }

    public void setFloatRange(Range<Float> range) {
        mFloatRange = range;
    }

    public void setTitle(String title) {
        mTitleStr = title;
        setTitleValue();
    }


    @SuppressLint("SetTextI18n")
    private void setTitleValue() {
        if (mLongRange != null) {
            @SuppressLint("DefaultLocale") String s = String.format("(%.2fs)", getLongValue() * 1f / CameraController.NS);
            mTitle.setText(mTitleStr + ":" + getLongValue() + s);
        } else if (mIntRange != null) {
            mTitle.setText(mTitleStr + ":" + getIntValue());
        } else if (mFloatRange != null) {
            mTitle.setText(mTitleStr + ":" + getFloatValue());
        } else {
            mTitle.setText(mTitleStr + ":" + mSeekBar.getProgress());
        }
    }

}
