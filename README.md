# 神寂 · 巡礼　Shenji · Pilgrimage

> 安卓 3D 第一人称心理恐怖游戏 · **原型** · 「佩迦神话」世界观
> Android 3D first-person psychological horror prototype — *Peiga Mythology*

一个**不用游戏引擎**、用纯 Java + OpenGL ES 2.0 手写的安卓 3D 恐怖原型。
玩家作为「巡礼者」坠入佩迦神话诸神域，在纯白、无声、失去参照的边界之地里，
一步步确认「自己是否还在」。

---

## 🎬 当前版本：v0.78 「世界海 · 白色之海」

一闪之后，不再是下一座神域，而是一片**无岸、无天、无参照**的乳白流质之海。
蒙尔斯洛斯远立白雾尽头——不逼近、不言语，只在。

- **可以自由走动**：这一次方向盘交给你，自己走向雾中的祂
- 海面是一整片**会呼吸的起伏水镜**（程序生成的波光网格 + 柔和乳白水纹）
- 海镜倒影（负 sy 几何副本）；白雾吞掉地平线
- 走到祂脚下 → 站定仰望 → 亮出「第二位神域」字卡
- 主题：**第十三觉 · 听觉** —— 脚下的海不发声，只剩自己的呼吸

## 🗺️ 已实现的三重境

| 境 | 版本 | 说明 |
|---|---|---|
| 审判场 · 圆形镜室 | v0.74 | 12 根石柱环绕，伊赛德亚立于圆心，脚下是镜面（预览图场景） |
| 无界 · 纯白走廊 | v0.75 | 无门无窗无尽头，白到失去参照；脚步声被一步步抽走 |
| 世界海 · 白色之海 | v0.78 | 乳白海镜之上，**可自由行走**，走向雾中的蒙尔斯洛斯 |

## 🛠 技术要点

- **纯 Java + OpenGL ES 2.0**，零游戏引擎（无 Unity / Unreal）
- 自研渲染器 `GameRenderer.java`（约 900 行）：手写光照 · 雾 · sRGB↔线性色彩管线
- 自研 SJM1 二进制网格格式（`loadMeshBin`，short 索引，≤ 65535 顶点）
- 3D 模型由 **TokenHub 图生 3D**（hy-3d-3.0）+ 4096² PBR 贴图，离线简化为 SJM1
- `minSdk 24`（安卓 7.0） / `targetSdk 36`（安卓 16）—— 一个包通吃 7 / 9 / 12 / 14 / 16
- 固定签名（`shenji.keystore`），保证跨版本覆盖安装

## 📦 目录结构

```
prototype-android/
├── app/
│   ├── build.gradle              # versionCode 77 / versionName 0.77
│   ├── shenji.keystore           # 固定调试签名
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/               # 网格(.bin) / 贴图 / 音频
│       └── java/com/shenji/game/ # 全部游戏逻辑
│           ├── GameRenderer.java   # 渲染器 · 场景 · 相机 · 雾
│           ├── GLCommon.java       # 着色器 / 网格 / 纹理
│           ├── GameView.java       # GLSurfaceView 包装
│           ├── MainActivity.java   # 流程编排
│           ├── SplashScreen.java / NextRealmView.java / TrialView.java
│           └── JoystickView.java / JumpButton.java / NineWines.java
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 🔨 构建

需要 JDK 17、Android SDK（build-tools 36 / platform android-36）、Gradle 8+。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 📲 下载 APK

成品 APK 见本仓库 **[Releases](../../releases)** 页面（免安装工具，直接下载安装）。

> 本工程为**原型 / 学习性质**，美术资源多为程序生成或 AI 生成，非商业用途。

---

*「佩迦神话」系列 · 由糯米协助开发 🐾*
