package com.shenji.game;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** 着色器：顶点色 + 纹理 + 方向光 + 雾 + 自发光 */
class Shader {

    final int program;
    final int aPos, aNormal, aColor, aUV;
    final int uMVP, uNormalMat, uLightDir, uAmbient;
    final int uFogColor, uFogNear, uFogFar, uTint, uEmissive;
    final int uHasTex, uTex, uUVTiling;

    private static final String VS =
            "uniform mat4 uMVP;\n" +
            "uniform mat3 uNormalMat;\n" +
            "uniform vec2 uUVTiling;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying vec2 vUV;\n" +
            "varying float vDepth;\n" +
            "void main() {\n" +
            "  vNormal = normalize(uNormalMat * aNormal);\n" +
            "  vColor = aColor;\n" +
            "  vUV = aUV * uUVTiling;\n" +
            "  vec4 p = uMVP * vec4(aPos, 1.0);\n" +
            "  vDepth = p.w;\n" +
            "  gl_Position = p;\n" +
            "}\n";

    private static final String FS =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform float uHasTex;\n" +
            "uniform vec3 uLightDir;\n" +
            "uniform vec3 uAmbient;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogNear;\n" +
            "uniform float uFogFar;\n" +
            "uniform vec3 uTint;\n" +
            "uniform float uEmissive;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying vec2 vUV;\n" +
            "varying float vDepth;\n" +
            "void main() {\n" +
            "  vec3 base = vColor;\n" +
            "  if (uHasTex > 0.5) { base *= texture2D(uTex, vUV).rgb; }\n" +
            "  base = pow(base, vec3(2.2));\n" +
            "  vec3 n = normalize(vNormal);\n" +
            "  float diff = max(dot(n, normalize(uLightDir)), 0.0);\n" +
            "  vec3 col = base * uTint * (uAmbient + diff * 0.9);\n" +
            "  col += base * uEmissive;\n" +
            "  float f = clamp((vDepth - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);\n" +
            "  col = mix(col, uFogColor, f);\n" +
            "  col = pow(col, vec3(1.0 / 2.2));\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    Shader() {
        int v = compile(GLES20.GL_VERTEX_SHADER, VS);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, FS);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, v);
        GLES20.glAttachShader(program, f);
        GLES20.glLinkProgram(program);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aNormal = GLES20.glGetAttribLocation(program, "aNormal");
        aColor = GLES20.glGetAttribLocation(program, "aColor");
        aUV = GLES20.glGetAttribLocation(program, "aUV");
        uMVP = GLES20.glGetUniformLocation(program, "uMVP");
        uNormalMat = GLES20.glGetUniformLocation(program, "uNormalMat");
        uLightDir = GLES20.glGetUniformLocation(program, "uLightDir");
        uAmbient = GLES20.glGetUniformLocation(program, "uAmbient");
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor");
        uFogNear = GLES20.glGetUniformLocation(program, "uFogNear");
        uFogFar = GLES20.glGetUniformLocation(program, "uFogFar");
        uTint = GLES20.glGetUniformLocation(program, "uTint");
        uEmissive = GLES20.glGetUniformLocation(program, "uEmissive");
        uHasTex = GLES20.glGetUniformLocation(program, "uHasTex");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        uUVTiling = GLES20.glGetUniformLocation(program, "uUVTiling");
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    void use() {
        GLES20.glUseProgram(program);
    }
}

/** 静态网格：位置(3) + 法线(3) + 颜色(3) + UV(2) */
class Mesh {

    private static final int STRIDE = 11 * 4;

    private final FloatBuffer vb;
    private final ShortBuffer ib;
    private final int indexCount;

    Mesh(float[] verts, short[] idx) {
        indexCount = idx.length;
        vb = ByteBuffer.allocateDirect(verts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vb.put(verts).position(0);
        ib = ByteBuffer.allocateDirect(idx.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        ib.put(idx).position(0);
    }

    void draw(Shader sh) {
        vb.position(0);
        GLES20.glVertexAttribPointer(sh.aPos, 3, GLES20.GL_FLOAT, false, STRIDE, vb);
        GLES20.glEnableVertexAttribArray(sh.aPos);
        vb.position(3);
        GLES20.glVertexAttribPointer(sh.aNormal, 3, GLES20.GL_FLOAT, false, STRIDE, vb);
        GLES20.glEnableVertexAttribArray(sh.aNormal);
        vb.position(6);
        GLES20.glVertexAttribPointer(sh.aColor, 3, GLES20.GL_FLOAT, false, STRIDE, vb);
        GLES20.glEnableVertexAttribArray(sh.aColor);
        vb.position(9);
        GLES20.glVertexAttribPointer(sh.aUV, 2, GLES20.GL_FLOAT, false, STRIDE, vb);
        GLES20.glEnableVertexAttribArray(sh.aUV);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, ib);
    }
}

/** 程序化几何：立方体 / 球体 / 圆锥（半径 1，高 1，中心在原点） */
class MeshBuilder {

