package com.xslczx.recorder;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VisualizeView extends View {

    /**
     * the count of spectrum
     */
    protected int mSpectrumCount = 60;
    /**
     * the width of every spectrum
     */
    protected float mStrokeWidth;
    /**
     * the color of drawing spectrum
     */
    protected int mColor;
    /**
     * audio data transform by hypot
     */
    protected float[] mRawAudioBytes;
    /**
     * the margin of adjoin spectrum
     */
    protected float mItemMargin = 12;

    protected float mSpectrumRatio = 2;

    protected RectF mRect;
    protected Paint mPaint;
    protected Path mPath;
    protected float centerX, centerY;
    private int mode = WAVE;
    public static final int SINGLE = 0;
    public static final int CIRCLE = 1;
    public static final int NET = 2;
    public static final int REFLECT = 3;
    public static final int WAVE = 4;
    public static final int GRAIN = 5;
    float radius = 150;

    public VisualizeView(Context context) {
        super(context);
        init();
    }

    public VisualizeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    protected void init() {
        mStrokeWidth = 5;

        mPaint = new Paint();
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setColor(getResources().getColor(R.color.black));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setAntiAlias(true);
        mPaint.setMaskFilter(new BlurMaskFilter(5, BlurMaskFilter.Blur.SOLID));

        mRect = new RectF();
        mPath = new Path();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int finallyWidth;
        int finallyHeight;
        int wSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int wSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int hSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        if (wSpecMode == MeasureSpec.EXACTLY) {
            finallyWidth = wSpecSize;
        } else {
            finallyWidth = 500;
        }

        if (hSpecMode == MeasureSpec.EXACTLY) {
            finallyHeight = hSpecSize;
        } else {
            finallyHeight = 500;
        }

        setMeasuredDimension(finallyWidth, finallyHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mRect.set(0, 0, getWidth(), getHeight() - 50);
        centerX = mRect.width() / 2;
        centerY = mRect.height() / 2;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mRawAudioBytes == null) {
            return;
        }
        drawChild(canvas);
    }

    protected void drawChild(Canvas canvas) {
        mStrokeWidth = (mRect.width() - (mSpectrumCount - 1) * mItemMargin) / mSpectrumCount * 1.0f;
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setStyle(Paint.Style.FILL);

        switch (mode) {
            case SINGLE:
                for (int i = 0; i < mSpectrumCount; i++) {
                    canvas.drawLine(mRect.width() * i / mSpectrumCount, mRect.height() / 2,
                        mRect.width() * i / mSpectrumCount, 2 + mRect.height() / 2 - mRawAudioBytes[i], mPaint);
                }
                break;
            case CIRCLE:
                mStrokeWidth = (float) ((Math.PI * 2 * radius - (mSpectrumCount - 1) * mItemMargin) / mSpectrumCount * 1.0f);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(2);
                canvas.drawCircle(centerX, centerY, radius, mPaint);
                mPaint.setStrokeWidth(mStrokeWidth);
                mPaint.setStyle(Paint.Style.FILL);
                mPath.moveTo(0, centerY);
                for (int i = 0; i < mSpectrumCount; i++) {
                    double angel = ((360d / mSpectrumCount * 1.0d) * (i + 1));
                    double startX = centerX + (radius + mStrokeWidth / 2) * Math.sin(Math.toRadians(angel));
                    double startY = centerY + (radius + mStrokeWidth / 2) * Math.cos(Math.toRadians(angel));
                    double stopX = centerX + (radius + mStrokeWidth / 2 + mSpectrumRatio * mRawAudioBytes[i]) * Math.sin(Math.toRadians(angel));
                    double stopY = centerY + (radius + mStrokeWidth / 2 + mSpectrumRatio * mRawAudioBytes[i]) * Math.cos(Math.toRadians(angel));
                    canvas.drawLine((float) startX, (float) startY, (float) stopX, (float) stopY, mPaint);
                }
                break;
            case NET:
                mStrokeWidth = (float) ((Math.PI * 2 * radius - (mSpectrumCount - 1) * mItemMargin) / mSpectrumCount * 1.0f);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(2);
                canvas.drawCircle(centerX, centerY, radius, mPaint);

                mPaint.setStrokeWidth(mStrokeWidth);
                mPaint.setStyle(Paint.Style.FILL);
                mPath.moveTo(0, centerY);
                for (int i = 0; i < mSpectrumCount; i++) {
                    double angel = ((360d / mSpectrumCount * 1.0d) * (i + 1));
                    double startX = centerX + (radius + mStrokeWidth / 2) * Math.sin(Math.toRadians(angel));
                    double startY = centerY + (radius + mStrokeWidth / 2) * Math.cos(Math.toRadians(angel));
                    double stopX = centerX + (radius + mStrokeWidth / 2 + mSpectrumRatio * mRawAudioBytes[i]) * Math.sin(Math.toRadians(angel));
                    double stopY = centerY + (radius + mStrokeWidth / 2 + mSpectrumRatio * mRawAudioBytes[i]) * Math.cos(Math.toRadians(angel));
                    canvas.drawLine((float) startX, (float) startY, (float) stopX, (float) stopY, mPaint);
                    if (i == 0) {
                        mPath.moveTo((float) startX, (float) startY);
                    }
                    mPath.lineTo((float) stopX, (float) stopY);
                }
                mPaint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(mPath, mPaint);
                mPath.reset();
                break;
            case REFLECT:
                mStrokeWidth = (mRect.width() - (mSpectrumCount - 1) * mItemMargin) / mSpectrumCount * 1.0f;
                mPaint.setStrokeWidth(mStrokeWidth);
                mPaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < mSpectrumCount; i++) {
                    canvas.drawLine(mRect.width() * i / mSpectrumCount, mRect.height() / 2, mRect.width() * i / mSpectrumCount, 2 + mRect.height() / 2 - mSpectrumRatio * mRawAudioBytes[i], mPaint);
                    canvas.drawLine(mRect.width() * i / mSpectrumCount, mRect.height() / 2, mRect.width() * i / mSpectrumCount, 2 + mRect.height() / 2 + mSpectrumRatio * mRawAudioBytes[i], mPaint);
                }
                break;
            case WAVE:
                mStrokeWidth = (mRect.width() - (mSpectrumCount - 1) * mItemMargin) / mSpectrumCount * 1.0f;
                mPaint.setStrokeWidth(mStrokeWidth);
                mPaint.setStyle(Paint.Style.FILL);
                mPath.moveTo(0, centerY);

                for (int i = 0; i < mSpectrumCount; i++) {
                    mPath.lineTo(mRect.width() * i / mSpectrumCount, 2 + mRect.height() / 2 + mRawAudioBytes[i]);
                }
                mPath.lineTo(mRect.width(), centerY);
                mPath.close();
                canvas.drawPath(mPath, mPaint);
                mPath.reset();
                break;
            case GRAIN:
                mStrokeWidth = (mRect.width() - (mSpectrumCount - 1) * mItemMargin) / mSpectrumCount * 1.0f;
                mPaint.setStrokeWidth(mStrokeWidth);
                mPaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < mSpectrumCount; i++) {
                    canvas.drawPoint(mRect.width() * i / mSpectrumCount, 2 + mRect.height() / 2 - mRawAudioBytes[i], mPaint);
                    canvas.drawPoint(mRect.width() * i / mSpectrumCount, mRect.height() / 4 + 2 + (mRect.height() / 2 - mRawAudioBytes[i]) / 2, mPaint);
                }
                break;
            default:
                break;
        }

    }

    public void setMode(int mode) {
        this.mode = mode;
        if (mRawAudioBytes != null) {
            invalidate();
        }
    }

    public void setData(float[] parseData) {
        mRawAudioBytes = parseData;
        invalidate();
    }
}
