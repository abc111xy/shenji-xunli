package com.shenji.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * ★ 坠入无界 → 「猛地一闪」→ 下一个神域
 *
 * 时序（对齐设定 5.5）：
 *   0.00s  全屏纯白爆闪（"就这么猛地一闪"）
 *   0.10s  白开始退，露出去色的下一神域
 *   0.55s  底幕沉黑
 *   0.90s  第二位神域的字浮上来
 *   2.00s  底部提示：轻触回到审判场（原型调试用）
 */
public class NextRealmView extends View {

    public interface Listener {
        void onTap();
    }

    private Listener listener;
    private long t0 = -1L;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cover = new Paint();
    private final Typeface serif = Typeface.create(Typeface.SERIF, Typeface.BOLD);
    private LinearGradient grad;
    private int cachedH;

    public NextRealmView(Context c) {
        super(c);
        p.setTypeface(serif);
        p.setSubpixelText(true);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void start() {
        t0 = System.currentTimeMillis();
        setVisibility(VISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || t0 < 0) return;
        float s = h / 900f;
        float t = (System.currentTimeMillis() - t0) / 1000f;

        // 下一神域的底幕（先黑，再浮字）
        if (cachedH != h) {
            cachedH = h;
            grad = new LinearGradient(0, 0, 0, h, 0xFF05060A, 0xFF000000, Shader.TileMode.CLAMP);
        }
        float dark = clamp((t - 0.20f) / 0.55f, 0f, 1f);
        cover.setAlpha((int) (255 * dark));
        cover.setShader(grad);
        c.drawRect(0, 0, w, h, cover);

        // 猛地一闪：纯白爆闪
        float white = (t < 0.10f) ? 1f : Math.max(0f, 1f - (t - 0.10f) / 0.40f);
        if (white > 0f) {
            cover.setShader(null);
            cover.setColor(0xFFFFFFFF);
            cover.setAlpha((int) (255 * white));
            c.drawRect(0, 0, w, h, cover);
        }

        float a = clamp((t - 0.90f) / 0.85f, 0f, 1f);
        if (a <= 0.01f) {
            postInvalidateOnAnimation();
            return;
        }

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(22 * s);
        p.setColor(alphaColor(0xFFB79A5F, a));
        c.drawText("第 二 位 神 域", w / 2f, h * 0.34f, p);

        p.setTextSize(46 * s);
        p.setColor(alphaColor(0xFFF2F2F2, a));
        c.drawText("蒙尔斯洛斯 · 白色之海", w / 2f, h * 0.44f, p);

        fillLine(c, w / 2f - 300 * s, w / 2f + 300 * s, h * 0.485f, alphaColor(0x8CD9BE86, a));

        p.setTextSize(21 * s);
        p.setColor(alphaColor(0xFFB79A5F, a));
        c.drawText("第 十 三 觉 · 听 觉", w / 2f, h * 0.55f, p);

        p.setTextSize(24 * s);
        p.setColor(alphaColor(0xFFD9BE86, a));
        c.drawText("「白，就是光。」", w / 2f, h * 0.62f, p);

        p.setTextSize(17 * s);
        p.setColor(alphaColor(0xFF8A8A8A, a));
        c.drawText("（ 原 型 至 此 · 轻 触 回 到 审 判 场 ）", w / 2f, h - 44 * s, p);

        postInvalidateOnAnimation();
    }

    private final Paint barPaint = new Paint();

    private void fillLine(Canvas c, float x0, float x1, float y, int color) {
        barPaint.setColor(color);
        c.drawRect(x0, y, x1, y + 1.4f * (getHeight() / 900f), barPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && t0 > 0
                && System.currentTimeMillis() - t0 > 2200L) {
            if (listener != null) listener.onTap();
        }
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int alphaColor(int color, float a) {
        int al = (int) (clamp(a, 0f, 1f) * 255f);
        return (color & 0x00FFFFFF) | (al << 24);
    }
}
