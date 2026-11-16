package cn.aiditto.match;

import cn.aiditto.ai.LlmCallLogger;
import cn.aiditto.ai.LlmException;
import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.LlmProperties;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.Lang;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.ProfileFacts;
import cn.aiditto.domain.copy.TemplateCopy;
import cn.aiditto.domain.copy.VenueOption;
import cn.aiditto.infra.json.Json;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 7 子图容错闸门：非法输出带问题重试、主备切换、网关全挂、异常不穿透、
 * 开关关闭零调用，以及「重试后通过率 ≥95% / 模板兜底率 ≤5%」的批量统计口径。
 */
class AiCopyServiceTest {

    private static final ProfileFacts LOW = new ProfileFacts(1, "小林", true, "junior",
            Set.of("music", "reading"),
            Map.of("musicSound", Set.of("indie", "rock")),
            Map.of("musicNowPlaying", "晴天", "readingLastBook", "活着"),
            Set.of("serious"), "steady", "20to50", "饭桌上能把我逗笑的人最加分。");

    private static final ProfileFacts HIGH = new ProfileFacts(2, "阿周", true, "junior",
            Set.of("music", "gym"),
            Map.of("musicSound", Set.of("indie")),
            Map.of("gymTrainingFor", "跑完第一个半马"),
            Set.of("serious", "casual"), "steady", "noPref", null);

    private static final List<VenueOption> VENUES = List.of(
            new VenueOption("v1", "树洞咖啡", "一杯饮品，约 45 分钟", "聊得来的话，绕湖走一圈"));

    private static CopyContext ctx() {
        return CopyContext.of(LOW, HIGH, VENUES, Set.of());
    }

    private static MatchCopyProperties props(boolean enabled, int attempts) {
        return new MatchCopyProperties(enabled, attempts, 1600, 0.6, "match-copy-v1");
    }

    private static LlmProperties llmProps(int maxAttempts, boolean fallbackAvailable) {
        // mode 用 mock 避免 LlmConfig 参与；网关只看 fallbackAvailable() 与重试参数
        return new LlmProperties("mock", "", "", "primary", "http://fallback",
                fallbackAvailable ? "key" : "", "fallback", maxAttempts, 1000, 0, 5, "", "");
    }

    private static AiCopyService service(Scripted primary, Scripted fallback,
                                         MatchCopyProperties cp, LlmProperties lp) {
        LlmGateway gw = new LlmGateway(primary, fallback, new LlmCallLogger.InMemory(), lp,
                s -> s != null && !s.isBlank(), () -> 1L, ms -> {});
        return new AiCopyService(gw, cp);
    }

    private static Optional<MatchCopy> generate(AiCopyService s) {
        return s.generate(LOW, HIGH, ctx(), VENUES);
    }

    @Test
    void invalidOutputIsRetriedWithConcreteIssues() {
        String bad = withBannedWord(validJson());
        Scripted primary = new Scripted(bad, validJson());
        AiCopyService s = service(primary, null, props(true, 2), llmProps(1, false));

        Optional<MatchCopy> out = generate(s);

        assertThat(out).isPresent();
        assertThat(out.get().source()).isEqualTo(MatchCopy.Source.AI);
        assertThat(primary.calls()).hasSize(2);
        assertThat(primary.calls().get(1).user()).contains("上次输出被程序判为作废", "BANNED_WORD");
        assertThat(s.stats())
                .containsEntry("copyAttempts", 2)
                .containsEntry("copyInvalidOutputs", 1)
                .containsEntry("copyAccepted", 1)
                .containsEntry("copyExhausted", 0);
    }

    @Test
    void allAttemptsInvalidReturnsEmptyForTemplateFallback() {
        String bad = withBannedWord(validJson());
        Scripted primary = new Scripted(bad, bad);
        AiCopyService s = service(primary, null, props(true, 2), llmProps(1, false));

        assertThat(generate(s)).isEmpty();
        assertThat(primary.calls()).hasSize(2);
        assertThat(s.stats())
                .containsEntry("copyInvalidOutputs", 2)
                .containsEntry("copyExhausted", 1);
    }

    @Test
    void gatewayDegradedDoesNotRetryWildlyAndNeverThrows() {
        Scripted primary = new Scripted(new LlmException(LlmException.Kind.SERVER_ERROR, "5xx"));
        AiCopyService s = service(primary, null, props(true, 3), llmProps(1, false));

        assertThat(generate(s)).isEmpty();
        assertThat(primary.calls()).hasSize(1);
        assertThat(s.stats()).containsEntry("copyDegradedCalls", 1);
    }

