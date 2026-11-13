package cn.aiditto.domain.copy;

import static cn.aiditto.domain.copy.CopyFixtures.blurb;
import static cn.aiditto.domain.copy.CopyFixtures.ctx;
import static cn.aiditto.domain.copy.CopyFixtures.headline;
import static cn.aiditto.domain.copy.CopyFixtures.openers;
import static cn.aiditto.domain.copy.CopyFixtures.reasons;
import static cn.aiditto.domain.copy.CopyFixtures.valid;
import static cn.aiditto.domain.copy.CopyFixtures.withLow;
import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.copy.CopyValidator.Issue;
import cn.aiditto.domain.copy.CopyValidator.Rule;
import cn.aiditto.domain.copy.MatchCopy.Reason;
import cn.aiditto.domain.copy.MatchCopy.ViewerCopy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CopyValidatorTest {

    static List<Rule> rules(MatchCopy c) {
        return CopyValidator.validate(c, ctx()).stream().map(Issue::rule).distinct().toList();
    }

    static List<Rule> lowText(String text) {
        ViewerCopy low = valid().forLow();
        return rules(withLow(valid(), openers(low, List.of(text, low.openers().get(1)))));
    }

    @Test
    void wellBehavedCopyPasses() {
        assertThat(CopyValidator.validate(valid(), ctx())).isEmpty();
        assertThat(CopyValidator.isValid(valid(), ctx())).isTrue();
    }

    @Test
    void structure() {
        ViewerCopy low = valid().forLow();
        assertThat(rules(null)).containsExactly(Rule.STRUCTURE);
        assertThat(rules(withLow(valid(), null))).containsExactly(Rule.STRUCTURE);
        assertThat(rules(withLow(valid(), headline(low, " ")))).containsExactly(Rule.STRUCTURE);
        assertThat(rules(withLow(valid(), reasons(low, low.reasons().subList(0, 1))))).containsExactly(Rule.STRUCTURE);
        assertThat(rules(withLow(valid(), reasons(low, null)))).containsExactly(Rule.STRUCTURE);
        assertThat(rules(withLow(valid(), openers(low, List.of("问问 ta 周末一般怎么过。"))))).containsExactly(Rule.STRUCTURE);
        assertThat(rules(withLow(valid(), blurb(low, List.of("a", "b", "c"))))).containsExactly(Rule.STRUCTURE);
        assertThat(rules(withLow(valid(), reasons(low, List.of(low.reasons().get(0), new Reason("", "x", List.of("hobbies")))))))
                .containsExactly(Rule.STRUCTURE);
    }

    @Test
    void lengthCountsPlaceholdersAsThreeCharacters() {
        ViewerCopy low = valid().forLow();
        String thirty = "{{partner}}" + "一".repeat(27);
        assertThat(rules(withLow(valid(), headline(low, thirty)))).isEmpty();
        assertThat(rules(withLow(valid(), headline(low, thirty + "二")))).containsExactly(Rule.LENGTH);
        assertThat(rules(withLow(valid(), headline(low, "😀".repeat(30))))).as("code points, not UTF-16 units").isEmpty();
        assertThat(lowText("问".repeat(61))).containsExactly(Rule.LENGTH);
    }

    @Test
    void citesMustBeFilledByBothAndIncludeSomethingShared() {
        ViewerCopy low = valid().forLow();
        Reason ok = low.reasons().get(0);
        for (List<String> cites : List.<List<String>>of(List.of("gymTrainingFor"), List.of("firstMessage"), List.of("firstDateBudget"), List.of(),
                List.of("height"), List.of("isStudent", "readingLastBook"))) {
            Reason bad = new Reason("你们都选了「音乐」。", "你们都选了「音乐」", cites);
            assertThat(rules(withLow(valid(), reasons(low, List.of(ok, bad))))).as(cites.toString()).containsExactly(Rule.CITES);
        }
        Reason sharedPlusOwn = new Reason("你们都选了「音乐」。", "你们都选了「音乐」", List.of("hobbies", "matchingStyle"));
        assertThat(rules(withLow(valid(), reasons(low, List.of(ok, sharedPlusOwn))))).isEmpty();
        Reason nullCites = new Reason("你们都选了「音乐」。", "你们都选了「音乐」", null);
        assertThat(rules(withLow(valid(), reasons(low, List.of(ok, nullCites))))).containsExactly(Rule.CITES);
    }

    @Test
    void venueMustBeACandidate() {
        MatchCopy c = valid();
        assertThat(rules(new MatchCopy(c.forLow(), c.forHigh(), "v2", c.activity(), c.extra(), c.source()))).isEmpty();
        assertThat(rules(new MatchCopy(c.forLow(), c.forHigh(), "v9", c.activity(), c.extra(), c.source()))).containsExactly(Rule.VENUE);
        assertThat(rules(new MatchCopy(c.forLow(), c.forHigh(), null, c.activity(), c.extra(), c.source()))).containsExactly(Rule.VENUE);
        assertThat(rules(new MatchCopy(c.forLow(), c.forHigh(), "v1", "加我微信聊", c.extra(), c.source()))).containsExactly(Rule.CONTACT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"你们俩颜值都很高。", "问问 ta 的身高。", "你们是灵魂伴侣。", "契合度很高。", "你们 95% 合拍。", "命中注定的相遇。",
            "聊聊宗教。", "聊聊政治。", "先借钱给 ta。", "Ask about their soulmate.", "You both look HOT.", "You're meant to be."})
    void bannedWords(String text) {
        assertThat(lowText(text)).contains(Rule.BANNED_WORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"加我微信 abc123 吧", "我的手机号 13812345678", "电话 138 1234 5678", "QQ：12345678", "vx 一下",
            "wxid_abc", "看 www.example.cn", "去 https://x.y", "发到 a.b@example.org", "关注 abc.top 吧", "WeChat me"})
    void contactInfo(String text) {
        assertThat(lowText(text)).contains(Rule.CONTACT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"问问 ta 周末一般怎么过。", "一起去吃火锅吧。", "问问 ta 最近 3 本书的感受。", "约在 18:30 见。"})
    void ordinaryLinesPass(String text) {
        assertThat(lowText(text)).isEmpty();
    }

    @Test
    void namesAndPlaceholders() {
        assertThat(lowText("问问阿周周末怎么过。")).containsExactly(Rule.NAME);
        assertThat(lowText("问问小王周末怎么过。")).as("names from the rest of the batch").containsExactly(Rule.NAME);
        assertThat(lowText("问问 {{name}} 周末怎么过。")).containsExactly(Rule.PLACEHOLDER);
        assertThat(lowText("问问 {{partner}} 和 {{me}} 周末怎么过。")).isEmpty();
    }

    @Test
    void quotedTermsMustComeFromTheInput() {
        assertThat(lowText("问问 ta 读完《百年孤独》的感受。")).containsExactly(Rule.UNKNOWN_QUOTE);
        assertThat(lowText("问问 ta 去过“大理”没有。")).containsExactly(Rule.UNKNOWN_QUOTE);
        assertThat(lowText("问问 ta 读完《活着》的感受。")).isEmpty();
        assertThat(lowText("你们都喜欢「独立」。")).as("option label").isEmpty();
        assertThat(lowText("去「树洞咖啡」坐坐。")).as("venue name").isEmpty();
        assertThat(lowText("Ask about “Indie” music.")).as("English option label").isEmpty();
    }

    @Test
    void extractJson() {
        assertThat(CopyValidator.extractJson("```json\n{\"a\": 1}\n```")).isEqualTo("{\"a\": 1}");
        assertThat(CopyValidator.extractJson("好的，结果如下：\n```\n{\"a\": {\"b\": 2}}\n```\n希望有帮助")).isEqualTo("{\"a\": {\"b\": 2}}");
        assertThat(CopyValidator.extractJson("Sure! {\"a\": 1} Hope this helps.")).isEqualTo("{\"a\": 1}");
        assertThat(CopyValidator.extractJson("  no json here ")).isEqualTo("no json here");
        assertThat(CopyValidator.extractJson(null)).isEmpty();
    }

    @Test
    void summaryIsOneLine() {
        List<Issue> issues = CopyValidator.validate(new MatchCopy(valid().forLow(), valid().forHigh(), "v9", null, null, MatchCopy.Source.AI), ctx());
        assertThat(CopyValidator.summary(issues)).startsWith("venueId: ").doesNotContain("\n");
    }
}