    /** 单位立方体，每面 UV 0..1 */
    static Mesh box() {
        float h = 0.5f;
        float[][] faces = {
                { 0, 0, 1, -h, -h, h, h, -h, h, h, h, h, -h, h, h },
                { 0, 0, -1, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h },
                { 1, 0, 0, h, -h, h, h, -h, -h, h, h, -h, h, h, h },
                { -1, 0, 0, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h },
                { 0, 1, 0, -h, h, h, h, h, h, h, h, -h, -h, h, -h },
                { 0, -1, 0, -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h },
        };
        float[] uv = { 0, 0, 1, 0, 1, 1, 0, 1 };
        float[] verts = new float[6 * 4 * 11];
        short[] idx = new short[6 * 6];
        int vi = 0, ii = 0;
        for (int f = 0; f < 6; f++) {
            float[] fc = faces[f];
            for (int k = 0; k < 4; k++) {
                verts[vi++] = fc[3 + k * 3];
                verts[vi++] = fc[4 + k * 3];
                verts[vi++] = fc[5 + k * 3];
                verts[vi++] = fc[0];
                verts[vi++] = fc[1];
                verts[vi++] = fc[2];
                verts[vi++] = 1f;
                verts[vi++] = 1f;
                verts[vi++] = 1f;
                verts[vi++] = uv[k * 2];
                verts[vi++] = uv[k * 2 + 1];
            }
            short base = (short) (f * 4);
            idx[ii++] = base;
            idx[ii++] = (short) (base + 1);
            idx[ii++] = (short) (base + 2);
            idx[ii++] = base;
            idx[ii++] = (short) (base + 2);
            idx[ii++] = (short) (base + 3);
        }
        return new Mesh(verts, idx);
    }

    /**
     * 圆柱侧面（不封口）：半径 r，从 y0 到 y1。
     * ★ 镜室的秘诀：把 y0 取负、y1 取正，同一个 mesh 就同时是「实体」和它在镜面上的倒影，
     *   上下天然对称 —— 于是不花一分钱就得到「站在镜子上」的画面。
     */
    static Mesh ring(float r, float y0, float y1, int seg) {
        return ring(r, y0, y1, seg, false);
    }

    /**
     * @param inward 法线朝内（做房间内壁用）。朝外则用于柱子、蜡烛这类实体。
     */
    static Mesh ring(float r, float y0, float y1, int seg, boolean inward) {
        int vCount = (seg + 1) * 2;
        float[] verts = new float[vCount * 11];
        short[] idx = new short[seg * 6];
        int vi = 0;
        for (int i = 0; i <= seg; i++) {
            float u = (float) i / seg;
            double t = u * Math.PI * 2.0;
            float nx = (float) Math.cos(t);
            float nz = (float) Math.sin(t);
            float cx = nx * r, cz = nz * r;
            if (inward) {
                nx = -nx;
                nz = -nz;
            }
            for (int k = 0; k < 2; k++) {
                verts[vi++] = cx;
                verts[vi++] = (k == 0) ? y0 : y1;
                verts[vi++] = cz;
                verts[vi++] = nx;
                verts[vi++] = 0f;
                verts[vi++] = nz;
                verts[vi++] = 1f;
                verts[vi++] = 1f;
                verts[vi++] = 1f;
                verts[vi++] = u;
                verts[vi++] = (k == 0) ? 0f : 1f;
            }
        }
        int ii = 0;
        for (int i = 0; i < seg; i++) {
            short a = (short) (i * 2);
            short b = (short) (a + 1);
            short c = (short) (a + 2);
            short d = (short) (a + 3);
            idx[ii++] = a; idx[ii++] = c; idx[ii++] = b;
            idx[ii++] = b; idx[ii++] = c; idx[ii++] = d;
        }
        return new Mesh(verts, idx);
    }

