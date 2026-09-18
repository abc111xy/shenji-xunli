package com.shenji.game;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ★ 自定义回答的大模型审判（agnes-2.5-flash）
 *
 * 固定三选项仍走本地文案（LocalProvider）；只有玩家「自定义回答」时，
 * 由本类把回答发给模型，由祂判定性质并亲自回应：
 *   kind 0 = 硬撑（崩塌） / 1 = 精准（答准） / 2 = 沉默边缘（答准但记账）
 *
 * 凭证按主人要求明文写入 APK（反编译可见，属已知取舍）。
 * 任何网络 / 解析异常都兜底为 kind=1（答准）+ 本地回应，审判流程永不中断。
 */
public final class LlmJudge {

    // ---- 明文凭证（主人要求直接写进 APK）----
    private static final String API_KEY = "sk-A0PWHfBRPIt5zRdON5CoDkE4rJ3Jz2BGuhpNiSQH8rMo7QjP";
    private static final String BASE_URL = "https://api.agnes-ai.cn/v1";
    private static final String MODEL = "agnes-2.5-flash";

    private static final int TIMEOUT_MS = 20000;

    public static final class Verdict {
        public final int kind;      // 0 崩塌 / 1 精准 / 2 沉默
        public final String reply;  // 神的回应
        Verdict(int kind, String reply) {
            this.kind = kind;
            this.reply = reply;
        }
    }

    public interface Callback {
        /** 回调发生在工作线程，UI 层记得 post 回主线程 */
        void onVerdict(Verdict v);
    }

    private LlmJudge() {}

    /** 异步审判：固定开一条工作线程，绝不阻塞 UI / GL 线程 */
    public static void judge(final NineWines.Question q, final String answer,
                             final List<NineWines.AnswerRecord> history, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Verdict v = null;
                try {
                    v = callApi(q, answer, history);
                } catch (Throwable ignored) {
                    v = null;
                }
                if (v == null || v.reply == null || v.reply.length() == 0) {
                    // 兜底：网络失败也算你答准了——祂不因为尘世的断网而多杀一人
                    v = new Verdict(1, NineWines.localReply(q.round, 1));
                }
                if (cb != null) cb.onVerdict(v);
            }
        }, "llm-judge").start();
    }

    // ---------------------------------------------------------------- 网络

    private static Verdict callApi(NineWines.Question q, String answer,
                                   List<NineWines.AnswerRecord> history) throws Exception {
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);

        StringBuilder user = new StringBuilder();
        user.append("《理论边界论》九大定律：自指边界律 / 递归升维律 / 差异先于实体律 / 观测即造界律 / 内外互构律 / 边界耗散律 / 元界不可逾律 / 叠界共存律 / 归零即重启律。\n\n");
        user.append("本轮定律：").append(q.law).append('\n');
        user.append("题面：").append(q.thesis).append('\n');
        user.append("你的逼问：").append(q.ask).append('\n');
        user.append("玩家自定义回答：").append(answer).append('\n');
        if (history != null && !history.isEmpty()) {
            user.append("他此前说过：\n");
            for (int i = 0; i < history.size(); i++) {
                NineWines.AnswerRecord a = history.get(i);
                user.append("  第 ").append(a.round).append(" 酒：").append(a.text);
                if (a.catastrophic) user.append("（崩塌）");
                user.append('\n');
            }
        }
        user.append("\n请审判。");

        JSONObject usr = new JSONObject();
        usr.put("role", "user");
        usr.put("content", user.toString());

        JSONArray messages = new JSONArray();
        messages.put(sys);
        messages.put(usr);

        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 220);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + "/chat/completions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) return null;

            JSONObject root = new JSONObject(resp);
            String content = root.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            return parseVerdict(content, q);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ---------------------------------------------------------------- 解析

    /** 模型可能给裸 JSON、可能包 markdown 代码块——抠出第一段 {...} 解析 */
    private static Verdict parseVerdict(String content, NineWines.Question q) {
        if (content == null) return null;
        int i0 = content.indexOf('{');
        int i1 = content.lastIndexOf('}');
        if (i0 < 0 || i1 <= i0) return null;
        try {
            JSONObject o = new JSONObject(content.substring(i0, i1 + 1));
            int kind = o.optInt("kind", 1);
            if (kind < 0 || kind > 2) kind = 1;
            String reply = o.optString("reply", "").trim();
            if (reply.length() == 0) reply = NineWines.localReply(q.round, kind);
            return new Verdict(kind, reply);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    // ---------------------------------------------------------------- 提示词（与本地验证一致）

    private static final String SYSTEM_PROMPT =
            "你是外神「伊赛德亚」，边界与谎言之庭的审判者。玩家没有用预设选项，而是给出了一句自定义回答，你要亲自审判它。\n\n" +
            "【判定标准】kind 取值：\n" +
            "0 = 硬撑（玩家断言自己能站到边界之外、否认边界、或用蛮力/逃避回答悖论——触发崩塌）\n" +
            "1 = 精准（玩家认出了界的自指/递归本质、以悖论回应悖论、或把回答收回界内——视为答准）\n" +
            "2 = 沉默边缘（玩家长考、回避实质、或回答近乎无物——视为答准但额外记账）\n\n" +
            "【你的回应】以伊赛德亚的口吻写一到两句：冷、短、优雅、带悖论感。回应用简体中文，总长不超过 60 字。\n\n" +
            "只输出 JSON，格式：{\"kind\": 0或1或2, \"reply\": \"你的回应\"}";
}
