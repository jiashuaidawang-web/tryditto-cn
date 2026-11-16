package cn.aiditto.match;

import cn.aiditto.ai.LlmCallLogger;
import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmProperties;
import cn.aiditto.ai.OpenAiCompatModel;
import cn.aiditto.domain.copy.BannedWords;
import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.CopyValidator;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.ProfileFacts;
import cn.aiditto.domain.copy.VenueOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 7 真实模型 E2E：验证提示词在真实 LLM 上能产出**通过 CopyValidator 严格校验**的文案，
 * 而不是只在 Mock 下自证。默认跳过；AIDITTO_STEP7_LIVE=1 + 网关环境变量开启。
 * 密钥只从环境变量读，不打印、不落盘。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_STEP7_LIVE", matches = "1")
class AiCopyLiveE2ETest {

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

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

    @Test
    void realModelProducesCopyThatPassesStrictValidation() {
        LlmGateway gateway = new LlmGateway(
                new OpenAiCompatModel(
                        env("LLM_LIVE_BASE_URL", env("LLM_FALLBACK_BASE_URL", "https://api.vveai.com/v1")),
                        env("LLM_E2E_API_KEY", ""),
                        env("LLM_E2E_MODEL", "deepseek-v4-flash"), 30_000L,
                        Map.of()),
                null,
                new LlmCallLogger.InMemory(),
                new LlmProperties("live", "", "", "", "", "", "",
                        2, 30_000L, 0L, 5, "", ""),
                s -> s != null && !s.isBlank(),
                () -> 1L, null);
        AiCopyService service = new AiCopyService(gateway,
                new MatchCopyProperties(true, 3, 1600, 0.6, "match-copy-v1"));
        CopyContext ctx = CopyContext.of(LOW, HIGH, VENUES, Set.of("小王"));

        Optional<MatchCopy> out = service.generate(LOW, HIGH, ctx, VENUES);

        System.out.println("[STEP7-LIVE] accepted=" + out.isPresent() + " stats=" + service.stats());
        assertThat(out).as("真实模型必须在 3 次内产出通过严格校验的文案").isPresent();
        MatchCopy copy = out.get();
        assertThat(copy.source()).isEqualTo(MatchCopy.Source.AI);
        assertThat(copy.venueId()).isEqualTo("v1");
        assertThat(CopyValidator.isValid(copy, ctx)).isTrue();
        for (MatchCopy.ViewerCopy v : List.of(copy.forLow(), copy.forHigh())) {
            for (String t : List.of(v.headline(), copy.activity(), copy.extra())) {
                assertThat(BannedWords.find(t)).isEmpty();
            }
            for (String t : v.blurb()) assertThat(BannedWords.find(t)).isEmpty();
            for (String t : v.openers()) assertThat(BannedWords.find(t)).isEmpty();
            for (MatchCopy.Reason r : v.reasons()) {
                assertThat(BannedWords.find(r.text())).isEmpty();
                assertThat(r.cites()).isNotEmpty();
                assertThat(ctx.sharedFields()).containsAll(r.cites());
            }
        }
        System.out.println("[STEP7-LIVE] headline=" + copy.forLow().headline()
                + " reasons=" + copy.forLow().reasons().size()
                + " activity=" + copy.activity());
    }
}
