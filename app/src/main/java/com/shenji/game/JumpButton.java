package com.shenji.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** 跳跃键（右下角） */
public class JumpButton extends View {

    public interface Listener {
        void onJump();
    }

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cx, cy, radius;
    private boolean pressed = false;
    private Listener listener;

    public JumpButton(Context context) {
        super(context);
        circlePaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xEEFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w * 0.5f;
        cy = h * 0.5f;
        radius = Math.min(w, h) * 0.45f;
        textPaint.setTextSize(radius * 0.62f);
    }

    @Override
    protected void onDraw(Canvas c) {
        circlePaint.setColor(pressed ? 0x88FFFFFF : 0x44FFFFFF);
        c.drawCircle(cx, cy, radius, circlePaint);
        float ty = cy - (textPaint.descent() + textPaint.ascent()) * 0.5f;
        c.drawText("跳", cx, ty, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if (pressed && listener != null) listener.onJump();
                pressed = false;
                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }
}