    /** 圆盘：半径 r，高度 y；up=true 法线朝上（镜面的封盖），false 朝下（穹顶） */
    static Mesh disc(float r, float y, int seg, boolean up) {
        float[] verts = new float[(seg + 2) * 11];
        short[] idx = new short[seg * 3];
        float ny = up ? 1f : -1f;
        int vi = 0;
        verts[vi++] = 0f; verts[vi++] = y; verts[vi++] = 0f;
        verts[vi++] = 0f; verts[vi++] = ny; verts[vi++] = 0f;
        verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f;
        verts[vi++] = 0.5f; verts[vi++] = 0.5f;
        for (int i = 0; i <= seg; i++) {
            float u = (float) i / seg;
            double t = u * Math.PI * 2.0;
            float cx = (float) Math.cos(t) * r, cz = (float) Math.sin(t) * r;
            verts[vi++] = cx; verts[vi++] = y; verts[vi++] = cz;
            verts[vi++] = 0f; verts[vi++] = ny; verts[vi++] = 0f;
            verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f;
            verts[vi++] = 0.5f + cx / (2f * r);
            verts[vi++] = 0.5f + cz / (2f * r);
        }
        for (int i = 0; i < seg; i++) {
            idx[i * 3] = 0;
            idx[i * 3 + 1] = (short) (i + 1);
            idx[i * 3 + 2] = (short) (i + 2);
        }
        return new Mesh(verts, idx);
    }

    /** 单位球（半径 0.5，中心在原点） */
    static Mesh sphere(int segW, int segH) {
        int vCount = (segW + 1) * (segH + 1);
        float[] verts = new float[vCount * 11];
        short[] idx = new short[segW * segH * 6];
        int vi = 0;
        for (int y = 0; y <= segH; y++) {
            float v = (float) y / segH;
            double phi = v * Math.PI;
            float sy = (float) Math.cos(phi);
            float rr = (float) Math.sin(phi);
            for (int x = 0; x <= segW; x++) {
                float u = (float) x / segW;
                double theta = u * Math.PI * 2.0;
                float nx = rr * (float) Math.cos(theta);
                float nz = rr * (float) Math.sin(theta);
                verts[vi++] = nx * 0.5f;
                verts[vi++] = sy * 0.5f;
                verts[vi++] = nz * 0.5f;
                verts[vi++] = nx;
                verts[vi++] = sy;
                verts[vi++] = nz;
                verts[vi++] = 1f;
                verts[vi++] = 1f;
                verts[vi++] = 1f;
                verts[vi++] = u;
                verts[vi++] = v;
            }
        }
        int ii = 0;
        for (int y = 0; y < segH; y++) {
            for (int x = 0; x < segW; x++) {
                short a = (short) (y * (segW + 1) + x);
                short b = (short) (a + 1);
                short c = (short) (a + segW + 1);
                short d = (short) (c + 1);
                idx[ii++] = a; idx[ii++] = c; idx[ii++] = b;
                idx[ii++] = b; idx[ii++] = c; idx[ii++] = d;
            }
        }
        return new Mesh(verts, idx);
    }

    /** 单位圆锥（底半径 0.5，高 1，中心在原点，尖端朝 +Y） */
    static Mesh cone(int seg) {
        int vCount = (seg + 1) * 2 + 2;
        float[] verts = new float[vCount * 11];
        short[] idx = new short[seg * 6];
        int vi = 0;
        // 底部圆周
        for (int i = 0; i <= seg; i++) {
            float u = (float) i / seg;
            double t = u * Math.PI * 2.0;
            float nx = (float) Math.cos(t);
            float nz = (float) Math.sin(t);
            verts[vi++] = nx * 0.5f;   // 侧壁底点
            verts[vi++] = -0.5f;
            verts[vi++] = nz * 0.5f;
            verts[vi++] = nx * 0.707f;
            verts[vi++] = 0.707f;
            verts[vi++] = nz * 0.707f;
            verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f;
            verts[vi++] = u; verts[vi++] = 0f;
        }
        // 侧壁顶点
        for (int i = 0; i <= seg; i++) {
            float u = (float) i / seg;
            double t = u * Math.PI * 2.0;
            float nx = (float) Math.cos(t);
            float nz = (float) Math.sin(t);
            verts[vi++] = 0f;
            verts[vi++] = 0.5f;
            verts[vi++] = 0f;
            verts[vi++] = nx * 0.707f;
            verts[vi++] = 0.707f;
            verts[vi++] = nz * 0.707f;
            verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f;
            verts[vi++] = u; verts[vi++] = 1f;
        }
        // 底部圆心 + 尖端（各一个）
        int baseCenter = vi / 11;
        verts[vi++] = 0f; verts[vi++] = -0.5f; verts[vi++] = 0f;
        verts[vi++] = 0f; verts[vi++] = -1f; verts[vi++] = 0f;
        verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f;
        verts[vi++] = 0.5f; verts[vi++] = 0.5f;

        int ii = 0;
        for (int i = 0; i < seg; i++) {
            // 侧面
            idx[ii++] = (short) i;
            idx[ii++] = (short) (seg + 1 + i);
            idx[ii++] = (short) (i + 1);
            idx[ii++] = (short) (i + 1);
            idx[ii++] = (short) (seg + 1 + i);
            idx[ii++] = (short) (seg + 2 + i);
        }
        return new Mesh(verts, idx);
    }
}
