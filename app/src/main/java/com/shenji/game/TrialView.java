package com.shenji.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * ★ 九酒之问 · 质询界面（纯 Canvas 覆盖层）
 *
 * 版式对齐设计稿（1600×900 基准，按屏高自适应缩放）：
 *   顶部  伊赛德亚 · 边界与谎言之庭 | 第 N 酒 · 〈律名〉
 *   中段  【复答】上一轮玩家原话 → 定律原句（金） → 神的逼问（白）
 *   选项  Ａ / Ｂ / Ｃ 三行
 *   底部  神的回应
 *   左右  注视值 / 反注视 ‖ 边界完整度 / 无界深度
 */
public class TrialView extends View {

    public interface Listener {
        /** 答错：边界崩塌，坠入无界 */
        void onCollapse(int abyss);

        /** 九轮走完：坠入更深的一层 */
        void onFinished(int abyss);
    }

    private NineWines.Session session;
    private NineWines.AnswerProvider provider = new NineWines.LocalProvider();
    private Listener listener;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface serif = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    private long tRound = 0L;
    private long tReply = -1L;
    private String reply = "";
    private boolean awaiting = true;
    private boolean sent = false;
    private NineWines.Result last;

    private final float[] optY = new float[3];
    private LinearGradient gTop, gBottom;
    private RadialGradient gCenter;
    private int cachedW, cachedH;

