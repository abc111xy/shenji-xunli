package com.shenji.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Locale;

/**
 * 启动页（标题画面）—— 《神寂·巡礼》 / Silenzio Divino: Il Pellegrinaggio
 *
 * · 暗黑风格背景（主人提供的海报，完整居中、左右羽化融入黑色负空间）
 * · 左下：标题 / 箴言          右下：加载进度条 → 完成后变为「轻触开始」
 * · 轻触后淡出并被移除（保证不再拦截触摸）
 */
public class SplashScreen extends FrameLayout {

    private static final long MIN_LOAD_MS = 700;    // 进度条最短展示时间，避免一闪而过
    private static final long READY_FALLBACK_MS = 10000; // 加载回调长时间不来时的兜底

    private final long t0 = System.currentTimeMillis();

    private boolean ready = false;
    private boolean finished = false;

    private LinearLayout loadingGroup;
    private LinearLayout readyGroup;
    private TextView loadLabel;
    private View barFill;
    private TextView startText;
    private int barFullW;

    public SplashScreen(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        setClickable(true);

        // ---- 背景海报 ----
        ImageView bg = new ImageView(c);
        Bitmap bm = loadBg(c);
        if (bm != null) {
            bg.setImageBitmap(bm);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        addView(bg, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- 左上：神域标注 ----
        TextView chapter = tv(c, "第一位神域 · 伊赛德亚 · 边界与谎言之庭", 11f, 0xCCD9BE86, false, 0.06f);
        LayoutParams cp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.TOP | Gravity.START;
        cp.leftMargin = dp(c, 46); cp.topMargin = dp(c, 32);
        addView(chapter, cp);

        // ---- 左下：标题组 ----
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView titleEn = tv(c, "SILENZIO DIVINO", 30f, 0xFFD9BE86, true, 0.24f);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p1.bottomMargin = dp(c, 2);
        left.addView(titleEn, p1);

        left.addView(tv(c, "Il Pellegrinaggio · 神寂 · 巡礼", 15f, 0xFFEDEDED, false, 0.08f));

        View line = new View(c);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(dp(c, 208), dp(c, 1));
        p2.topMargin = dp(c, 13); p2.bottomMargin = dp(c, 13);
        line.setLayoutParams(p2);
        line.setBackgroundColor(0x77D9BE86);
        left.addView(line);

        TextView pillars = tv(c, "凝 视 · 沉 默 · 揭 示", 12f, 0xCCB79A5F, false, 0.20f);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p3.bottomMargin = dp(c, 9);
        left.addView(pillars, p3);

        left.addView(tv(c, "「每一个副本，一位神。每一位神，一个真相。」", 12f, 0xFF9C9C9C, false, 0.02f));

        LayoutParams lpLeft = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lpLeft.gravity = Gravity.BOTTOM | Gravity.START;
        lpLeft.leftMargin = dp(c, 52); lpLeft.bottomMargin = dp(c, 34);
        addView(left, lpLeft);

        // ---- 右下：加载进度 / 轻触开始 ----
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);

        // (1) 加载中：阶段文字 + 金色进度条
        loadingGroup = new LinearLayout(c);
        loadingGroup.setOrientation(LinearLayout.VERTICAL);
        loadingGroup.setGravity(Gravity.END);

        loadLabel = tv(c, "正在加载 …", 11f, 0xFFB79A5F, false, 0.06f);
        loadLabel.setGravity(Gravity.END);
        loadingGroup.addView(loadLabel);

        barFullW = dp(c, 210);
        FrameLayout track = new FrameLayout(c);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(barFullW, dp(c, 3));
        tp.topMargin = dp(c, 9);
        track.setLayoutParams(tp);
        track.setBackgroundColor(0x33FFFFFF);
        barFill = new View(c);
        barFill.setBackgroundColor(0xFFD9BE86);
        track.addView(barFill, new FrameLayout.LayoutParams(0, LayoutParams.MATCH_PARENT));
        loadingGroup.addView(track);
        right.addView(loadingGroup);

        // (2) 加载完成：轻触开始
        readyGroup = new LinearLayout(c);
        readyGroup.setOrientation(LinearLayout.VERTICAL);
        readyGroup.setGravity(Gravity.END);
        readyGroup.setVisibility(View.GONE);

        startText = tv(c, "轻 触 开 始", 15f, 0xFFF2F2F2, false, 0.26f);
        startText.setGravity(Gravity.END);
        readyGroup.addView(startText);

        TextView hint = tv(c, "TAP TO BEGIN", 10f, 0x99B79A5F, false, 0.30f);
        hint.setGravity(Gravity.END);
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p4.topMargin = dp(c, 5); p4.bottomMargin = dp(c, 14);
        readyGroup.addView(hint, p4);

        readyGroup.addView(tv(c, "v0.72 · 原型 DEMO", 10f, 0x80888888, false, 0.05f));
        right.addView(readyGroup);

        LayoutParams lpRight = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lpRight.gravity = Gravity.BOTTOM | Gravity.END;
        lpRight.rightMargin = dp(c, 52); lpRight.bottomMargin = dp(c, 34);
        addView(right, lpRight);

        // 兜底：万一加载回调一直不来，也不能把玩家永远卡在启动页
        postDelayed(new Runnable() {
            @Override public void run() { setReady(); }
        }, READY_FALLBACK_MS);
    }

    /** GL 线程 → 主线程回报加载进度 */
    public void setProgress(final int percent, final String label) {
        post(new Runnable() {
            @Override public void run() {
                if (ready) return;
                int p = Math.max(0, Math.min(100, percent));
                loadLabel.setText(String.format(Locale.US, "%s  %d%%", label, p));
                ViewGroup.LayoutParams lp = barFill.getLayoutParams();
                lp.width = (int) (barFullW * p / 100f);
                barFill.setLayoutParams(lp);
            }
        });
    }

    /** 加载完成：稍作停顿后切换为「轻触开始」 */
    public void setReady() {
        long wait = MIN_LOAD_MS - (System.currentTimeMillis() - t0);
        if (wait > 0) {
            postDelayed(new Runnable() {
                @Override public void run() { showReady(); }
            }, wait);
        } else {
            post(new Runnable() {
                @Override public void run() { showReady(); }
            });
        }
    }

    private void showReady() {
        if (ready || finished) return;
        ready = true;
        loadingGroup.setVisibility(View.GONE);
        readyGroup.setVisibility(View.VISIBLE);
        AlphaAnimation blink = new AlphaAnimation(1f, 0.28f);
        blink.setDuration(1200);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        startText.startAnimation(blink);
    }

    private static TextView tv(Context c, String s, float sp, int color,
                               boolean serif, float spacing) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLetterSpacing(spacing);
        t.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        if (serif) t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        return t;
    }

    private int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static Bitmap loadBg(Context c) {
        try {
            InputStream is = c.getAssets().open("splash_bg.jpg");
            Bitmap b = BitmapFactory.decodeStream(is);
            is.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!ready) return true;          // 加载中：吞掉触摸，避免误触
        if (e.getActionMasked() == MotionEvent.ACTION_UP) dismiss();
        return true;
    }

    /** 轻触开始：淡出后必然移除自身（同步兜底，绝不残留拦截触摸） */
    private void dismiss() {
        if (finished) return;
        AlphaAnimation fade = new AlphaAnimation(1f, 0f);
        fade.setDuration(600);
        fade.setFillAfter(true);
        fade.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) { }
            @Override public void onAnimationRepeat(Animation a) { }
            @Override public void onAnimationEnd(Animation a) { finish(); }
        });
        startAnimation(fade);
        // 双保险：即使动画回调异常，也保证移除
        postDelayed(new Runnable() {
            @Override public void run() { finish(); }
        }, 800);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        clearAnimation();
        setVisibility(View.GONE);
        ViewParent p = getParent();
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(this);
    }
}
