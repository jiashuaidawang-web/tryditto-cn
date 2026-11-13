package cn.aiditto.domain.copy;

import static cn.aiditto.domain.copy.CopyFixtures.HIGH;
import static cn.aiditto.domain.copy.CopyFixtures.LOW;
import static cn.aiditto.domain.copy.CopyFixtures.VENUES;
import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.MatchingResult;
import cn.aiditto.domain.matching.MatchingSolver;
import cn.aiditto.domain.matching.synthetic.FixtureCandidates;
import cn.aiditto.domain.matching.synthetic.SyntheticPopulation;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TemplateCopyTest {

    @ParameterizedTest
    @EnumSource(Lang.class)
    void samplePairValidates(Lang lang) {
        MatchCopy c = TemplateCopy.build(LOW, HIGH, VENUES, lang);
        assertThat(CopyValidator.validate(c, CopyContext.of(LOW, HIGH, VENUES, Set.of()))).isEmpty();
        assertThat(c.source()).isEqualTo(MatchCopy.Source.TEMPLATE);
        assertThat(c.venueId()).isEqualTo("v1");
        assertThat(c.forLow().reasons()).hasSizeBetween(2, 3);
        assertThat(c.forLow().reasons().get(0).cites()).containsExactly("hobbies", "musicSound");
    }

    @Test
    void zhSampleReadsNaturally() {
        MatchCopy c = TemplateCopy.build(LOW, HIGH, VENUES, Lang.ZH);
        assertThat(c.forLow().reasons().get(0).text()).isEqualTo("你们都选了「音乐」，而且都喜欢「独立」。");
        assertThat(c.forLow().openers().get(0)).isEqualTo("问问 ta「跑完第一个半马」这个目标练到哪一步了。");
        assertThat(c.forHigh().openers()).containsExactly("问问 ta 为什么最近一直在循环「晴天」。", "问问 ta 读完「活着」最大的感受。");
        assertThat(c.forLow().blurb().get(0)).isEqualTo("ta 是大三学生，平时喜欢音乐、健身。");
        assertThat(c.forLow().reasons()).extracting(MatchCopy.Reason::text)
                .containsExactly("你们都选了「音乐」，而且都喜欢「独立」。", "你们都在找「认真谈恋爱」。", "你们想要的节奏一样：「稳稳的节奏」。");
        assertThat(c.activity()).isEqualTo("一杯饮品，约 45 分钟");
    }

    @Test
    void nothingInCommonStillGivesTwoReasons() {
        ProfileFacts a = new ProfileFacts(1, "甲", true, "freshman", Set.of("art"), Map.of(), Map.of(), Set.of("friends"), "faster", "under20", null);
        ProfileFacts b = new ProfileFacts(2, "乙", false, null, Set.of("gym"), Map.of(), Map.of(), Set.of("serious"), "theOne", "100plus", null);
        for (Lang lang : Lang.values()) {
            MatchCopy c = TemplateCopy.build(a, b, VENUES, lang);
            assertThat(CopyValidator.validate(c, CopyContext.of(a, b, VENUES, Set.of()))).isEmpty();
            assertThat(c.forLow().reasons()).hasSize(2).allSatisfy(r -> assertThat(r.cites()).containsExactly("location"));
        }
    }

    @Test
    void hostileFreeTextIsNeverQuoted() {
        ProfileFacts nasty = new ProfileFacts(2, "阿坏", true, "junior", Set.of("music", "reading", "custom:加微信abc"),
                Map.of(), Map.of("musicNowPlaying", "加我微信 abc123", "readingLastBook", "身材管理指南"),
                Set.of("serious"), "steady", "20to50", "13812345678");
        for (Lang lang : Lang.values()) {
            MatchCopy c = TemplateCopy.build(LOW, nasty, VENUES, lang);
            assertThat(CopyValidator.validate(c, CopyContext.of(LOW, nasty, VENUES, Set.of()))).isEmpty();
            String all = String.join("|", c.forLow().openers()) + String.join("|", c.forLow().blurb());
            assertThat(all).doesNotContain("微信").doesNotContain("身材");
        }
    }

    @Test
    void everyFixturePairGetsValidCopy() {
        Map<String, Object> doc = SyntheticPopulation.document();
        List<Map<String, Object>> users = SyntheticPopulation.users(doc);
        List<Candidate> cs = FixtureCandidates.all(doc);
        MatchingResult r = new MatchingSolver().solve(MatchingInput.of(SyntheticPopulation.REFERENCE_WEEK, cs, 1));
        Set<String> names = new HashSet<>();
        for (Map<String, Object> u : users) names.add((String) ((Map<?, ?>) u.get("answers")).get("name"));
        int checked = 0;
        for (var p : r.pairs()) {
            ProfileFacts low = CopyFixtures.facts(users.get((int) p.low() - 1), p.low());
            ProfileFacts high = CopyFixtures.facts(users.get((int) p.high() - 1), p.high());
            CopyContext ctx = CopyContext.of(low, high, VENUES, names);
            for (Lang lang : Lang.values()) {
                MatchCopy c = TemplateCopy.build(low, high, VENUES, lang);
                assertThat(CopyValidator.validate(c, ctx)).as(p.key() + " " + lang).isEmpty();
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(100);
    }
}
