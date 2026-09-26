package com.shenji.game;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * ★ 调试日志环形缓冲：内存里留最近 N 条，悬浮球点开看。
 * 线程安全（jev/llm 判定在工作线程回调）。
 */
public final class DebugLog {

    private static final int MAX = 300;
    private static final ArrayDeque<String> LINES = new ArrayDeque<String>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private DebugLog() {}

    public static synchronized void log(String tag, String msg) {
        LINES.addLast(FMT.format(new Date()) + " [" + tag + "] " + msg);
        while (LINES.size() > MAX) LINES.removeFirst();
    }

    public static synchronized void logErr(String tag, String msg, Throwable t) {
        log(tag, msg + " -> " + t);
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        // 堆栈只留前 5 行，省地方
        String[] ls = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, ls.length); i++) sb.append("    ").append(ls[i]).append('\n');
        log(tag, sb.toString().trim());
    }

    public static synchronized String dump() {
        if (LINES.isEmpty()) return "（暂无日志）";
        StringBuilder sb = new StringBuilder();
        for (String l : LINES) sb.append(l).append('\n');
        return sb.toString();
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
