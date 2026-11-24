package cn.aiditto.match;

import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.CopyValidator;
import cn.aiditto.domain.copy.Lang;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.ProfileFacts;
import cn.aiditto.domain.copy.ResonanceHint;
import cn.aiditto.domain.copy.TemplateCopy;
import cn.aiditto.domain.copy.VenueOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 深层理由闸门：受控共鸣信号（ResonanceHint）从裁判维度提炼、低可信不出标签、
 * 且 resonance 作为合法 cites 能通过 CopyValidator——让「为什么是你们俩」能写跨表面理由。
 */
class ResonanceReasonTest {

    private static final ProfileFacts LOW = new ProfileFacts(1, "小林", true, "junior",
            Set.of("music"), Map.of(), Map.of(), Set.of("serious"), "steady", "20to50", "逗我笑就行");
    private static final ProfileFacts HIGH = new ProfileFacts(2, "阿周", true, "junior",
            Set.of("music"), Map.of(), Map.of(), Set.of("serious"), "steady", "20to50", "热爱音乐");
    private static final List<VenueOption> VENUES = List.of(new VenueOption("v1", "树洞咖啡", "一杯饮品", "散步"));

    @Test
    void highConfidenceDimsYieldControlledTraits() {
        ResonanceHint h = ResonanceHint.from(Map.of(
                "values", 80, "chemistry", 90, "vibe", 78, "feasible", 70), 0.85, false);
        assertThat(h.present()).isTrue();
        assertThat(h.traits()).containsExactly("聊得来", "节奏合拍", "想法相近");
        // 只有固定安全词，绝不含自由文本
        assertThat(h.traits()).allSatisfy(t -> assertThat(t).isIn("聊得来", "节奏合拍", "想法相近"));
    }

    @Test
    void lowConfidenceOrDegradedYieldsNothing() {
        assertThat(ResonanceHint.from(Map.of("chemistry", 90), 0.3, false).present()).isFalse();
        assertThat(ResonanceHint.from(Map.of("chemistry", 90), 0.9, true).present()).isFalse();
        assertThat(ResonanceHint.from(Map.of("chemistry", 50), 0.9, false).traits()).isEmpty();
    }

    /** 用合法模板基线，把共鸣理由追加到 low 的 reasons（保留基线理由满足 2–3 条约束）。 */
    private static MatchCopy withLowReason(MatchCopy.Reason reason) {
        MatchCopy base = TemplateCopy.build(LOW, HIGH, VENUES, Lang.ZH);
        List<MatchCopy.Reason> reasons = new java.util.ArrayList<>(
                base.forLow().reasons().subList(0, Math.min(2, base.forLow().reasons().size())));
        reasons.add(reason);
        MatchCopy.ViewerCopy lowVc = new MatchCopy.ViewerCopy(base.forLow().headline(),
                base.forLow().blurb(), reasons, base.forLow().openers());
        return new MatchCopy(lowVc, base.forHigh(), base.venueId(), base.activity(), base.extra(),
                MatchCopy.Source.AI);
    }

    @Test
    void resonanceIsAValidSharedCiteWhenPresent() {
        ResonanceHint h = ResonanceHint.from(Map.of("chemistry", 88), 0.9, false);
        CopyContext ctx = CopyContext.of(LOW, HIGH, VENUES, Set.of(), h);
        assertThat(ctx.sharedFields()).contains(ResonanceHint.CITE);
        assertThat(ctx.knownTerms()).contains("聊得来");

        MatchCopy copy = withLowReason(new MatchCopy.Reason(
                "你们的聊天节奏意外地合拍", "来自分身对话的共鸣观察", List.of("resonance")));
        assertThat(CopyValidator.validate(copy, ctx)).isEmpty();
    }

    @Test
    void resonanceCiteRejectedWhenNoSignal() {
        // 没有共鸣信号时，resonance 不是合法字段——防止文案凭空编造"共鸣"
        CopyContext ctx = CopyContext.of(LOW, HIGH, VENUES, Set.of(), ResonanceHint.none());
        assertThat(ctx.sharedFields()).doesNotContain(ResonanceHint.CITE);
        MatchCopy copy = withLowReason(new MatchCopy.Reason(
                "你们很合拍", "无依据", List.of("resonance")));
        assertThat(CopyValidator.validate(copy, ctx)).isNotEmpty();
    }
}
