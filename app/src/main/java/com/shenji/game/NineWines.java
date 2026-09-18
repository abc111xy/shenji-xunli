package com.shenji.game;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ 九酒之问 —— 伊赛德亚 · 边界与谎言之庭 的核心审判机制
 *
 * 设定（主人 2026-09-14 拍板）：
 *   1. 「九大定律就是九个问题」——每一酒的题面，就是《理论边界论》里那一条定律本身；
 *   2. 回答方式 = A 预设三选项；
 *   3. 四道铁律：答错 → 脚下边界崩塌、坠入无界；答得越准越接近无界；九轮走完也只坠入更深无界；从不释放任何人。
 *
 * 于是玩家唯一的自由不是「能不能活」，而是「死得多深」：
 *   · 答「准」（kind=1）：你把界划得更牢，边界完整度下降，无界深度 +1，继续下一酒；
 *   · 答「错」（kind=0）：当场崩塌，无界深度 = 已走完的酒数（死得浅）；
 *   · 沉  默（kind=2）：视为答准，但额外记账（隐藏路线伏笔）。
 *
 * 【预留大模型】神的「回应」一律通过 {@link AnswerProvider} 索取。
 *   当前默认实现是本地文案 {@link LocalProvider}；未来接入大模型时，
 *   只需另写一个 RemoteProvider 实现该接口，UI 与流程代码一行都不用改（见文件末尾）。
 */
public class NineWines {

    public static final int ROUNDS = 9;

    // ================================================================ 题面

    /** 一轮质询：题面就是定律本身 */
    public static final class Question {
        public final int round;          // 1..9
        public final String law;         // 定律名，如「自指边界律」
        public final String thesis;      // 定律原句 —— 这就是「题面」（金色小字）
        public final String ask;         // 神的逼问（白色大字）
        public final String[] options;   // 三个预设选项（A / B / C）
        public final int[] kinds;        // 选项性质：0=硬撑(崩塌) 1=精准(陷阱) 2=沉默

        Question(int round, String law, String thesis, String ask, String[] options, int[] kinds) {
            this.round = round;
            this.law = law;
            this.thesis = thesis;
            this.ask = ask;
            this.options = options;
            this.kinds = kinds;
        }

        /** 崩塌项下标；-1 = 本轮不会崩塌（第九酒：三选皆终） */
        public int collapseChoice() {
            for (int i = 0; i < kinds.length; i++) if (kinds[i] == 0) return i;
            return -1;
        }
    }

    private static final Question[] Q = new Question[ROUNDS];

    static {
        Q[0] = new Question(1, "自指边界律",
                "「边界只指向自身，永不在自身之外。」",
                "那么你，能站到它外面吗？",
                new String[]{"能。我跨出去，就是外面。", "不能——可「不能」也是界内的一句话。", "〔沉默〕"},
                new int[]{0, 1, 2});

        Q[1] = new Question(2, "递归升维律",
                "「破界不是消灭边界，是在更高层重新立界。」",
                "你每破开一层，就在更高处立起一层——你的「突破」，算什么？",
                new String[]{"不算什么——破得越多，只是换到更高的牢。", "那就一直破下去，总能破到没有墙的地方。", "〔沉默〕"},
                new int[]{1, 0, 2});

        Q[2] = new Question(3, "差异先于实体律",
                "「先有差，才有界；实体是界稳定后的沉淀。」",
                "你摸到的，是石头，还是那道不被你穿透的差异？",
                new String[]{"是石头。硬的就是硬的，不用绕。", "是差异——「石头」只是我不再穿透它时，随手留下的名字。", "〔沉默〕"},
                new int[]{0, 1, 2});

        Q[3] = new Question(4, "观测即造界律",
                "「未观测的场不是无界，是未成界。」",
                "你不看它的那一刻，它的边界在哪里？",
                new String[]{"不在——不看的那一刻，界还没被裁出来。", "〔沉默〕", "在。我看不看，它都在那里。"},
                new int[]{1, 2, 0});

        Q[4] = new Question(5, "内外互构律",
                "「外面是里面吐出来的影子，里面是外面收回的锚。」",
                "你想站到外面来审判我——可那个「外面」，是谁吐出来的？",
                new String[]{"是真的外面，与这里无关。", "〔沉默〕", "是我从里面吐出来的影子。"},
                new int[]{0, 2, 1});

        Q[5] = new Question(6, "边界耗散律",
                "「界靠耗能维持差异；塌处又凝成更粗糙的新界。」",
                "这道墙塌了，新的墙为什么立刻长出来？",
                new String[]{"因为「塌」本身也在耗能——耗掉的那些，又凝成了下一层。", "塌了就没了，不可能立刻长出来。", "〔沉默〕"},
                new int[]{1, 0, 2});

        Q[6] = new Question(7, "元界不可逾律",
                "「元界不是墙，是能想墙的那盏灯。」",
                "最外面那道墙——你翻得出去吗？",
                new String[]{"能。只要找到最外面，翻过去就出去了。", "翻不出去——「找最外面」这件事，本身就还在这盏灯里。", "〔沉默〕"},
                new int[]{0, 1, 2});

        Q[7] = new Question(8, "叠界共存律",
                "「你不是在边界里，是在边界的堆栈里直立。」",
                "你，是一个人，还是这叠边界暂时不打仗的合谋？",
                new String[]{"〔沉默〕", "我是我。一层就是一层，哪来的一堆。", "是合谋——「我」只是它们停战时的名字。"},
                new int[]{2, 0, 1});

        Q[8] = new Question(9, "归零即重启律",
                "「界本无咎，执者成牢；一笑撒手，新界已生。」",
                "若连「无界」也不执——那时，你是谁？",
                new String[]{"那我就什么都不存在了。", "（笑）——问的人、答的人，都是刚才那道界。", "〔沉默〕"},
                new int[]{1, 1, 2});
    }

