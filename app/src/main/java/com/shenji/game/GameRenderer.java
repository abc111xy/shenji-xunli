package com.shenji.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 渲染 + 玩法核心：伊赛德亚 · 边界与谎言之庭
 * 神像采用 TokenHub 生成的 3D 模型（简化后 10.7 万面）
 */
public class GameRenderer implements GLSurfaceView.Renderer {

    // ---- 玩家 ----
    private float px = 0f, pz = 9f;
    private float yaw = 0f, pitch = -3f;
    private float moveX = 0f, moveY = 0f;
    private float bobPhase = 0f, bob = 0f;

    // ---- 跳跃 ----
    private float py = 0f, vy = 0f;
    private boolean grounded = true;
    private static final float GRAVITY = -22f;
    private static final float JUMP_V = 7.2f;

    // ---- 凝视 ----
    private float gaze = 0f;
    private float reverse = 0f;
    private boolean gazing = false;
    private float eyeGlow = 0.25f;

    // ---- 第一人称：相机与玩家模型的绑定 ----
    // 相机固定在人物「头部中央」，再沿视线水平前移一点点（从头部探出，避免被自身几何遮挡）
    private static final float EYE_HEIGHT = 1.62f;     // 头部中央高度（模型 ×1.5 后头顶约 1.75m）
    private static final float EYE_FORWARD = 0.15f;    // 沿视线前移量（“一点点”）
    private static final float MODEL_FRONT_YAW = 180f; // 模型正面朝 +Z，需转 180° 才与相机视线同向

    // ---- 场景障碍（柱子）碰撞 ----
    private static final float PILLAR_HALF = 0.5f;     // 柱子半宽（柱体 1.0 × 1.0）
    private static final float PLAYER_RADIUS = 0.35f;  // 玩家碰撞半径
    // 柱子中心 (x, z) 交替存放；buildScene() 与碰撞共用同一份数据源
    private static final float[] PILLARS_XZ = {
            -5.2f, 7f, 5.2f, 7f,
            -5.2f, -1f, 5.2f, -1f,
            -5.2f, -9f, 5.2f, -9f,
    };