    @Test
    void unexpectedRuntimeExceptionIsSwallowed() {
        Scripted primary = new Scripted(new IllegalStateException("boom"));
        AiCopyService s = service(primary, null, props(true, 2), llmProps(1, false));
        assertThat(generate(s)).isEmpty();
        assertThat(s.stats()).containsEntry("copyDegradedCalls", 1);
    }

    @Test
    void authFailureSwitchesToFallbackAndBatchStillCompletes() {
        Scripted primary = new Scripted(new LlmException(LlmException.Kind.AUTH, "401"));
        Scripted fallback = new Scripted(validJson());
        AiCopyService s = service(primary, fallback, props(true, 2), llmProps(1, true));

        Optional<MatchCopy> out = generate(s);

        assertThat(out).isPresent();
        assertThat(fallback.calls()).hasSize(1);
        assertThat(s.stats()).containsEntry("copyAccepted", 1);
    }

    @Test
    void disabledNeverCallsAnyModel() {
        Scripted primary = new Scripted(validJson());
        AiCopyService s = service(primary, null, props(false, 2), llmProps(1, false));
        assertThat(generate(s)).isEmpty();
        assertThat(primary.calls()).isEmpty();
        assertThat(s.stats()).containsEntry("copyAttempts", 0);
    }

    /** 闸门：100 次「首次非法、二次合法」必须 100% 通过（≥95%）。 */
    @Test
    void retryPassRateGate() {
        int passed = 0;
        for (int i = 0; i < 100; i++) {
            AiCopyService s = service(new Scripted(withBannedWord(validJson()), validJson()),
                    null, props(true, 2), llmProps(1, false));
            if (generate(s).isPresent()) passed++;
        }
        assertThat(passed).isEqualTo(100);
    }

    /** 闸门：模型稳定可用时模板兜底率必须为 0（≤5%）。 */
    @Test
    void templateFallbackRateGate() {
        int fallback = 0;
        for (int i = 0; i < 100; i++) {
            AiCopyService s = service(new Scripted(validJson()), null,
                    props(true, 2), llmProps(1, false));
            if (generate(s).isEmpty()) fallback++;
        }
        assertThat(fallback).isZero();
    }

    @Test
    void resetClearsBatchCounters() {
        AiCopyService s = service(new Scripted(validJson()), null, props(true, 2), llmProps(1, false));
        generate(s);
        assertThat(s.stats()).containsEntry("copyAccepted", 1);
        s.reset();
        assertThat(s.stats()).containsEntry("copyAccepted", 0).containsEntry("copyAttempts", 0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    /** 合法样例由 TemplateCopy 反向序列化，保证一定通过 CopyValidator。 */
    private static String validJson() {
        MatchCopy t = TemplateCopy.build(LOW, HIGH, VENUES, Lang.ZH);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("low", viewer(t.forLow()));
        root.put("high", viewer(t.forHigh()));
        root.put("activity", t.activity());
        root.put("extra", t.extra());
        return Json.write(root);
    }

    private static Map<String, Object> viewer(MatchCopy.ViewerCopy v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("headline", v.headline());
        m.put("blurb", v.blurb());
        m.put("openers", v.openers());
        List<Map<String, Object>> rs = new ArrayList<>();
        for (MatchCopy.Reason r : v.reasons()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("text", r.text());
            one.put("basis", r.basis());
            one.put("cites", r.cites());
            rs.add(one);
        }
        m.put("reasons", rs);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static String withBannedWord(String json) {
        Map<String, Object> root = Json.readMap(json);
        ((Map<String, Object>) root.get("low")).put("headline", "{{partner}}颜值很高");
        return Json.write(root);
    }

    /** 按脚本返回内容或抛出故障，记录每次请求用于断言重试提示。 */
    static final class Scripted implements LlmModel {
        private final Deque<Object> script = new ConcurrentLinkedDeque<>();
        private final List<LlmRequest> calls = new ArrayList<>();

        Scripted(Object... items) {
            if (items != null) for (Object o : items) script.add(o);
        }

        @Override
        public String name() {
            return "scripted";
        }

        @Override
        public Raw call(LlmRequest request) throws LlmException {
            calls.add(request);
            Object x = script.poll();
            if (x instanceof LlmException e) throw e;
            if (x instanceof RuntimeException e) throw e;
            return new Raw(x == null ? "" : x.toString(), "stop", 0, 0);
        }

        List<LlmRequest> calls() {
            return List.copyOf(calls);
        }
    }
}
