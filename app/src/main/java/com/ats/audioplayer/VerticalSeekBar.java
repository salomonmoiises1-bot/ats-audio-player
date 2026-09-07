package com.ats.audioplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

/**
 * VerticalSeekBar custom view que extiende AppCompatSeekBar.
 * Rota el Canvas 270 grados (-90 grados) para renderizar el fader verticalmente
 * y mapea las coordenadas táctiles para que el desplazamiento hacia arriba incremente el valor.
 */
public class VerticalSeekBar extends AppCompatSeekBar {

    private OnSeekBarChangeListener mListener;

    public VerticalSeekBar(@NonNull Context context) {
        super(context);
    }

    public VerticalSeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public VerticalSeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        this.mListener = l;
        super.setOnSeekBarChangeListener(l);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(h, w, oldh, oldw);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
    }

    @Override
    protected void onDraw(Canvas c) {
        c.rotate(-90);
        c.translate(-getHeight(), 0);
        super.onDraw(c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mListener != null) {
                    mListener.onStartTrackingTouch(this);
                }
                updateProgressFromTouch(event);
                setPressed(true);
                break;

            case MotionEvent.ACTION_MOVE:
                updateProgressFromTouch(event);
                setPressed(true);
                break;

            case MotionEvent.ACTION_UP:
                updateProgressFromTouch(event);
                setPressed(false);
                if (mListener != null) {
                    mListener.onStopTrackingTouch(this);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (mListener != null) {
                    mListener.onStopTrackingTouch(this);
                }
                break;
        }
        return true;
    }

    private void updateProgressFromTouch(MotionEvent event) {
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        int y = (int) event.getY() - getPaddingTop();
        if (height <= 0) return;

        int progress = getMax() - (int) ((float) y / (float) height * getMax());
        if (progress < 0) progress = 0;
        if (progress > getMax()) progress = getMax();

        setProgress(progress);
        onSizeChanged(getWidth(), getHeight(), 0, 0);
    }

    public synchronized void setProgressAndThumb(int progress) {
        setProgress(progress);
        onSizeChanged(getWidth(), getHeight(), 0, 0);
    }
}