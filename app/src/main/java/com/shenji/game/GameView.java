package com.shenji.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

/**
 * GL 视图：只负责「拖动转视角」。
 * 移动由 JoystickView 提供，跳跃由 JumpButton 提供。
 */
public class GameView extends GLSurfaceView {

    public interface HudListener {
        void onHud(String text);
    }

    private final GameRenderer renderer;

    private int lookPointerId = -1;
    private float lookLastX, lookLastY;

    public GameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new GameRenderer(context);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        // 切后台时尽量保留 EGL 上下文，减少 onSurfaceCreated 重建；
        // 即便不保留，buildScene() 已做幂等处理，不会重复叠加模型。
        setPreserveEGLContextOnPause(true);
    }

    public void setHudListener(HudListener l) {
        renderer.setHudListener(l);
    }

    /** 渲染加载进度 → 启动页进度条 */
    public void setLoadListener(GameRenderer.LoadListener l) {
        renderer.setLoadListener(l);
    }

    /** 摇杆输入（-1..1） */
    public void setMoveVec(float nx, float ny) {
        renderer.setMoveInput(nx * 150f, ny * 150f);
    }

    public void jump() {
        renderer.jump();
    }

    /** 觐见（走近并仰望神像）→ 触发九酒之问 */
    public void setSummonListener(GameRenderer.SummonListener l) {
        renderer.setSummonListener(l);
    }

    /** 质询中锁住移动 / 转视角 */
    public void freeze(boolean b) {
        renderer.freeze(b);
    }

    /** 铁律一：脚下「边界」崩塌 */
    public void collapse() {
        renderer.collapse();
    }

    /** 坠入无界 → 纯白走廊 */
    public void enterCorridor() {
        renderer.enterCorridor();
    }

    /** 觐见 → 自动进入审判场（圆形镜室，预览图那个） */
    public void enterTrialRoom() {
        renderer.enterTrialRoom();
    }

    /** 坠入世界海 · 白色之海（蒙尔斯洛斯） */
    public void enterWhiteSea() {
        renderer.enterWhiteSea();
    }

    /** ★ 走到蒙尔斯洛斯脚下时回调（白色之海 · A 方案收尾） */
    public void setSeaListener(GameRenderer.SeaListener l) {
        renderer.setSeaListener(l);
    }

    /** 是否正在「无界 · 纯白走廊」里（此时轻触屏幕 = 确认自己还在） */
    public boolean isCorridor() {
        return renderer.isCorridor();
    }

    /** 走廊轻触确认：一记脚步声 + 镜头轻晃 */
    public void nudge() {
        renderer.nudge();
    }

    /** 走廊最后几秒：连脚步声也抽走 */
    public void corridorSilence() {
        renderer.setCorridorSilence();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                // 纯白走廊里玩家无权操控，但轻触一下必须「有反应」——
                // 否则一片静止的白，任谁都会以为游戏崩了。
                if (renderer.isCorridor()) {
                    renderer.nudge();
                    break;
                }
                if (lookPointerId == -1) {
                    lookPointerId = e.getPointerId(idx);
                    lookLastX = e.getX(idx);
                    lookLastY = e.getY(idx);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int i = e.findPointerIndex(lookPointerId);
                if (i >= 0) {
                    float x = e.getX(i);
                    float y = e.getY(i);
                    renderer.addLook(x - lookLastX, y - lookLastY);
                    lookLastX = x;
                    lookLastY = y;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                if (e.getPointerId(idx) == lookPointerId) {
                    lookPointerId = -1;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                lookPointerId = -1;
                break;
            }
            default:
                break;
        }
        return true;
    }
}
