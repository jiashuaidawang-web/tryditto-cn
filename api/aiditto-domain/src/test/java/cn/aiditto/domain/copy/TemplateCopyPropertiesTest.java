package cn.aiditto.domain.copy;

import static cn.aiditto.domain.copy.CopyFixtures.VENUES;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/** Template copy validates for any facts, including hostile free text. */
class TemplateCopyPropertiesTest {

    @Property(tries = 300)
    void randomFactsAlwaysValidate(@ForAll long seed) {
        Random r = new Random(seed);
        ProfileFacts a = randomFacts(r, 1);
        ProfileFacts b = randomFacts(r, 2);
        for (Lang lang : Lang.values()) {
            MatchCopy c = TemplateCopy.build(a, b, VENUES, lang);
            assertThat(CopyValidator.validate(c, CopyContext.of(a, b, VENUES, Set.of()))).isEmpty();
        }
    }

    private static final String[] TEXTS = {"晴天", "活着", "大理", "番茄牛腩", "加我微信 abc", "13812345678", "身高 180", "《三体》",
            "{{name}}", "灵魂伴侣", "https://x.cn", "一个特别特别特别特别特别特别特别特别特别特别长的回答一个特别特别特别特别特别特别长的回答", "小林"};

    static ProfileFacts randomFacts(Random r, long id) {
        Set<String> hobbies = new HashSet<>();
        for (String h : Labels.HOBBY_ORDER) if (r.nextInt(3) == 0) hobbies.add(h);
        if (r.nextInt(5) == 0) hobbies.add("custom:" + TEXTS[r.nextInt(TEXTS.length)]);
        Map<String, Set<String>> options = new HashMap<>();
        Map<String, String> texts = new HashMap<>();
        for (String h : hobbies) {
            List<String> branch = Labels.BRANCHES.get(h);
            if (branch == null) continue;
            List<String> opts = new ArrayList<>(List.of("indie", "pop", "asian", "city", "litfic", "running", "rpg", "anime", "drawing"));
            Set<String> pick = new HashSet<>();
            for (String o : opts) if (r.nextInt(4) == 0) pick.add(o);
            options.put(branch.get(0), pick);
            if (r.nextBoolean()) texts.put(branch.get(1), TEXTS[r.nextInt(TEXTS.length)]);
        }
        String[] years = {"freshman", "junior", "phd", "other", null};
        String[] looking = {"serious", "casual", "friends", "notSure", "lifePartner"};
        String[] styles = {"faster", "steady", "fewer", "theOne", null};
        String[] budgets = {"under20", "20to50", "noPref", null};
        return new ProfileFacts(id, id == 1 ? "小林" : "阿周", r.nextBoolean(), years[r.nextInt(years.length)], hobbies, options, texts,
                Set.of(looking[r.nextInt(looking.length)]), styles[r.nextInt(styles.length)], budgets[r.nextInt(budgets.length)],
                r.nextBoolean() ? TEXTS[r.nextInt(TEXTS.length)] : null);
    }
}
