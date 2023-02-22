package com.cdts.synccapture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Map;

public class TimeStaticsView extends androidx.appcompat.widget.AppCompatImageView {
    public TimeStaticsView(Context context) {
        super(context);
    }

    public TimeStaticsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TimeStaticsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    private Map<Long, Long> mTimeStatics;
    private float mStandard;

    public void setTimeStatics(Map<Long, Long> timeStatics, float standard) {
        mTimeStatics = timeStatics;
        mStandard = standard;
        if (mTimeStatics != null && !mTimeStatics.isEmpty()) {
            setVisibility(VISIBLE);
            invalidate();
        } else {
            setVisibility(INVISIBLE);
        }
    }


    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final static int mLeftPadding = 50;
    private final static int mBottomPadding = 0;

    @SuppressLint("DefaultLocale")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        mPaint.setColor(Color.RED);
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);

        if (mTimeStatics != null && !mTimeStatics.isEmpty()) {
            int size = mTimeStatics.size();
            Log.d("TimeStaticsView", mTimeStatics.toString());
            int w = getWidth() - getPaddingLeft() - getPaddingRight() - mLeftPadding;
            int h = getHeight() - getPaddingTop() - getPaddingBottom() - mBottomPadding;
            int itemW = w / size;

            Long valMax = mTimeStatics.values().stream().max((o1, o2) -> (int) (o1 - o2)).get();
            Long valMin = mTimeStatics.values().stream().max((o1, o2) -> (int) (o2 - o1)).get();

            Long keyMax = mTimeStatics.keySet().stream().max((o1, o2) -> (int) (o1 - o2)).get();
            Long keyMin = mTimeStatics.keySet().stream().max((o1, o2) -> (int) (o2 - o1)).get();

            final Long[] sum = {0L, 0L};
            mTimeStatics.values().forEach(aLong -> sum[0] += aLong);
            mTimeStatics.keySet().forEach(aLong -> sum[1] += aLong * mTimeStatics.get(aLong));
            Log.d("TimeStaticsView", "values sum " + sum[0] + ",key sum:" + sum[1]);

            @SuppressLint("DefaultLocale") String keyAvg = String.format("%.2f", sum[1] * 1f / sum[0]);


            int itemMinLen = (int) (h * 0.2f);
            int itemMaxLen = (int) (h * 0.8f);
            int itemLengthRange = (int) (itemMaxLen - itemMinLen);

            @SuppressLint("DrawAllocation") Long[] sortKey = mTimeStatics.keySet().stream().sorted().toArray(value -> new Long[mTimeStatics.size()]);
            int index = 0;
            for (Long key : sortKey) {
                long val = mTimeStatics.get(key);
                float ch = (val - valMin) * 1f / (valMax - valMin) * itemLengthRange + itemMinLen;

                r.set(0, 0, itemW / 2f, ch);

                r.offsetTo(itemW * index + mLeftPadding + itemW / 3f, h - ch);

                Log.d("TimeStaticsView", index + "  onDraw R: " + r + " " + key + "=" + val + ",ch:" + ch + " height: " + r.height());
                mPaint.setColor(0x992893E4);
                mPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(r, mPaint);

                mPaint.setColor(Color.BLUE);
                mPaint.setTextAlign(Paint.Align.CENTER);
                mPaint.setTextSize(30);
                canvas.drawText(key + "", r.centerX(), r.top - 10, mPaint);

                mPaint.setColor(0x4BC62D7C);
                canvas.drawLine(mLeftPadding, r.top, getWidth(), r.top, mPaint);
                mPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(val + "", mLeftPadding, r.top, mPaint);

                canvas.drawLine(mLeftPadding, 0, mLeftPadding, getHeight(), mPaint);

                index++;

            }

            Paint.FontMetrics fontMetrics = mPaint.getFontMetrics();
            float textHeight = fontMetrics.bottom - fontMetrics.top;
            mPaint.setTextAlign(Paint.Align.LEFT);
            mPaint.setColor(Color.BLUE);
            String text = "Min:" + keyMin + "ms" + ",Max:" + keyMax + "ms,Avg:" + keyAvg + "ms";
            canvas.drawText(text, mLeftPadding, textHeight, mPaint);
            mPaint.setColor(Color.RED);
            if (mStandard > 0) {
                canvas.drawText("Standard:" + String.format("%.2f", mStandard) + "ms", mPaint.measureText(text) + mLeftPadding + 20, textHeight, mPaint);
            }

        }
    }
}
