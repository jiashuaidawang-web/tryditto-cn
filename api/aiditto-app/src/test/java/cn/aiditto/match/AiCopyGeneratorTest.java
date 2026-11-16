package cn.aiditto.match;

import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.CopyValidator;
import cn.aiditto.domain.copy.Lang;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.ProfileFacts;
import cn.aiditto.domain.copy.TemplateCopy;
import cn.aiditto.domain.copy.VenueOption;
import cn.aiditto.infra.json.Json;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 7 闸门：AI 文案输出的严格校验（禁用词 / 联系方式 / 真实姓名 / 未知占位符 /
 * 编造引用 / 无效 cites / 长度 / 结构），以及提示词的 PII 白名单。
 *
 * 基准 JSON 由 {@link TemplateCopy} 反向序列化得到，保证"合法样例"一定通过
 * CopyValidator，避免手写文案踩到词表口径差异导致假失败。
 */
class AiCopyGeneratorTest {

    private static final ProfileFacts LOW = new ProfileFacts(1, "小林", true, "junior",
            Set.of("music", "reading"),
            Map.of("musicSound", Set.of("indie", "rock")),
            Map.of("musicNowPlaying", "晴天", "readingLastBook", "活着"),
            Set.of("serious"), "steady", "20to50", "饭桌上能把我逗笑的人最加分。");

    private static final ProfileFacts HIGH = new ProfileFacts(2, "阿周", true, "junior",
            Set.of("music", "gym"),
            Map.of("musicSound", Set.of("indie")),
            Map.of("gymTrainingFor", "跑完第一个半马"),
            Set.of("serious", "casual"), "steady", "noPref", "联系我 13800138000 或 a@b.com");

    private static final List<VenueOption> VENUES = List.of(
            new VenueOption("v1", "树洞咖啡", "一杯饮品，约 45 分钟", "聊得来的话，绕湖走一圈"),
            new VenueOption("v2", "荷塘书咖", "一杯咖啡加散步，约 1 小时", ""));

    private static CopyContext ctx() {
        return CopyContext.of(LOW, HIGH, VENUES, Set.of("小王"));
    }

    private static AiCopyGenerator.Result parse(String json) {
        return AiCopyGenerator.parse(json, ctx(), "v1", "一杯饮品，约 45 分钟", "聊得来的话，绕湖走一圈");
    }

    @Test
    void validOutputPassesAndServerOwnsVenue() {
        String json = mutate(m -> m.put("venueId", "evil-venue"));
        AiCopyGenerator.Result r = parse(json);
        assertThat(r.issues()).isEmpty();
        assertThat(r.ok()).isTrue();
        assertThat(r.copy().source()).isEqualTo(MatchCopy.Source.AI);
        // 模型无权决定地点：即使输出了 venueId，落库的仍是程序选定的那个
        assertThat(r.copy().venueId()).isEqualTo("v1");
        assertThat(r.copy().activity()).isEqualTo("一杯饮品，约 45 分钟");
        assertThat(r.copy().forLow().reasons()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void codeFenceIsStripped() {
        AiCopyGenerator.Result r = parse("```json\n" + modelJson() + "\n```");
        assertThat(r.ok()).isTrue();
    }

    @Test
    void malformedJsonAndEmptyOutputRejected() {
        assertThat(parse("not-json```").ok()).isFalse();
        assertThat(parse("").issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.STRUCTURE);
        assertThat(parse("{\"low\":null,\"high\":null}").issues()).isNotEmpty();
    }

    @Test
    void missingViewerRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> m.remove("high")));
        assertThat(r.ok()).isFalse();
        assertThat(r.issues().toString()).contains("high");
    }

    @Test
    void bannedWordRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> low(m).put("headline", "{{partner}}颜值很高，一定合适")));
        assertThat(r.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.BANNED_WORD);
    }

    @Test
    void contactInfoRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> low(m).put("headline", "加我微信聊吧 {{partner}}")));
        assertThat(r.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.CONTACT);
    }

    @Test
    void realNameRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> low(m).put("headline", "小林想见{{partner}}")));
        assertThat(r.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.NAME);
    }

    @Test
    void unknownPlaceholderRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> low(m).put("headline", "去见{{name}}吧")));
        assertThat(r.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.PLACEHOLDER);
    }

    @Test
    void inventedQuoteRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> low(m).put("headline", "你们都爱「量子物理」")));
        assertThat(r.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.UNKNOWN_QUOTE);
    }

    @Test
    void invalidCitesRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> {
            Map<String, Object> first = reasons(m).get(0);
            first.put("cites", List.of("notAField"));
        }));
        assertThat(r.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.CITES);
    }

    @Test
    void tooLongHeadlineRejected() {
        AiCopyGenerator.Result r = parse(mutate(m -> low(m).put("headline", "很".repeat(40))));
        assertThat(r.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.LENGTH);
    }

    @Test
    void wrongCountsRejected() {
        AiCopyGenerator.Result fewReasons = parse(mutate(m -> {
            List<Map<String, Object>> rs = reasons(m);
            m.put("low", lowWithReasons(rs.subList(0, 1)));
        }));
        assertThat(fewReasons.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.STRUCTURE);

        AiCopyGenerator.Result oneBlurb = parse(mutate(m -> low(m).put("blurb", List.of("只有一句"))));
        assertThat(oneBlurb.issues()).extracting(i -> i.rule())
                .contains(CopyValidator.Rule.STRUCTURE);

        AiCopyGenerator.Result oneOpener = parse(mutate(m -> low(m).put("openers", List.of("只有一句"))));
        assertThat(oneOpener.issues()).isNotEmpty();
    }

    @Test
    void blankActivityFallsBackToVenueDefaults() {
        AiCopyGenerator.Result r = parse(mutate(m -> {
            m.put("activity", "");
            m.put("extra", "");
        }));
        assertThat(r.ok()).isTrue();
        assertThat(r.copy().activity()).isEqualTo("一杯饮品，约 45 分钟");
        assertThat(r.copy().extra()).isEqualTo("聊得来的话，绕湖走一圈");
    }

    @Test
    void promptNeverCarriesPiiOrSensitiveFacts() {
        String prompt = AiCopyGenerator.system() + "\n"
                + AiCopyGenerator.user(LOW, HIGH, ctx(), VENUES);
        // 昵称只用于服务端检测泄漏，绝不进提示词
        assertThat(prompt).doesNotContain("小林", "阿周", "小王");
        // 自由文本里的手机号 / 邮箱在最后一道脱敏闸被打码
        assertThat(prompt).doesNotContain("13800138000", "a@b.com", "sk-");
        // 性别 / 身高 / 生日 / 照片 / dealbreaker 原文等字段从未出现在白名单
        assertThat(prompt).doesNotContain("gender", "height", "birthday", "photo", "dealbreaker");
        assertThat(prompt).contains("共同项清单", "可用词表", "A 的白名单资料", "B 的白名单资料");
        assertThat(prompt).contains("[手机号]", "[邮箱]");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private static String modelJson() {
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

    private static String mutate(Consumer<Map<String, Object>> fn) {
        Map<String, Object> m = Json.readMap(modelJson());
        fn.accept(m);
        return Json.write(m);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> low(Map<String, Object> root) {
        return (Map<String, Object>) root.get("low");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> reasons(Map<String, Object> root) {
        return (List<Map<String, Object>>) low(root).get("reasons");
    }

    private static Map<String, Object> lowWithReasons(List<Map<String, Object>> rs) {
        Map<String, Object> v = new LinkedHashMap<>(low(Json.readMap(modelJson())));
        v.put("reasons", rs);
        return v;
    }
}
