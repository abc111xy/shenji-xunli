package com.shenji.game;

/**
 * ★ 存档唯一入口（docs/save-schema.md）
 * 键名规范：<domain>:<name>，全小写 ASCII，键名不可改（改键=老存档失效）。
 * SharedPreferences 无版本概念，由本类自管 meta:schema_version 并做迁移。
 */
public final class SaveManager {

    private static android.content.SharedPreferences p;

    public static final int SCHEMA_VERSION = 1;

    private SaveManager() {}

    public static void init(android.content.Context c) {
        p = c.getSharedPreferences("shenji_save", android.content.Context.MODE_PRIVATE);
        int v = getInt("meta:schema_version", 0);
        if (v < 1) {
            // v0 → v1：旧 "shili" 文件 → boon:world_hitch
            android.content.SharedPreferences old = c.getSharedPreferences("shili", android.content.Context.MODE_PRIVATE);
            if (old.getBoolean("owned", false)) setFlag("boon:world_hitch", true);
            old.edit().clear().apply();
            // 旧 "ibliss" 文件 → ibliss:* 全键名（数值在 enterBoss 首次进入时兜底搬迁）
            setInt("meta:schema_version", SCHEMA_VERSION);
        }
    }

    public static boolean getFlag(String k, boolean d) { return p != null && p.getBoolean(k, d); }
    public static void setFlag(String k, boolean v) { if (p != null) p.edit().putBoolean(k, v).apply(); }
    public static int getInt(String k, int d) { return p != null ? p.getInt(k, d) : d; }
    public static void setInt(String k, int v) { if (p != null) p.edit().putInt(k, v).apply(); }
    public static long getLong(String k, long d) { return p != null ? p.getLong(k, d) : d; }
    public static void setLong(String k, long v) { if (p != null) p.edit().putLong(k, v).apply(); }
    public static float getFloat(String k, float d) { return p != null ? p.getFloat(k, d) : d; }
    public static void setFloat(String k, float v) { if (p != null) p.edit().putFloat(k, v).apply(); }

    /** 标记不可中断流程开始；崩溃重启后由上层决定跳过（防永久黑屏/卡死） */
    public static void beginCritical(String tag) {
        setFlag("meta:dirty", true);
        if (p != null) p.edit().putString("meta:dirty_tag", tag).commit();
        setLong("meta:dirty_at", System.currentTimeMillis());
    }
    public static void endCritical() { setFlag("meta:dirty", false); }
    public static boolean isDirty() { return getFlag("meta:dirty", false); }
    public static String dirtyTag() { return p != null ? p.getString("meta:dirty_tag", "") : ""; }
}
