package com.tencent.yolov8ncnn;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

public class ZoomableImageView extends ImageView {

    private Matrix matrix = new Matrix();
    private PointF start = new PointF();
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;
    private float lastDist = 1f;
    private float dampeningFactorZoom = 0.1f; // dampening factor for zooming
    private float dampeningFactorDrag = 0.5f; // dampening factor for dragging
    private float maxZoom = 4f;
    private float minZoom = 0.5f;

    public ZoomableImageView(Context context) {
        super(context);
        init();
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        super.setClickable(true);
        setScaleType(ScaleType.MATRIX);
        matrix.setTranslate(1f, 1f);
        setImageMatrix(matrix);

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        matrix.set(getImageMatrix());
                        start.set(event.getX(), event.getY());
                        mode = DRAG;
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        lastDist = spacing(event);
                        if (lastDist > 10f) {
                            matrix.set(getImageMatrix());
                            mode = ZOOM;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            float dx = (event.getX() - start.x) * dampeningFactorDrag;
                            float dy = (event.getY() - start.y) * dampeningFactorDrag;
                            matrix.postTranslate(dx, dy);
                            fixTrans();
                        } else if (mode == ZOOM) {
                            float newDist = spacing(event);
                            if (newDist > 10f) {
                                float scale = 1 + (newDist / lastDist - 1) * dampeningFactorZoom;
                                scale = Math.min(scale, maxZoom / getCurrentZoom());
                                scale = Math.max(scale, minZoom / getCurrentZoom());
                                matrix.postScale(scale, scale, getWidth() / 2, getHeight() / 2);
                                fixTrans();
                            }
                        }
                        break;
                }
                setImageMatrix(matrix);
                return true;
            }

            private float spacing(MotionEvent event) {
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return (float) Math.sqrt(x * x + y * y);
            }
        });
    }

    private void fixTrans() {
        float[] matrixValues = new float[9];
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, getWidth(), getDrawable().getIntrinsicWidth() * matrixValues[Matrix.MSCALE_X]);
        float fixTransY = getFixTrans(transY, getHeight(), getDrawable().getIntrinsicHeight() * matrixValues[Matrix.MSCALE_Y]);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans = 0;
        float maxTrans = viewSize - contentSize;

        if (maxTrans > 0) {
            maxTrans = 0;
        }

        if (trans < maxTrans) {
            return -trans + maxTrans;
        }
        if (trans > minTrans) {
            return -trans + minTrans;
        }
        return 0;
    }

    private float getCurrentZoom() {
        float[] values = new float[9];
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }
}