    public static Question question(int round) {
        int r = round < 1 ? 1 : (round > ROUNDS ? ROUNDS : round);
        return Q[r - 1];
    }

    /** 中文数字（酒） */
    public static String cn(int n) {
        String[] a = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        return (n >= 0 && n <= 9) ? a[n] : String.valueOf(n);
    }

    // ================================================================ 神的回应（本地预置文案）

    private static final String[] REPLY_COLLAPSE = {
            "错了。你连墙在哪都没看清——那就从你脚下开始。",
            "你用一句硬话，换了整片地面。",
            "否认不是出口，是更快的入口。",
    };

    private static final String[] REPLY_SILENT = {
            "沉默也是回答。而且是最诚实的那个。",
            "你不说，我也听见了。",
            "好。那就让沉默替你说完这句。",
    };

    private static final String[] REPLY_PRECISE = {
            "很好。你把界划得更清楚了——那是你自己的轮廓。",             // 1 自指边界律
            "你在更高的地方，替我砌了一层。",                             // 2 递归升维律
            "名字是你给的。差异不是你给的。",                             // 3 差异先于实体律
            "那一刀，是你裁的。",                                         // 4 观测即造界律
            "你吐出来的影子，正在看你。",                                 // 5 内外互构律
            "塌掉的，都记在账上了。",                                     // 6 边界耗散律
            "灯罩换了，光没换。",                                         // 7 元界不可逾律
            "合谋散场的时候，你会在哪一层？",                             // 8 叠界共存律
            "连这句也不执——才是终律。",                                 // 9 归零即重启律
    };

    public static String localReply(int round, int kind) {
        int i = round - 1;
        if (i < 0) i = 0;
        if (i > ROUNDS - 1) i = ROUNDS - 1;
        if (kind == 0) return REPLY_COLLAPSE[i % REPLY_COLLAPSE.length];
        if (kind == 2) return REPLY_SILENT[i % REPLY_SILENT.length];
        return REPLY_PRECISE[i];
    }

    // ================================================================ 大模型接入点

    /** 神的「回应」来源。未来换大模型 = 换一个实现，别处不动。 */
    public interface AnswerProvider {
        void respond(Question q, int choice, int kind, List<AnswerRecord> history, Callback cb);

        interface Callback {
            /** 可在任意线程回调；UI 层已做线程安全分发 */
            void onResponse(String line);
        }
    }

    /** 当前使用：本地预置文案 */
    public static final class LocalProvider implements AnswerProvider {
        @Override
        public void respond(Question q, int choice, int kind, List<AnswerRecord> history, Callback cb) {
            cb.onResponse(localReply(q.round, kind));
        }
    }

    /**
     * 【预留 · 未启用】未来接入大模型的入口。
     * 接入时要做三件事：
     *   1) 用 buildPrompt() 组装上下文（九大定律 + 本轮律 + 玩家全部历史回答 + 当前选择）；
     *   2) 异步请求大模型，回到主线程调用 cb.onResponse(reply)；
     *   3) 任何异常都兜底回 localReply(q.round, kind)，保证审判流程永不中断。
     * 其余代码（TrialView / MainActivity / 流程）一行都不用改。
     */
    public static final class RemoteProvider implements AnswerProvider {
        @Override
        public void respond(Question q, int choice, int kind, List<AnswerRecord> history, Callback cb) {
            // TODO 大模型接入（当前未启用，暂以本地文案兜底）
            cb.onResponse(localReply(q.round, kind));
        }

