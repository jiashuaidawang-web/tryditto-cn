package cn.aiditto.match;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 裁判 rubric 服务端校验：不可信输出必须返回 null 由上层降级，绝不猜分。 */
class AiJudgeTest {

    private static String verdict(int values, int chemistry, int vibe, int feasible,
                                  String conflicts, String reason, String topics, double conf, int reported) {
        return "{\"total\":" + reported + ",\"dims\":{\"values\":" + values
                + ",\"chemistry\":" + chemistry + ",\"vibe\":" + vibe + ",\"feasible\":" + feasible
                + "},\"conflicts\":" + conflicts + ",\"reason_text\":\"" + reason
                + "\",\"topics\":" + topics + ",\"confidence\":" + conf + "}";
    }

    private static final String TOPICS = "[\"攀岩时最险的一次\",\"周末怎么安排\",\"最近在听的歌\"]";

    @Test
    void parsesValidVerdictAndRecomputesWeightedTotal() {
        AiJudge.Verdict v = AiJudge.parse(verdict(80, 90, 70, 60, "[]", "都爱冒险但节奏互补", TOPICS, 0.9, 78));
        assertThat(v).isNotNull();
        assertThat(v.valid()).isTrue();
        // 0.3*80 + 0.3*90 + 0.2*70 + 0.1*60 = 24+27+14+6 = 71
        assertThat(v.total()).isEqualTo(71);
        assertThat(v.topics()).hasSize(3);
        assertThat(v.degraded()).isFalse();
    }

    @Test
    void hardConflictForcesZeroEvenWhenModelClaimsHighScore() {
        String body = verdict(95, 95, 95, 95, "[\"吸烟\"]", "条件很好但有红线", TOPICS, 0.95, 99);
        // 只有服务端声明过的结构化红线才算硬冲突
        AiJudge.Verdict v = AiJudge.parse(body, java.util.Set.of("吸烟"));
        assertThat(v).isNotNull();
        assertThat(v.total()).isZero();
        assertThat(v.conflicts()).containsExactly("吸烟");
    }

    @Test
    void undeclaredConflictsAreIgnoredToAvoidKillingCrossSurfaceChemistry() {
        // 实测 deepseek 会把"户外冒险 vs 城市潮流"当成 conflict。
        // 这不是红线，必须忽略；否则 Ditto 最核心的跨表面共鸣会被误杀。
        String body = verdict(78, 75, 65, 70,
                "[\"活动场景差异较大：户外冒险 vs 城市潮流文化\"]",
                "共享挑战与新鲜感", TOPICS, 0.8, 72);
        AiJudge.Verdict v = AiJudge.parse(body);
        assertThat(v).isNotNull();
        assertThat(v.conflicts()).isEmpty();
        assertThat(v.total()).isGreaterThan(50);
        assertThat(v.degraded()).isFalse();
    }

    @Test
    void rejectsSelfContradictoryTotal() {
        // 模型自报 100，rubric 复算只有 71，差 >25 → 判不可信
        assertThat(AiJudge.parse(verdict(80, 90, 70, 60, "[]", "还行", TOPICS, 0.9, 100))).isNull();
    }

    @Test
    void rejectsMalformedOrOutOfRangeOutput() {
        assertThat(AiJudge.parse(null)).isNull();
        assertThat(AiJudge.parse("")).isNull();
        assertThat(AiJudge.parse("not-json```")).isNull();
        assertThat(AiJudge.parse("{\"total\":50}")).isNull(); // 缺 dims
        assertThat(AiJudge.parse(verdict(120, 10, 10, 10, "[]", "x", TOPICS, 0.9, 50))).isNull();
        assertThat(AiJudge.parse(verdict(10, 10, 10, 10, "[]", "x", "[\"a\"]", 0.9, 10))).isNull();
        assertThat(AiJudge.parse(verdict(10, 10, 10, 10, "[]",
                "理由".repeat(25), TOPICS, 0.9, 10))).isNull(); // 50 字，确实超过 40
        assertThat(AiJudge.parse(verdict(10, 10, 10, 10, "[]", "x", TOPICS, 1.8, 10))).isNull();
    }

    @Test
    void acceptsJsonFenceButStillValidates() {
        String body = verdict(80, 90, 70, 60, "[]", "节奏互补", TOPICS, 0.9, 71);
        assertThat(AiJudge.parse("```json\n" + body + "\n```")).isNotNull();
    }

    @Test
    void lowConfidenceIsDegradedAndPulledTowardNeutral() {
        AiJudge.Verdict v = AiJudge.parse(verdict(90, 90, 90, 90, "[]", "信息不足", TOPICS, 0.3, 90));
        assertThat(v).isNotNull();
        assertThat(v.degraded()).isTrue();
        assertThat(AiJudge.applyConfidence(v.total(), v.confidence()))
                .isLessThan(v.total()).isGreaterThan(50);
        assertThat(AiJudge.applyConfidence(80, 0.9)).isEqualTo(80);
    }

    @Test
    void transcriptValidationIsStrict() {
        String good = "{\"turns\":[{\"a\":\"你好\",\"b\":\"嗨\"},{\"a\":\"喜欢爬山吗\",\"b\":\"周末常去\"},{\"a\":\"下次一起\",\"b\":\"好啊\"}]}";
        assertThat(AiJudge.validateTranscript(good)).isNull();
        assertThat(AiJudge.validateTranscript(null)).isNotNull();
        assertThat(AiJudge.validateTranscript("{}")).isNotNull();
        assertThat(AiJudge.validateTranscript("{\"turns\":[{\"a\":\"x\",\"b\":\"y\"}]}")).isNotNull();
        assertThat(AiJudge.validateTranscript(good.replace("你好", "这个匹配算法认为我们很合适"))).isNotNull();
        String longLine = "啊".repeat(46);
        assertThat(AiJudge.validateTranscript(good.replace("你好", longLine))).isNotNull();
    }

    @Test
    void reportHasNoPiiAndKeepsEvidence() {
        AiJudge.Verdict v = AiJudge.parse(verdict(80, 90, 70, 60, "[]", "节奏互补", TOPICS, 0.9, 71));
        Map<String, Object> r = AiJudge.report(v, "{\"turns\":[]}");
        assertThat(r).containsKeys("prompt_version", "total", "dims", "conflicts",
                "reason_text", "topics", "confidence", "degraded", "transcript");
        assertThat(r.get("prompt_version")).isEqualTo(AiJudge.PROMPT_VERSION);
    }

    @Test
    void promptCarriesNoRawPiiFromInputs() {
        String user = AiJudge.simulateUser("a@x.com 13800138000", "李四", "北大");
        assertThat(user).doesNotContain("a@x.com").doesNotContain("13800138000");
    }
}