    public TrialView(Context c) {
        super(c);
        p.setTypeface(serif);
        p.setSubpixelText(true);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    /** 未来接入大模型：在这里换成 new NineWines.RemoteProvider() */
    public void setProvider(NineWines.AnswerProvider a) {
        this.provider = a;
    }

    /** 开始新一轮审判（重置整局） */
    public void reset() {
        session = new NineWines.Session();
        tRound = System.currentTimeMillis();
        tReply = -1L;
        reply = "";
        awaiting = true;
        sent = false;
        last = null;
        invalidate();
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void onDraw(Canvas c) {
        final int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        final float s = h / 900f;
        final long now = System.currentTimeMillis();
        final float tIn = (now - tRound) / 1000f;

        buildGradients(w, h);
        veil.setShader(gTop);
        c.drawRect(0, 0, w, h * 0.26f, veil);
        veil.setShader(gBottom);
        c.drawRect(0, h * 0.46f, w, h, veil);
        veil.setShader(gCenter);
        c.drawRect(0, 0, w, h, veil);
        veil.setShader(null);

        if (session == null) {
            postInvalidateOnAnimation();
            return;
        }

        NineWines.Question q = session.question();

        // ---- 顶部 ----
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(21 * s);
        p.setColor(0xFFB79A5F);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("伊赛德亚 · 边界与谎言之庭", 46 * s, 38 * s, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("第 " + NineWines.cn(q.round) + " 酒 ／ 共九酒 · " + q.law, w - 46 * s, 38 * s, p);
        fill.setColor(0x40D9BE86);
        c.drawRect(46 * s, 58 * s, w - 46 * s, 59.4f * s, fill);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(24 * s);
        p.setColor(0xFFD9BE86);
        c.drawText("九 酒 之 问", w / 2f, 50 * s, p);

        // ---- 复答（第 4、7 酒：把玩家自己说过的话钉回来）----
        int rt = session.recallTarget();
        String prev = (rt > 0) ? session.recall(rt) : null;
        float base = (prev != null) ? 62f : 0f;

        if (prev != null) {
            float a = fade(tIn, 0.10f, 0.45f);
            textFit(c, "【复答】你在第" + NineWines.cn(rt) + "酒答过：" + prev,
                    w / 2f, 240 * s, w * 0.80f, 23 * s, alpha(0xFFB79A5F, a));
        }

        // ---- 题面 = 定律原句（金色）----
        float yThesis = (306 + base) * s;
        float aT = fade(tIn, 0.22f, 0.5f);
        textFit(c, q.thesis, w / 2f, yThesis, w * 0.78f, 25 * s, alpha(0xFFB79A5F, aT));

        fill.setColor(alpha(0x8CD9BE86, aT));
        c.drawRect(w / 2f - 260 * s, yThesis + 22 * s, w / 2f + 260 * s, yThesis + 23.4f * s, fill);

        // ---- 神的逼问（白色大字）----
        float aA = fade(tIn, 0.38f, 0.55f);
        textFit(c, q.ask, w / 2f, (384 + base) * s, w * 0.90f, 42 * s, alpha(0xFFFFFFFF, aA));

        // ---- 三个选项 ----
        float y0 = (452 + base * 0.6f) * s;
        for (int i = 0; i < 3; i++) {
            float oy = y0 + i * 72 * s;
            optY[i] = oy;
            float a = fade(tIn, 0.55f + i * 0.14f, 0.40f);
            if (!awaiting) a *= 0.5f;
            textFit(c, q.options[i], w / 2f, oy, w * 0.86f, 27 * s, alpha(0xFFFFFFFF, a));
            fill.setColor(alpha(0x46D9BE86, a));
            c.drawRect(w / 2f - 300 * s, oy + 11 * s, w / 2f + 300 * s, oy + 12.4f * s, fill);
        }

        // ---- 神的回应 ----
        if (tReply > 0 && reply.length() > 0) {
            float tr = (now - tReply) / 1000f;
            float a = fade(tr, 0f, 0.55f);
            textFit(c, reply, w / 2f, 700 * s, w * 0.88f, 24 * s, alpha(0xFFD9BE86, a));
        }

        // ---- 左下 / 右下 状态 ----
        drawStatus(c, w, h, s, q);

        // ---- 崩塌红闪 ----
        if (last != null && last.catastrophic && tReply > 0) {
            float ft = (now - tReply) / 1000f;
            float a = clamp(ft / 0.45f, 0f, 1f) * (0.45f + 0.55f * Math.abs((float) Math.sin(ft * 9f)));
            c.drawColor(alpha(0xFF8A1014, a * 0.8f));
        }

        // ---- 时序推进 ----
        if (!awaiting && tReply > 0 && !sent) {
            float tr = (now - tReply) / 1000f;
            if (last != null && last.catastrophic) {
                if (tr > 1.7f) {
                    sent = true;
                    if (listener != null) listener.onCollapse(session.abyss);
                    return;
                }
            } else if (tr > 2.3f) {
                sent = true;
                if (session.finished) {
                    if (listener != null) listener.onFinished(session.abyss);
                    return;
                }
                nextRound();
                return;
            }
        }
        postInvalidateOnAnimation();
    }

    private void drawStatus(Canvas c, int w, int h, float s, NineWines.Question q) {
        float gaze = Math.min(1f, 0.55f + 0.05f * q.round);
        float rev = Math.min(1f, 0.30f + 0.07f * q.round);
        float y0 = h - 152 * s;

        p.setTextAlign(Paint.Align.LEFT);
        statusLine(c, s, 46 * s, y0, "注 视 值", gaze, 0xFFD9BE86, pct(gaze));
        statusLine(c, s, 46 * s, y0 + 62 * s, "反 注 视", rev, 0xFF96BEEB, pct(rev));

        p.setTextAlign(Paint.Align.RIGHT);
        statusLine(c, s, w - 46 * s, y0, "边 界 完 整 度", session.boundary, 0xFFD9BE86, pct(session.boundary));
        p.setTextSize(19 * s);
        p.setColor(0xFFA8A8A8);
        c.drawText("无 界 深 度", w - 46 * s, y0 + 62 * s, p);
        p.setTextSize(22 * s);
        p.setColor(0xFFCD7D7D);
        c.drawText(session.abyss + " 层", w - 46 * s, y0 + 90 * s, p);
    }

    private void statusLine(Canvas c, float s, float x, float y, String label, float v, int color, String val) {
        p.setTextSize(19 * s);
        p.setColor(0xFFA8A8A8);
        c.drawText(label, x, y, p);

        float left = (p.getTextAlign() == Paint.Align.RIGHT) ? x - 210 * s : x;
        fill.setColor(0x2EFFFFFF);
        c.drawRect(left, y + 12 * s, left + 210 * s, y + 18 * s, fill);
        fill.setColor(alpha(color, 0.9f));
        c.drawRect(left, y + 12 * s, left + 210 * s * clamp(v, 0f, 1f), y + 18 * s, fill);

        p.setTextSize(17 * s);
        p.setColor(color);
        c.drawText(val, x, y + 42 * s, p);
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (session == null) return true;
        if (awaiting && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (System.currentTimeMillis() - tRound < 900L) return true;   // 防手滑误触
            float s = getHeight() / 900f;
            float y = e.getY();
            for (int i = 0; i < 3; i++) {
                if (Math.abs(y - optY[i]) < 40 * s) {
                    choose(i);
                    break;
                }
            }
        }
        return true;
    }

    private void choose(int i) {
        if (!awaiting || session == null) return;
        awaiting = false;
        final NineWines.Question q = session.question();
        final int kind = q.kinds[i];
        last = session.answer(i);
        tReply = System.currentTimeMillis();
        reply = "";
        invalidate();

        provider.respond(q, i, kind, session.history, new NineWines.AnswerProvider.Callback() {
            @Override
            public void onResponse(final String line) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        reply = (line == null) ? NineWines.localReply(q.round, kind) : line;
                        invalidate();
                    }
                });
            }
        });
    }

    private void nextRound() {
        tRound = System.currentTimeMillis();
        tReply = -1L;
        reply = "";
        awaiting = true;
        sent = false;
        last = null;
        invalidate();
    }

    // ---------------------------------------------------------------- 工具

    private void buildGradients(int w, int h) {
        if (w == cachedW && h == cachedH) return;
        cachedW = w;
        cachedH = h;
        gTop = new LinearGradient(0, 0, 0, h * 0.26f, 0xC8000000, 0x00000000, Shader.TileMode.CLAMP);
        gBottom = new LinearGradient(0, h * 0.46f, 0, h, 0x00000000, 0xDC000000, Shader.TileMode.CLAMP);
        gCenter = new RadialGradient(w / 2f, h * 0.52f, h * 0.62f, 0x5E000000, 0x00000000, Shader.TileMode.CLAMP);
    }

    /** 居中绘制并按最大宽度自动缩字号（中文长句不溢出） */
    private void textFit(Canvas c, String txt, float cx, float y, float maxW, float size, int color) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);
        float tw = p.measureText(txt);
        if (tw > maxW) p.setTextSize(size * maxW / tw);
        p.setColor(color);
        c.drawText(txt, cx, y, p);
    }

    private static float fade(float t, float start, float dur) {
        return clamp((t - start) / dur, 0f, 1f);
    }

    private static String pct(float v) {
        return ((int) (clamp(v, 0f, 1f) * 100f)) + "%";
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int alpha(int color, float a) {
        int al = (int) (clamp(a, 0f, 1f) * 255f);
        return (color & 0x00FFFFFF) | (al << 24);
    }
}