        /** 供未来接入时复用：把一整局质询压成一段提示词 */
        public static String buildPrompt(Question q, int choice, List<AnswerRecord> history) {
            StringBuilder sb = new StringBuilder();
            sb.append("你是外神「伊赛德亚」，边界与谎言之庭的审判者。")
                    .append("你从不释放任何人，你的每一句话都在把提问者逼进更深的界里。")
                    .append("语气：冷、短、优雅、带悖论感。每次只说一到两句。\n\n")
                    .append("《理论边界论》九大定律：自指边界律 / 递归升维律 / 差异先于实体律 / 观测即造界律 /")
                    .append("内外互构律 / 边界耗散律 / 元界不可逾律 / 叠界共存律 / 归零即重启律。\n\n");
            sb.append("现在进行第 ").append(q.round).append(" 酒：").append(q.law).append("\n");
            sb.append("题面：").append(q.thesis).append("\n");
            sb.append("你的逼问：").append(q.ask).append("\n\n");
            if (history != null && !history.isEmpty()) {
                sb.append("提问者此前的回答：\n");
                for (int i = 0; i < history.size(); i++) {
                    AnswerRecord a = history.get(i);
                    sb.append("  第 ").append(a.round).append(" 酒：").append(a.text)
                            .append(a.catastrophic ? "（崩塌）" : "").append('\n');
                }
                sb.append('\n');
            }
            sb.append("他这一次选择了：").append(q.options[choice]).append("\n");
            sb.append("请以上帝视角给出你的回应。");
            return sb.toString();
        }
    }

    // ================================================================ 复答记录

    public static final class AnswerRecord {
        public int round;
        public int choice;
        public int kind;          // 0=硬撑 1=精准 2=沉默
        public String text;       // 玩家选项原文（复答时原样钉回来）
        public boolean catastrophic;
        public boolean silent;
    }

    // ================================================================ 一局审判

    public static final class Result {
        public boolean catastrophic;   // 本轮是否触发崩塌
        public int abyss;              // 累计无界深度
        public float boundary;         // 剩余边界完整度
    }

    public static final class Session {

        public final List<AnswerRecord> history = new ArrayList<AnswerRecord>();
        public float boundary = 1f;    // 边界完整度：答得越准越接近无界
        public int abyss = 0;          // 无界深度：= 已走完的酒数
        public int round = 1;          // 当前第几酒
        public boolean collapsed = false;
        public boolean finished = false;

        public Question question() {
            return NineWines.question(round);
        }

        /**
         * 复答目标：第 4、7 酒各钉回一次玩家自己说过的话（Q(n) 生于 A(n-1)）。
         * 返回 0 表示本轮不做复答。
         */
        public int recallTarget() {
            if (round == 4) return 1;
            if (round == 7) return 4;
            return 0;
        }

        /** 取玩家在第 r 酒说过的话；没有则 null */
        public String recall(int r) {
            for (int i = 0; i < history.size(); i++) {
                AnswerRecord a = history.get(i);
                if (a.round == r) return a.text;
            }
            return null;
        }

        /** 玩家作答（固定选项）：返回本轮结果，并推进状态机 */
        public Result answer(int choice) {
            Question q = question();
            int kind = q.kinds[choice];

            AnswerRecord rec = new AnswerRecord();
            rec.round = q.round;
            rec.choice = choice;
            rec.kind = kind;
            rec.text = q.options[choice];
            rec.catastrophic = (kind == 0);
            rec.silent = (kind == 2);
            history.add(rec);

            return advance(q, kind);
        }

        /**
         * ★ 玩家自定义回答：kind 由大模型审判（LlmJudge）给出。
         * 注意：必须先拿到 kind 再调本方法 —— 是否崩塌由祂说了算。
         */
        public Result answerCustom(String text, int kind) {
            Question q = question();

            AnswerRecord rec = new AnswerRecord();
            rec.round = q.round;
            rec.choice = -1;          // -1 = 自定义回答（复答时原样钉回）
            rec.kind = kind;
            rec.text = text;
            rec.catastrophic = (kind == 0);
            rec.silent = (kind == 2);
            history.add(rec);

            return advance(q, kind);
        }

        /** 推进状态机：kind 决定崩塌还是继续（固定选项与自定义回答共用） */
        private Result advance(Question q, int kind) {
            Result r = new Result();
            if (kind == 0) {
                // 铁律一：答错 → 脚下边界崩塌，坠入无界（死得浅：还没被献祭够）
                collapsed = true;
                boundary = 0f;
                abyss = q.round;
                r.catastrophic = true;
            } else {
                // 铁律二：答得越准 → 边界完整度越低（你把界划得更牢了），无界深度 +1
                boundary = Math.max(0.04f, boundary - (kind == 2 ? 0.05f : 0.09f));
                abyss = q.round;
                if (q.round >= ROUNDS) {
                    // 铁律三：九轮走完 ≠ 通关，只是坠入更深的一层
                    finished = true;
                } else {
                    round++;
                }
            }
            r.abyss = abyss;
            r.boundary = boundary;
            return r;
        }
    }
}