    // ---- 矩阵 ----
    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];
    private final float[] tmp4 = new float[16];
    private final float[] nrm3 = new float[9];

    private Shader shader;
    private GameView.HudListener hud;
    private long lastTime = 0L;
    private float hudTimer = 0f;

    private final List<Obj> objs = new ArrayList<>();

    private final Context ctx;
    private int texFloor = 0, texWall = 0, texDeity = 0, texPlayer = 0, texCorridor = 0, texMsls = 0, texSea = 0;
    private Obj playerObj = null;   // 玩家模型（每帧跟随）

    // ---- ★ 九酒之问 / 转场状态 ----
    public static final int MODE_TRIAL = 0;      // 初见：矩形暗廊（走近祂）
    public static final int MODE_CORRIDOR = 1;   // 无界 · 纯白走廊
    public static final int MODE_COURT = 2;      // 审判场 · 圆形镜室（预览图那个）
    public static final int MODE_SEA = 3;        // 世界海 · 白色之海（蒙尔斯洛斯）
    private int mode = MODE_TRIAL;
    private final List<Obj> eyeObjs = new ArrayList<Obj>();   // 神像双眼（自发光，含镜中倒影）
    private boolean frozen = false;              // 质询中：锁移动与转视角
    private Obj floorObj = null;                 // 地板（崩塌时被抹除）
    private float collapseT = -1f;               // <0 = 未崩塌；>=0 为崩塌计时
    // ---- 无界 · 纯白走廊：三段式（从地上爬起来 → 顿一下 → 走）----
    public static final float CORRIDOR_RISE = 2.0f;    // 起身用时（秒）
    public static final float CORRIDOR_PAUSE = 1.2f;   // 站定后那一顿（秒）
    private static final float CORRIDOR_SPEED = 2.2f;  // 自动前进速度（m/s）
    private static final int CORR_RISE = 0, CORR_PAUSE = 1, CORR_WALK = 2;
    private int corrPhase = CORR_WALK;
    private float corrPhaseT = 0f;               // 当前阶段已用时
    private float corrDist = 0f;                 // 已在走廊里走过的距离

    private float corridorT = 0f;                // 纯白走廊计时
    private float nudgeT = -1f;                  // 走廊「轻触确认」的反馈计时
    private float silence = 0f;                  // 0..1；1 = 连脚步声也抽干（走廊最后几秒）
    private float summonT = 0f;                  // 「觐见」凝视累计
    private boolean summoned = false;            // 是否已触发觐见
    // ---- 世界海 · 白色之海 ----
    private float seaT = 0f;                     // 白色之海计时
    private float seaDist = 0f;                  // 已在海面上走过的距离
    private boolean seaArrived = false;          // 是否已走到蒙尔斯洛斯脚下
    private static final float SEA_ARRIVE_Z = -32f;   // 抵达线（神像在 z=-42，此处距祂约 10m）
    private static final float SEA_WALK_SPEED = 3.2f; // 海面上的行走速度
    private static final float SEA_EYE_HEIGHT = 1.46f;// 海面上视点高度（比陆地略低，像涉水而行）

    public interface SummonListener {
        void onSummoned();
    }

    private SummonListener summonListener;

    public void setSummonListener(SummonListener l) {
        this.summonListener = l;
    }

    /** ★ 世界海 · 白色之海：玩家真的走到蒙尔斯洛斯脚下时触发（A 方案收尾） */
    public interface SeaListener {
        void onSeaArrived();
    }

    private SeaListener seaListener;

    public void setSeaListener(SeaListener l) {
        this.seaListener = l;
    }

    /** 质询中锁住移动 / 转视角（相机被钉在祂脸上） */
    public void freeze(boolean b) {
        frozen = b;
        if (b) {
            // 清掉上一帧残留的移动惯性，避免质询中「自己往前飘」
            moveX = 0f;
            moveY = 0f;
        }
    }

    /** 铁律一：脚下「边界」具现被抹除 —— 地板消失，直坠无界 */
    public void collapse() {
        if (collapseT < 0f) collapseT = 0f;
    }

    /** 坠入无界 → 「无界 · 纯白走廊」。进来时人是趴在地上的，得先爬起来 */
    public void enterCorridor() {
        mode = MODE_CORRIDOR;
        corridorT = 0f;
        corrDist = 0f;
        corrPhase = CORR_RISE;
        corrPhaseT = 0f;
        nudgeT = -1f;
        silence = 0f;
        frozen = true;
        buildCorridor();
    }

    /**
     * ★ 觐见：场景当即切换为「审判场 · 圆形镜室」（预览图那个）
     * 玩家被挪到镜室中央偏前、面朝神像，随后质询界面浮起。
     */
    public void enterTrialRoom() {
        mode = MODE_COURT;
        frozen = true;
        px = 0f;
        pz = 10.5f;
        py = 0f;
        vy = 0f;
        yaw = 0f;          // 面朝 -Z，正对神像
        pitch = 16f;       // 略略抬头，把祂的上半身收进画
        moveX = 0f;
        moveY = 0f;
        bob = 0f;
        gaze = 1f;
        summoned = true;
        buildCourtRoom();
    }

    /** ★ 下一重境：坠入「世界海 · 白色之海」。乳白海镜之上，蒙尔斯洛斯远立雾中。
     *  与走廊不同 —— 这里【可以自由走动】，由玩家自己走向祂。 */
    public void enterWhiteSea() {
        mode = MODE_SEA;
        frozen = false;               // ★ 自由移动
        seaT = 0f;
        seaDist = 0f;
        seaArrived = false;
        px = 0f;
        pz = 12f;                     // 从神像前方 54m 处出发
        py = 0f;
        vy = 0f;
        grounded = true;
        yaw = 0f;                     // 面向神像（-z 方向）
        pitch = -2f;
        moveX = 0f;
        moveY = 0f;
        bobPhase = 0f;
        bob = 0f;
        gaze = 0f;
        reverse = 0f;
        if (stepStreamId != -1 && soundPool != null) {
            soundPool.stop(stepStreamId);
            stepStreamId = -1;
        }
        buildWhiteSea();
    }

    public int getMode() {
        return mode;
    }

    public boolean isCorridor() {
        return mode == MODE_CORRIDOR;
    }

    /**
     * 走廊里轻触一下屏幕：给一次「我还在」的反馈（一记脚步声 + 镜头轻晃）。
     * 玩家能立刻确认游戏没卡死 —— 这是「纯白走廊」不再被当成 bug 的关键之一。
     */
    public void nudge() {
        if (mode != MODE_CORRIDOR) return;
        nudgeT = 0f;
        if (soundPool != null && stepSoundId > 0) {
            soundPool.play(stepSoundId, 0.8f, 0.8f, 1, 0, 1f);
        }
    }

    /** 走廊最后几秒：连脚步声也抽走，只剩静默 */
    public void setCorridorSilence() {
        if (mode == MODE_CORRIDOR && silence <= 0f) silence = 0.0001f;
    }

    // 音频（SoundPool，GL 线程安全；避免 OPPO 上 MediaPlayer 崩溃）
    private android.media.SoundPool soundPool;
    private int stepSoundId = -1;
    private int stepStreamId = -1;
    private int breathSoundId = -1;
    private int breathStreamId = -1;

    // 模型缩放与落位（模型高 0.89 单位 → 约 11 米）
    private static final float DEITY_SCALE = 12f;
    private static final float DEITY_X = 0f;
    private static final float DEITY_Z = -14.1f;

    // 神像双眼位置（凝视判定目标）
    private static final float EYE_X = 0f, EYE_Y = 9.6f, EYE_Z = DEITY_Z - 0.35f;

    public GameRenderer(Context ctx) {
        this.ctx = ctx;
    }

    public void setHudListener(GameView.HudListener l) {
        this.hud = l;
    }

    // ---- 启动加载进度（供启动页进度条使用；回调发生在 GL 线程）----
    public interface LoadListener {
        void onLoadStep(String label, int percent);
    }

    private LoadListener loadListener;

    public void setLoadListener(LoadListener l) {
        this.loadListener = l;
    }

    private void report(String label, int pct) {
        if (loadListener != null) loadListener.onLoadStep(label, pct);
    }

    public void setMoveInput(float dx, float dy) {
        if (frozen) {
            moveX = 0f;
            moveY = 0f;
            return;
        }
        moveX = clamp(dx / 150f, -1f, 1f);
        moveY = clamp(-dy / 150f, -1f, 1f);
    }

    public void addLook(float dx, float dy) {
        if (frozen) return;
        yaw += dx * 0.22f;
        pitch -= dy * 0.22f;
        pitch = clamp(pitch, -55f, 55f);
    }

    public void jump() {
        if (frozen) return;
        if (grounded) {
            vy = JUMP_V;
            grounded = false;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        report("初始化渲染管线", 4);
        GLES20.glClearColor(0.02f, 0.02f, 0.03f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        // ① 编译着色器（未来可在此扩展更多着色器 / 变体预编译）
        report("编译着色器", 18);
        shader = new Shader();

        // ② 加载纹理
        report("加载纹理", 42);
        texFloor = loadTexture("textures/floor_color.jpg", false);
        texWall = loadTexture("textures/wall_color.jpg", false);
        texDeity = loadTexture("textures/ithadea_tex.jpg", false);
        texPlayer = loadTexture("textures/player_tex.jpg", false);
        // 纯白走廊的板材贴图：极淡的接缝网格 —— 它存在的唯一意义，就是让「还在往前走」看得见
        texCorridor = loadTexture("textures/corridor_wall.jpg", false);
        texMsls = loadTexture("textures/msls_tex.jpg", false);
        // 白色之海的海面贴图：柔和的乳白流质波纹（程序生成）
        texSea = loadTexture("textures/sea_water.jpg", false);

        // ③ 加载音频
        report("加载音频", 64);
        initAudio();

        // ④ 构建场景（模型 / 网格）
        report("构建场景", 84);
        buildScene();

        report("准备完成", 100);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        float ratio = (float) w / (float) Math.max(1, h);
        Matrix.perspectiveM(proj, 0, 62f, ratio, 0.1f, 400f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.currentTimeMillis();
        float dt = (lastTime == 0L) ? 0.016f : Math.min(0.05f, (now - lastTime) / 1000f);
        lastTime = now;

        update(dt);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // ---- 无界 · 纯白走廊：自动前进，无操控、无 HUD、无参照 ----
        if (mode == MODE_CORRIDOR) {
            float cy, cx, lookDy;
            float cz = 20f - corrDist;

            if (corrPhase == CORR_RISE) {
                // ① 从地上爬起来：视点从贴着地面的 0.26m 抬到站立高度，视线由「低头看地」慢慢抬平。
                //    用 smoothstep 而非匀速 —— 人是先撑起来、再站直的。
                float k = clamp(corrPhaseT / CORRIDOR_RISE, 0f, 1f);
                float e = k * k * (3f - 2f * k);
                cy = 0.26f + (EYE_HEIGHT - 0.26f) * e;
                cx = 0f;
                lookDy = -0.64f + 0.57f * e - (float) Math.sin(k * Math.PI * 3f) * 0.07f * (1f - k);
            } else if (corrPhase == CORR_PAUSE) {
                // ② 站定了，顿一下：只有呼吸在起伏。这一顿是「爬起来」与「继续走」之间的空白。
                cy = EYE_HEIGHT - 0.012f + (float) Math.sin(corrPhaseT * 2.1f) * 0.012f;
                cx = 0f;
                lookDy = -0.05f;
            } else {
                // ③ 走：每步一次下沉 + 左右微摆。画面永远在动，就不会被当成白屏卡死。
                float stride = corrDist * 3.6f;
                cy = EYE_HEIGHT - Math.abs((float) Math.sin(stride)) * 0.042f;
                cx = (float) Math.sin(stride * 0.5f) * 0.035f;
                lookDy = -0.05f;
            }

            if (nudgeT >= 0f) cx += (float) Math.sin(nudgeT * 46f) * 0.06f * (1f - nudgeT / 0.35f);

            Matrix.setLookAtM(view, 0, cx, cy, cz, cx, cy + lookDy, cz - 1f, 0f, 1f, 0f);
            Matrix.multiplyMM(vp, 0, proj, 0, view, 0);
            shader.use();
            GLES20.glUniform3f(shader.uLightDir, -0.3f, -1f, -0.2f);
            GLES20.glUniform3f(shader.uAmbient, 1.0f, 1.0f, 1.03f);
            GLES20.glUniform3f(shader.uFogColor, 1f, 1f, 1f);
            GLES20.glUniform1f(shader.uFogNear, 2.0f);
            GLES20.glUniform1f(shader.uFogFar, 30f);
            for (int i = 0; i < objs.size(); i++) drawObj(objs.get(i));
            return;
        }

        // ---- 世界海 · 白色之海：复用常规第一人称相机（自由走动），仅在下方雾/环境分支里换色 ----

        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float fx = (float) (Math.sin(radYaw) * Math.cos(radPitch));
        float fy = (float) Math.sin(radPitch);
        float fz = (float) (-Math.cos(radYaw) * Math.cos(radPitch));

        // 相机 = 人物头部中央 + 沿水平视线前移一点点（身体随 yaw 转动，见 update()）
        float hx = (float) Math.sin(radYaw), hz = (float) -Math.cos(radYaw);
        float eyeH = (mode == MODE_SEA) ? SEA_EYE_HEIGHT : EYE_HEIGHT;
        float camX = px + hx * EYE_FORWARD;
        float camY = py + eyeH + bob;
        float camZ = pz + hz * EYE_FORWARD;

        Matrix.setLookAtM(view, 0,
                camX, camY, camZ,
                camX + fx, camY + fy, camZ + fz,
                0f, 1f, 0f);
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0);

        shader.use();
        GLES20.glUniform3f(shader.uLightDir, 0.3f, 0.92f, 0.4f);
        if (mode == MODE_SEA) {
            // 世界海 · 白色之海：无岸、无天、无地平线。乳白过曝的海面一直漫进远处的雾里
            GLES20.glUniform3f(shader.uLightDir, 0.10f, 0.98f, 0.14f);
            GLES20.glUniform3f(shader.uAmbient, 0.72f, 0.72f, 0.76f);
            GLES20.glUniform3f(shader.uFogColor, 1f, 1f, 1f);
            GLES20.glUniform1f(shader.uFogNear, 12f);
            GLES20.glUniform1f(shader.uFogFar, 64f);
        } else if (collapseT >= 0f) {
            // 崩塌：边界被抹除的那一刻，庭中血色浓起来，尽头只剩一片暗红
            GLES20.glUniform3f(shader.uAmbient, 0.50f, 0.055f, 0.065f);
            GLES20.glUniform3f(shader.uFogColor, 0.13f, 0.012f, 0.017f);
            GLES20.glUniform1f(shader.uFogNear, 2.5f);
            GLES20.glUniform1f(shader.uFogFar, 26f);
        } else if (mode == MODE_COURT) {
            // 镜室：幽暗，光只从瞳、烛与穹顶漏下来
            float amb = 0.18f + gaze * 0.05f;
            GLES20.glUniform3f(shader.uAmbient, amb, amb + 0.005f, amb + 0.055f);
            GLES20.glUniform3f(shader.uFogColor, 0.022f, 0.022f, 0.032f);
            GLES20.glUniform1f(shader.uFogNear, 12f);
            GLES20.glUniform1f(shader.uFogFar, 70f);
        } else {
            float amb = 0.34f + gaze * 0.06f;
            GLES20.glUniform3f(shader.uAmbient, amb, amb, amb + 0.04f);
            GLES20.glUniform3f(shader.uFogColor, 0.04f, 0.04f, 0.06f);
            GLES20.glUniform1f(shader.uFogNear, 16f);
            GLES20.glUniform1f(shader.uFogFar, 80f);
        }

        for (int i = 0; i < objs.size(); i++) {
            drawObj(objs.get(i));
        }

        hudTimer += dt;
        if (mode != MODE_CORRIDOR && mode != MODE_SEA && hudTimer > 0.12f && hud != null) {
            hudTimer = 0f;
            hud.onHud(buildHud());
        }
    }

    private void update(float dt) {
        // ---- 无界 · 纯白走廊：自动前进；声音被一步步抽走，最后只剩静默 ----
        if (mode == MODE_CORRIDOR) {
            corridorT += dt;
            corrPhaseT += dt;

            // 三段式推进：爬起来 → 顿一下 → 走
            if (corrPhase == CORR_RISE) {
                if (corrPhaseT >= CORRIDOR_RISE) {
                    corrPhase = CORR_PAUSE;
                    corrPhaseT = 0f;
                }
            } else if (corrPhase == CORR_PAUSE) {
                if (corrPhaseT >= CORRIDOR_PAUSE) {
                    corrPhase = CORR_WALK;
                    corrPhaseT = 0f;
                }
            } else {
                corrDist += CORRIDOR_SPEED * dt;
            }

            if (nudgeT >= 0f) {
                nudgeT += dt;
                if (nudgeT > 0.35f) nudgeT = -1f;
            }
            if (silence > 0f && silence < 1f) silence = Math.min(1f, silence + dt / 3.5f);

            if (soundPool != null) {
                // 喘息：爬起来时最重，站定后平下来
                if (breathStreamId != -1) {
                    float bv;
                    if (corrPhase == CORR_RISE) {
                        bv = 0.62f * (1f - corrPhaseT / CORRIDOR_RISE * 0.45f);
                    } else if (corrPhase == CORR_PAUSE) {
                        bv = 0.30f;
                    } else {
                        bv = 0.22f + 0.20f * clamp(1f - corrDist / 90f, 0f, 1f);
                    }
                    bv *= (1f - silence);
                    soundPool.setVolume(breathStreamId, bv, bv);
                }
                // 脚步：站起来之后才响 —— 在这条白走廊上，脚步声就是唯一的「参照物」
                if (corrPhase == CORR_WALK) {
                    if (stepSoundId > 0 && stepStreamId == -1 && silence < 1f) {
                        stepStreamId = soundPool.play(stepSoundId, 0.5f, 0.5f, 1, -1, 1f);
                    }
                    float v = (0.20f + 0.32f * clamp(1f - corrDist / 90f, 0f, 1f)) * (1f - silence);
                    if (stepStreamId > 0) {
                        soundPool.setVolume(stepStreamId, v, v);
                        if (silence >= 1f) {
                            soundPool.stop(stepStreamId);
                            stepStreamId = -1;
                        }
                    }
                }
            }
            return;
        }

        // ---- 世界海 · 白色之海：自由走动，走向雾中的蒙尔斯洛斯 ----
        //   这一境属于「听觉」—— 脚下的海不发声，只剩自己的呼吸
        if (mode == MODE_SEA) {
            seaT += dt;
            float rad = (float) Math.toRadians(yaw);
            float fwdX = (float) Math.sin(rad), fwdZ = (float) -Math.cos(rad);
            float rgtX = (float) Math.cos(rad), rgtZ = (float) Math.sin(rad);
            px += (fwdX * moveY + rgtX * moveX) * SEA_WALK_SPEED * dt;
            pz += (fwdZ * moveY + rgtZ * moveX) * SEA_WALK_SPEED * dt;
            // 海面无边，但别让玩家走得太偏／穿到神像身后
            px = clamp(px, -46f, 46f);
            pz = clamp(pz, -37.5f, 16f);
            seaDist = Math.max(seaDist, 12f - pz);

            float moving = Math.abs(moveX) + Math.abs(moveY);
            if (moving > 0.08f) {
                bobPhase += dt * 6.6f;                 // 涉水而行的轻晃
                bob = (float) Math.sin(bobPhase) * 0.034f;
            } else {
                bob *= 0.92f;
            }

            if (soundPool != null && breathStreamId > 0) {
                // 呼吸声缓慢涨落，像海的潮汐；越靠近祂，呼吸越沉
                float near = clamp((12f - pz) / 44f, 0f, 1f);
                float v = 0.22f + 0.10f * (float) Math.sin(seaT * 0.5f) + near * 0.22f;
                soundPool.setVolume(breathStreamId, v, v);
            }

            // ★ 走到祂脚下 → 交回 MainActivity 收尾（A：弹「第二位神域」字卡）
            if (!seaArrived && pz <= SEA_ARRIVE_Z) {
                seaArrived = true;
                if (seaListener != null) seaListener.onSeaArrived();
            }
            return;
        }

        // ---- 崩塌：脚下「边界」具现被抹除，没有底了 ----
        if (collapseT >= 0f) {
            collapseT += dt;
            if (floorObj != null) {
                float k = Math.max(0f, 1f - collapseT / 1.1f);
                floorObj.sy = 0.5f * k;
                floorObj.y = -0.25f - (1f - k) * 10f;
            }
            py -= dt * 7.0f;          // 直坠无界
            bob *= 0.92f;
            if (soundPool != null && stepStreamId > 0) {
                soundPool.stop(stepStreamId);
                stepStreamId = -1;
            }
            return;
        }

        float speed = 3.4f;
        float rad = (float) Math.toRadians(yaw);
        float fwdX = (float) Math.sin(rad), fwdZ = (float) -Math.cos(rad);
        float rgtX = (float) Math.cos(rad), rgtZ = (float) Math.sin(rad);
        px += (fwdX * moveY + rgtX * moveX) * speed * dt;
        pz += (fwdZ * moveY + rgtZ * moveX) * speed * dt;
        // 柱子碰撞：玩家视为半径 PLAYER_RADIUS 的圆，柱体为 1.0×1.0 方柱（AABB）。
        // 用「最近点推挤」——贴着柱子会被平滑推开，不会穿模，也能自然沿柱面滑动。
        for (int i = 0; i + 1 < PILLARS_XZ.length; i += 2) {
            float cx = PILLARS_XZ[i], cz = PILLARS_XZ[i + 1];
            float x0 = cx - PILLAR_HALF, x1 = cx + PILLAR_HALF;
            float z0 = cz - PILLAR_HALF, z1 = cz + PILLAR_HALF;
            float nx = clamp(px, x0, x1), nz = clamp(pz, z0, z1);
            float ddx = px - nx, ddz = pz - nz;
            float d2 = ddx * ddx + ddz * ddz;
            if (d2 < PLAYER_RADIUS * PLAYER_RADIUS) {
                float d = (float) Math.sqrt(d2);
                if (d > 1e-4f) {
                    float push = (PLAYER_RADIUS - d) / d;
                    px += ddx * push;
                    pz += ddz * push;
                } else {
                    // 圆心已落入柱体内：沿最近的一面推出
                    float m = Math.min(Math.min(px - x0, x1 - px), Math.min(pz - z0, z1 - pz));
                    if (m == px - x0) px = x0 - PLAYER_RADIUS;
                    else if (m == x1 - px) px = x1 + PLAYER_RADIUS;
                    else if (m == pz - z0) pz = z0 - PLAYER_RADIUS;
                    else pz = z1 + PLAYER_RADIUS;
                }
            }
        }

        // 房间边界
        px = clamp(px, -5.0f, 5.0f);
        pz = clamp(pz, -11.5f, 13f);

        vy += GRAVITY * dt;
        py += vy * dt;
        if (py <= 0f) {
            py = 0f;
            vy = 0f;
            grounded = true;
        }

        float moving = Math.abs(moveX) + Math.abs(moveY);
        if (moving > 0.08f && grounded) {
            bobPhase += dt * 9.5f;
            bob = (float) Math.sin(bobPhase) * 0.045f;
        } else {
            bob *= 0.9f;
        }

        float eyeY = 1.65f + bob + py;
        float rp = (float) Math.toRadians(pitch);
        float fx = (float) (Math.sin(rad) * Math.cos(rp));
        float fy = (float) Math.sin(rp);
        float fz = (float) (-Math.cos(rad) * Math.cos(rp));

        float tx = EYE_X - px, ty = EYE_Y - eyeY, tz = EYE_Z - pz;
        float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl > 0.001f) {
            tx /= tl; ty /= tl; tz /= tl;
        }
        float dot = fx * tx + fy * ty + fz * tz;
        gazing = dot > 0.965f;

        if (gazing) {
            gaze = clamp(gaze + dt * 0.45f, 0f, 1f);
        } else {
            gaze = clamp(gaze - dt * 0.30f, 0f, 1f);
        }

        if (moving < 0.08f) {
            reverse = clamp(reverse + dt * 0.20f, 0f, 1f);
        } else {
            reverse = clamp(reverse - dt * 0.35f, 0f, 1f);
        }

        // ---- 觐见判定：走近祂，并仰望祂的双眼 ----
        if (!summoned && !frozen) {
            float dToDeity = Math.abs(pz - DEITY_Z);
            if (gaze > 0.68f && dToDeity < 10.5f) {
                summonT += dt;
            } else {
                summonT = Math.max(0f, summonT - dt * 0.6f);
            }
            if (summonT > 0.9f) {
                summoned = true;
                if (summonListener != null) summonListener.onSummoned();
            }
        }

        eyeGlow = 0.30f + gaze * 0.70f;
        // 注视值越高，祂的瞳孔越亮（镜中那双倒影只跟着亮一半）
        for (int i = 0; i < eyeObjs.size(); i++) {
            eyeObjs.get(i).emissive = (i < 2) ? eyeGlow : eyeGlow * 0.45f;
        }

        // 玩家模型跟随相机（位置 + 朝向）：转身时身体同步转动，正面始终迎着视线方向
        if (playerObj != null) {
            playerObj.x = px;
            playerObj.y = py;
            playerObj.z = pz;
            playerObj.rotY = MODEL_FRONT_YAW - yaw;
        }

        // 脚步声：走路时循环播放，停下即静音
        boolean walking = moving > 0.08f && grounded;
        if (stepSoundId > 0) {
            if (walking && stepStreamId == -1) {
                stepStreamId = soundPool.play(stepSoundId, 0.85f, 0.85f, 1, -1, 1f);
            } else if (!walking && stepStreamId != -1) {
                soundPool.stop(stepStreamId);
                stepStreamId = -1;
            }
        }

        // 呼吸声：随紧张度加重（越被注视，呼吸越粗）
        if (breathStreamId != -1) {
            float tension = Math.max(reverse, gaze);
            float bv = 0.22f + tension * 0.55f;
            soundPool.setVolume(breathStreamId, bv, bv);
        }
    }

    private String buildHud() {
        StringBuilder sb = new StringBuilder();
        sb.append("神寂·巡礼  ·  伊赛德亚 — 边界与谎言之庭\n");
        sb.append("注视值  ").append(bar(gaze)).append("  ").append((int) (gaze * 100)).append("%\n");
        sb.append("反注视  ").append(bar(reverse)).append("  ").append((int) (reverse * 100)).append("%\n");
        if (summoned) {
            sb.append("状态：觐见。祂把第一句话掷过来了。");
        } else if (summonT > 0.15f) {
            sb.append("状态：……祂注意到了你。别移开视线。");
        } else if (Math.abs(pz - DEITY_Z) < 10f) {
            sb.append("状态：走近了。仰望祂的双眼。");
        } else if (reverse > 0.85f) {
            sb.append("状态：祂的瞳孔里，映出了你。");
        } else if (gazing) {
            sb.append("状态：你正仰望着祂。");
        } else {
            sb.append("状态：庭中无声。走近祂。");
        }
        return sb.toString();
    }

    private static String bar(float v) {
        int n = (int) (v * 10f + 0.5f);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < n ? '|' : '.');
        sb.append(']');
        return sb.toString();
    }

    // ---------- 场景 ----------

    private void buildScene() {
        // ★ 幂等：切后台久了 EGL 上下文会被系统销毁，回到前台 onSurfaceCreated 会再次调用本方法。
        //   若不清空，场景里的物体（尤其是跟随相机的玩家模型）会被重复叠加，
        //   表现为「复制出来一个/多个玩家模型」。
        objs.clear();
        eyeObjs.clear();
        playerObj = null;

        Mesh box = MeshBuilder.box();
        Mesh sph = MeshBuilder.sphere(12, 8);

        // 环境
        Obj o;
        o = add(box, 0f, -0.25f, -4f, 26f, 0.5f, 60f, 1f, 1f, 1f); o.texId = texFloor; o.uvTiling = 15f;
        floorObj = o;   // ★ 崩塌时，就是这块地板要被抹除
        o = add(box, -6.2f, 3f, -4f, 1.2f, 6f, 60f, 1f, 1f, 1f); o.texId = texWall; o.uvTiling = 18f;
        o = add(box, 6.2f, 3f, -4f, 1.2f, 6f, 60f, 1f, 1f, 1f); o.texId = texWall; o.uvTiling = 18f;
        o = add(box, 0f, 3f, -19.5f, 16f, 6f, 1.2f, 1f, 1f, 1f); o.texId = texWall; o.uvTiling = 12f;
        // 柱子（有碰撞体积，碰撞逻辑见 update()）
        for (int i = 0; i + 1 < PILLARS_XZ.length; i += 2) {
            o = add(box, PILLARS_XZ[i], 3f, PILLARS_XZ[i + 1], 1.0f, 6f, 1.0f, 1f, 1f, 1f);
            o.texId = texWall;
            o.uvTiling = 3f;
        }

        // ★ 神像：加载 TokenHub 生成的 3D 模型
        Mesh deity = loadMeshBin("ithadea_mesh.bin");
        if (deity != null) {
            o = add(deity, DEITY_X, 0f, DEITY_Z, DEITY_SCALE, DEITY_SCALE, DEITY_SCALE, 1f, 1f, 1f);
            o.texId = texDeity;
            o.uvTiling = 1f;
        } else {
            // 兜底：万一模型加载失败，用简单形体占位
            add(box, DEITY_X, 4f, DEITY_Z, 2.5f, 8f, 2.0f, 0.09f, 0.12f, 0.32f);
        }

        // ★ 玩家模型（跟随相机的身体）
        Mesh player = loadMeshBin("player_mesh.bin");
        if (player != null) {
            playerObj = add(player, px, 0f, pz, 1.5f, 1.5f, 1.5f, 1f, 1f, 1f);
            playerObj.texId = texPlayer;
            playerObj.uvTiling = 1f;
        }

        // 双眼（自发光；同时登记进 eyeObjs，供凝视时增亮）
        for (int s = -1; s <= 1; s += 2) {
            o = add(sph, EYE_X + 0.32f * s, EYE_Y, EYE_Z, 0.5f, 0.5f, 0.35f, 1.0f, 0.85f, 0.25f);
            o.emissive = 0.9f;
            eyeObjs.add(o);
        }
    }

    /**
     * 无界 · 纯白走廊
     * 一条笔直、无门无窗、无尽头、无地平线的走廊：白到失去参照，
     * 唯一还剩的「边界」，是玩家自己的轮廓。（靠雾 + 过曝把尽头抹平，几乎零美术量）
     */
    private void buildCorridor() {
        objs.clear();
        playerObj = null;
        floorObj = null;
        Mesh box = MeshBuilder.box();
        Obj o;
        // 板材网格 2.6m 一格：260m 长的面上 U/V 分开平铺，格子才是正方形。
        // 这些淡到几乎看不见的接缝，就是玩家眼里「我还在往前走」的唯一证据。
        o = add(box, 0f, -0.25f, -80f, 5.2f, 0.5f, 260f, 1f, 1f, 1f);              // 地板
        o.texId = texCorridor; o.uvTiling = 2f; o.uvTilingV = 100f;
        o = add(box, 0f, 3.45f, -80f, 5.2f, 0.5f, 260f, 0.98f, 0.98f, 1f);         // 天花板
        o.texId = texCorridor; o.uvTiling = 2f; o.uvTilingV = 100f;
        for (int s = -1; s <= 1; s += 2) {                                          // 左右墙
            o = add(box, 2.7f * s, 1.60f, -80f, 0.4f, 3.7f, 260f, 0.95f, 0.95f, 0.99f);
            o.texId = texCorridor; o.uvTiling = 100f; o.uvTilingV = 1.42f;
        }
    }

    /**
     * ★ 世界海 · 白色之海
     *
     * 一片乳白的流质之海：没有岸、没有天、没有参照，海镜之下映着万物的倒影。
     * 蒙尔斯洛斯远立雾中 —— 不逼近、不言语，只在；这一境属于「听觉」。
     */
    private void buildWhiteSea() {
        objs.clear();
        eyeObjs.clear();
        playerObj = null;
        floorObj = null;
        Obj o;

        // ★ 海面：一整片会「呼吸」的乳白流质（起伏网格 + 柔和水纹贴图）
        //   不再拿走廊的砖纹去平铺 —— 那看着像地板，不像海。
        o = new Obj();
        o.mesh = makeSeaPlane(300f, 96, 0.11f);
        o.x = 0f; o.y = 0f; o.z = -60f;
        o.sx = 1f; o.sy = 1f; o.sz = 1f;
        o.tr = 0.985f; o.tg = 0.985f; o.tb = 1.0f;
        o.texId = texSea; o.uvTiling = 30f;
        o.emissive = 0.16f;            // 海面自身泛着极淡的光，像一路过曝的白
        objs.add(o);

        // ★ 蒙尔斯洛斯：白雾尽头远立（z=-42，被雾半掩）
        Mesh msls = loadMeshBin("msls_mesh.bin");
        if (msls != null) {
            o = add(msls, 0f, 0f, -42f, 14f, 14f, 14f, 1f, 1f, 1f);
            o.texId = texMsls; o.uvTiling = 1f;
            // 海镜倒影：y 翻转副本，沉在海面之下，淡一些（仿镜室的做法）
            o = add(msls, 0f, 0f, -42f, 14f, -14f, 14f, 0.74f, 0.74f, 0.82f);
            o.texId = texMsls; o.uvTiling = 1f;
        } else {
            add(MeshBuilder.box(), 0f, 6f, -42f, 3f, 12f, 3f, 0.9f, 0.9f, 0.95f); // 兜底占位
        }
    }

    /**
     * 程序生成的海面网格：一张 size×size 的细分平面，顶点带极小的柔和起伏。
     * 起伏很浅（远小于视点高度），但足以让方向光在水面拉出「波光」的明暗 —— 这才像海。
     */
    private Mesh makeSeaPlane(float size, int seg, float amp) {
        int vCount = (seg + 1) * (seg + 1);
        float[] verts = new float[vCount * 11];
        short[] idx = new short[seg * seg * 6];
        int vi = 0;
        for (int j = 0; j <= seg; j++) {
            float v = (float) j / seg;
            float z = -size * 0.5f + size * v;
            for (int i = 0; i <= seg; i++) {
                float u = (float) i / seg;
                float x = -size * 0.5f + size * u;
                double a = x * 0.105 + z * 0.062;
                double b = z * 0.140 - x * 0.046;
                double c2 = (x + z) * 0.188;
                float y = amp * (float) (Math.sin(a) * 0.5 + Math.sin(b) * 0.3 + Math.sin(c2) * 0.2);
                // 法线 ≈ 高度梯度 (∂y/∂x, ∂y/∂z)
                float dyx = amp * (float) (Math.cos(a) * 0.105 * 0.5
                        + Math.cos(b) * (-0.046) * 0.3 + Math.cos(c2) * 0.188 * 0.2);
                float dyz = amp * (float) (Math.cos(a) * 0.062 * 0.5
                        + Math.cos(b) * 0.140 * 0.3 + Math.cos(c2) * 0.188 * 0.2);
                float nx = -dyx, ny = 1f, nz = -dyz;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl; ny /= nl; nz /= nl;
                verts[vi++] = x; verts[vi++] = y; verts[vi++] = z;
                verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz;
                verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f;
                verts[vi++] = u; verts[vi++] = v;
            }
        }
        int ii = 0;
        for (int j = 0; j < seg; j++) {
            for (int i = 0; i < seg; i++) {
                short p0 = (short) (j * (seg + 1) + i);
                short p1 = (short) (p0 + 1);
                short p2 = (short) (p0 + seg + 1);
                short p3 = (short) (p2 + 1);
                idx[ii++] = p0; idx[ii++] = p2; idx[ii++] = p1;
                idx[ii++] = p1; idx[ii++] = p2; idx[ii++] = p3;
            }
        }
        return new Mesh(verts, idx);
    }

    /**
     * ★ 审判场 · 圆形镜室（预览图那个）
     *
     * 无门无窗的圆形镜室：12 根石柱环绕，伊赛德亚立于圆心，脚下是一面镜子。
     * 镜子不靠反射贴图 —— 所有体型都用「上下贯通」的 mesh（y 取 −22 … +22），
     * 实体与倒影是同一份几何，在 y=0 处天然接缝；只有神像因为不对称，才单独补一份 y 翻转的副本。
     */
    private void buildCourtRoom() {
        objs.clear();
        eyeObjs.clear();
        playerObj = null;
        floorObj = null;

        Mesh wall = MeshBuilder.ring(16f, -22f, 22f, 96, true);  // 圆墙（内壁朝内 + 上下贯通 = 墙 + 倒影）
        Mesh pillar = MeshBuilder.ring(0.55f, -22f, 22f, 10);  // 石柱（同上）
        Mesh candle = MeshBuilder.ring(0.26f, -0.52f, 0.52f, 10);
        Mesh dome = MeshBuilder.disc(16f, 22f, 64, false);
        Mesh base = MeshBuilder.disc(16f, -22f, 64, true);
        Mesh sph = MeshBuilder.sphere(12, 8);
        Mesh box = MeshBuilder.box();

        Obj o;
        o = add(wall, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f); o.texId = texWall; o.uvTiling = 10f;
        o = add(dome, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f); o.texId = texWall; o.uvTiling = 4f;
        o = add(base, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f); o.texId = texWall; o.uvTiling = 4f;

        // 十二根石柱（错开半格，好让正前方空出来，不挡神像）
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2.0 * (k + 0.5) / 12.0;
            float x = (float) (Math.cos(a) * 13.1);
            float z = (float) (Math.sin(a) * 13.1);
            o = add(pillar, x, 0f, z, 1f, 1f, 1f, 1f, 1f, 1f); o.texId = texWall; o.uvTiling = 6f;
        }

        // ★ 伊赛德亚立于圆心（tokenhub 生成的模型，正面朝 +Z，正对玩家）
        Mesh deity = loadMeshBin("ithadea_mesh.bin");
        if (deity != null) {
            o = add(deity, 0f, 0f, 0f, 12f, 12f, 12f, 1f, 1f, 1f); o.texId = texDeity; o.uvTiling = 1f;
            // 镜中倒影：y 轴翻转（不对称的它，必须单独补一份）
            o = add(deity, 0f, 0f, 0f, 12f, -12f, 12f, 0.40f, 0.40f, 0.46f); o.texId = texDeity; o.uvTiling = 1f;
        } else {
            o = add(box, 0f, 5f, 0f, 2.5f, 10f, 2.0f, 0.09f, 0.12f, 0.32f);
        }

        // 双眼（自发光）：镜中那一对暗一些
        for (int s = -1; s <= 1; s += 2) {
            o = add(sph, 0.32f * s, 9.6f, 0.35f, 0.5f, 0.5f, 0.35f, 1.0f, 0.85f, 0.25f);
            o.emissive = 1.05f;
            eyeObjs.add(o);
        }
        for (int s = -1; s <= 1; s += 2) {
            o = add(sph, 0.32f * s, -9.6f, 0.35f, 0.5f, -0.5f, 0.35f, 0.65f, 0.55f, 0.18f);
            o.emissive = 0.45f;
            eyeObjs.add(o);
        }

        // 神像脚边那九支酒烛（一杯一声）
        for (int k = 0; k < 9; k++) {
            double a = -0.62 + 0.155 * k;
            float x = (float) (Math.sin(a) * 4.6);
            float z = (float) (Math.cos(a) * 4.6);
            o = add(candle, x, 0f, z, 1f, 1f, 1f, 0.88f, 0.70f, 0.36f);
            o.emissive = 0.9f;
        }
    }

    private Obj add(Mesh mesh, float x, float y, float z,
                    float sx, float sy, float sz,
                    float r, float g, float b) {
        Obj o = new Obj();
        o.mesh = mesh;
        o.x = x; o.y = y; o.z = z;
        o.sx = sx; o.sy = sy; o.sz = sz;
        o.tr = r; o.tg = g; o.tb = b;
        objs.add(o);
        return o;
    }

    private void drawObj(Obj o) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, o.x, o.y, o.z);
        if (o.rotZ != 0f) Matrix.rotateM(model, 0, o.rotZ, 0f, 0f, 1f);
        if (o.rotX != 0f) Matrix.rotateM(model, 0, o.rotX, 1f, 0f, 0f);
        if (o.rotY != 0f) Matrix.rotateM(model, 0, o.rotY, 0f, 1f, 0f);
        Matrix.scaleM(model, 0, o.sx, o.sy, o.sz);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);

        System.arraycopy(model, 0, tmp4, 0, 16);
        Matrix.invertM(tmp4, 0, tmp4, 0);
        Matrix.transposeM(tmp4, 0, tmp4, 0);
        nrm3[0] = tmp4[0]; nrm3[1] = tmp4[1]; nrm3[2] = tmp4[2];
        nrm3[3] = tmp4[4]; nrm3[4] = tmp4[5]; nrm3[5] = tmp4[6];
        nrm3[6] = tmp4[8]; nrm3[7] = tmp4[9]; nrm3[8] = tmp4[10];

        GLES20.glUniformMatrix4fv(shader.uMVP, 1, false, mvp, 0);
        GLES20.glUniformMatrix3fv(shader.uNormalMat, 1, false, nrm3, 0);
        GLES20.glUniform3f(shader.uTint, o.tr, o.tg, o.tb);
        GLES20.glUniform1f(shader.uEmissive, o.emissive);
        float tv = (o.uvTilingV > 0f) ? o.uvTilingV : o.uvTiling;
        GLES20.glUniform2f(shader.uUVTiling, o.uvTiling, tv);

        if (o.texId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, o.texId);
            GLES20.glUniform1i(shader.uTex, 0);
            GLES20.glUniform1f(shader.uHasTex, 1f);
        } else {
            GLES20.glUniform1f(shader.uHasTex, 0f);
        }

        o.mesh.draw(shader);
    }

    /** 初始化音效（幂等：上下文重建时不要重复创建 SoundPool 或叠加播放） */
    private void initAudio() {
        if (soundPool != null) return;
        soundPool = new android.media.SoundPool.Builder().setMaxStreams(4).build();
        try {
            android.content.res.AssetFileDescriptor afd = ctx.getAssets().openFd("audio/step.mp3");
            stepSoundId = soundPool.load(afd, 1);
            afd.close();
        } catch (Exception e) {
            stepSoundId = -1;
        }
        // 呼吸声：环境底噪，循环（SoundPool，GL 线程安全）
        try {
            android.content.res.AssetFileDescriptor afd = ctx.getAssets().openFd("audio/breath.mp3");
            breathSoundId = soundPool.load(afd, 1);
            afd.close();
        } catch (Exception e) {
            breathSoundId = -1;
        }
        if (breathSoundId > 0) {
            breathStreamId = soundPool.play(breathSoundId, 0.30f, 0.30f, 1, -1, 1f);
        }
    }

    /** 读取 SJM1 二进制网格 */
    private Mesh loadMeshBin(String path) {
        try {
            InputStream is = ctx.getAssets().open(path);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            ByteBuffer bb = ByteBuffer.wrap(bos.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
            bb.position(4); // skip magic
            int vCount = bb.getInt();
            int iCount = bb.getInt();
            float[] verts = new float[vCount * 11];
            for (int i = 0; i < vCount; i++) {
                int o = i * 11;
                verts[o] = bb.getFloat();
                verts[o + 1] = bb.getFloat();
                verts[o + 2] = bb.getFloat();
                verts[o + 3] = bb.getFloat();
                verts[o + 4] = bb.getFloat();
                verts[o + 5] = bb.getFloat();
                verts[o + 6] = 1f;
                verts[o + 7] = 1f;
                verts[o + 8] = 1f;
                verts[o + 9] = bb.getFloat();
                verts[o + 10] = 1f - bb.getFloat();   // 翻转 V（OBJ 图像坐标 → OpenGL）
            }
            short[] idx = new short[iCount];
            for (int i = 0; i < iCount; i++) idx[i] = bb.getShort();
            return new Mesh(verts, idx);
        } catch (Exception e) {
            return null;
        }
    }

    private int loadTexture(String path, boolean repeat) {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        try {
            InputStream is = ctx.getAssets().open(path);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            bmp.recycle();
            is.close();
        } catch (Exception e) {
            GLES20.glDeleteTextures(1, tex, 0);
            return 0;
        }
        return tex[0];
    }

    private static class Obj {
        Mesh mesh;
        float x, y, z;
        float rotX = 0f, rotY = 0f, rotZ = 0f;
        float sx = 1f, sy = 1f, sz = 1f;
        float tr, tg, tb;
        float emissive;
        int texId;
        float uvTiling = 1f;
        float uvTilingV = 0f;   // 0 = 跟随 uvTiling；>0 时 U/V 分开平铺（长走廊要方形格）
    }
}
