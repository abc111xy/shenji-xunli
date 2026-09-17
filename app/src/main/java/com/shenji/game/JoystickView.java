package com.shenji.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** 虚拟摇杆（左下角），输出 -1..1 的归一化方向 */
public class JoystickView extends View {

    public interface Listener {
        void onMove(float x, float y);
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cx, cy, radius;
    private float knobX, knobY;
    private boolean active = false;
    private int pointerId = -1;
    private Listener listener;

    public JoystickView(Context context) {
        super(context);
        bgPaint.setColor(0x33FFFFFF);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(5f);
        stickPaint.setColor(0x99FFFFFF);
        stickPaint.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w * 0.5f;
        cy = h * 0.5f;
        radius = Math.min(w, h) * 0.44f;
        knobX = cx;
        knobY = cy;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawCircle(cx, cy, radius, bgPaint);
        c.drawCircle(knobX, knobY, radius * 0.40f, stickPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                if (!active) {
                    active = true;
                    pointerId = e.getPointerId(idx);
                    update(e.getX(idx), e.getY(idx));
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (active) {
                    int i = e.findPointerIndex(pointerId);
                    if (i >= 0) update(e.getX(i), e.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int idx = e.getActionIndex();
                if (active && e.getPointerId(idx) == pointerId) {
                    active = false;
                    pointerId = -1;
                    knobX = cx;
                    knobY = cy;
                    if (listener != null) listener.onMove(0f, 0f);
                    invalidate();
                }
                break;
            }
            default:
                break;
        }
        return true;
    }

    private void update(float x, float y) {
        float dx = x - cx, dy = y - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > radius) {
            dx = dx / len * radius;
            dy = dy / len * radius;
        }
        knobX = cx + dx;
        knobY = cy + dy;
        if (listener != null) listener.onMove(dx / radius, dy / radius);
        invalidate();
    }
}
