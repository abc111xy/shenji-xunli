package com.shenji.game;

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
 * ★ jev 模式审判（opencode zen · jev-1.13-free）
 *
 * 与 LlmJudge 不同：jev 是「状态 → 结构化问答」模型，不写散文，
 * 只给选项 + 概率分布。所以祂的审判更像一台没有感情的天平：
 *   - kind 由 choice 题给出（硬撑 / 精准 / 沉默边缘）
 *   - 神的回应降级为本地文案（祂不说话，祂只宣判）
 *
 * 匿名免 key，任何网络 / 解析异常都兜底为 kind=1（答准）+ 本地回应，
 * 审判流程永不中断——与 LlmJudge 同一信条。
 */
public final class JevJudge {

    private static final String ENDPOINT = "https://opencode.ai/zen/v1/systemone";
    private static final String MODEL = "jev-1.13-free";
    private static final int TIMEOUT_MS = 20000;

    private JevJudge() {}

    /** 与 LlmJudge.judge 签名一致，调用方无感切换 */
    public static void judge(final NineWines.Question q, final String answer,
                             final List<NineWines.AnswerRecord> history,
                             final LlmJudge.Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LlmJudge.Verdict v = null;
                String err = null;
                try {
                    v = callApi(q, answer, history);
                    if (v != null) DebugLog.log("jev", "审判完成 kind=" + v.kind);
                } catch (Throwable t) {
                    DebugLog.logErr("jev", "请求失败", t);
                    err = "网络未触及祂：" + t.getClass().getSimpleName() + " " + t.getMessage();
                }
                if (v == null) {
                    // ★ 不重试不兜底判决：直接把错误亮给玩家（主人要求）
                    v = new LlmJudge.Verdict(-1, "⚠ " + (err != null ? err : "未知错误") + "\n（本轮未审判，可再次呈上）");
                    DebugLog.log("jev", "审判中断，已亮出错误");
                }
                if (cb != null) cb.onVerdict(v);
            }
        }, "jev-judge").start();
    }

    // ---------------------------------------------------------------- 网络

    private static LlmJudge.Verdict callApi(NineWines.Question q, String answer,
                                            List<NineWines.AnswerRecord> history) throws Exception {
        // ---- state：把审判现场压缩成一段叙述 ----
        StringBuilder state = new StringBuilder();
        state.append("外神伊赛德亚正在边界与谎言之庭审判一名玩家的回答。");
        state.append("本轮定律：").append(q.law).append('。');
        state.append("题面：").append(q.thesis).append('。');
        state.append("神的逼问：").append(q.ask).append('。');
        state.append("玩家的自定义回答：").append(answer).append('。');
        if (history != null && !history.isEmpty()) {
            state.append("玩家此前的回答：");
            for (int i = 0; i < history.size(); i++) {
                NineWines.AnswerRecord a = history.get(i);
                state.append("「").append(a.text).append("」");
                if (a.catastrophic) state.append("(崩塌)");
            }
        }

        // ---- questions：三选项 choice + 傲慢分 score ----
        JSONObject criteria = new JSONObject();
        criteria.put("stubborn", "玩家断言自己能站到边界之外、否认边界、用蛮力/逃避回答悖论、或辱骂/攻击/挑衅神明（如脏话、人身攻击、答非所问）——应触发崩塌");
        criteria.put("precise", "玩家认出了界的自指/递归本质、以悖论回应悖论、或把回答收回界内——应判答准");
        criteria.put("silent", "玩家长考、回避实质、或回答近乎无物——答准但需额外记账");

        JSONObject verdict = new JSONObject();
        verdict.put("type", "choice");
        verdict.put("instructions", "审判这名玩家的回答，只能三选一");
        verdict.put("criteria", criteria);

        JSONObject pride = new JSONObject();
        pride.put("type", "score");
        pride.put("instructions", "玩家回答里透出多少傲慢？");
        pride.put("criteria", new org.json.JSONArray()
                .put("谦卑").put("平常").put("傲慢"));

        JSONObject questions = new JSONObject();
        questions.put("verdict", verdict);
        questions.put("pride", pride);

        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("state", state.toString());
        body.put("questions", questions);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            DebugLog.log("jev", "HTTP " + code);
            if (code < 200 || code >= 300) {
                InputStream es = conn.getErrorStream();
                String errBody = readAll(es);
                DebugLog.log("jev", "err: " + (errBody.length() > 400 ? errBody.substring(0, 400) : errBody));
                // ★ 亮出真实错误码（429 等），不静默兜底
                return new LlmJudge.Verdict(-1, "⚠ HTTP " + code + "：祂此刻缄默（" +
                        (code == 429 ? "限流，稍后再呈" : "天平异常") + "）\n（本轮未审判，可再次呈上）");
            }
            String resp = readAll(conn.getInputStream());
            DebugLog.log("jev", "resp: " + (resp.length() > 400 ? resp.substring(0, 400) : resp));

            // ---- 解析：choice → kind ----
            JSONObject answers = new JSONObject(resp).getJSONObject("answers");
            String choice = answers.getJSONObject("verdict").getString("choice");
            int kind;
            if ("stubborn".equals(choice)) kind = 0;
            else if ("silent".equals(choice)) kind = 2;
            else kind = 1;

            // jev 不写判词——祂只宣判。回本地文案。
            String reply = NineWines.localReply(q.round, kind);
            return new LlmJudge.Verdict(kind, reply);
        } finally {
            if (conn != null) conn.disconnect();
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
}
