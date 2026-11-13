package cn.aiditto.domain.matching.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.copy.BannedWords;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.MatchingInvariants;
import cn.aiditto.domain.matching.MatchingResult;
import cn.aiditto.domain.matching.MatchingResult.MatchedPair;
import cn.aiditto.domain.matching.MatchingResult.SolverKind;
import cn.aiditto.domain.matching.MatchingSolver;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.Weights;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards {@code aiditto-app/src/main/resources/synthetic/users-v1.json}. Regenerate with
 * {@code mvn -o -pl aiditto-domain test -Dtest=SyntheticFixtureTest -Dsynthetic.write=true}.
 */
class SyntheticFixtureTest {
    static final Path FILE = Path.of("../aiditto-app/src/main/resources/synthetic/users-v1.json");
    static final List<String> REAL_UNIVERSITIES = List.of(
            "北京大学", "北大", "清华", "人民大学", "人大", "北京师范", "北师大", "复旦", "同济", "华东师范", "交通大学", "交大",
            "浙江大学", "南京大学", "武汉大学", "中山大学", "深圳大学", "四川大学", "西安交通", "Peking", "Tsinghua", "Renmin",
            "Fudan", "Tongji", "Jiao Tong");

    static Map<String, Object> doc;
    static List<Map<String, Object>> users;
    static List<Candidate> candidates;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws IOException {
        String generated = Json.write(SyntheticPopulation.document());
        if (Boolean.getBoolean("synthetic.write")) {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, generated, StandardCharsets.UTF_8);
        }
        assertThat(FILE).as("fixture missing; run with -Dsynthetic.write=true").exists();
        String committed = Files.readString(FILE, StandardCharsets.UTF_8);
        if (!committed.equals(generated)) Files.writeString(Path.of("target/users-v1.generated.json"), generated, StandardCharsets.UTF_8);
        assertThat(committed).as("fixture is out of date with the generator; run with -Dsynthetic.write=true").isEqualTo(generated);
        doc = (Map<String, Object>) Json.parse(committed);
        users = SyntheticPopulation.users(doc);
        candidates = FixtureCandidates.all(doc);
    }

    @Test
    void jsonRoundTrips() {
        assertThat(Json.write(doc)).isEqualTo(Json.write(SyntheticPopulation.document()));
    }

    @Test
    void population() {
        assertThat(users).hasSize(200);
        assertThat(count(u -> (String) u.get("kind"))).containsExactlyInAnyOrderEntriesOf(Map.of("partner", 3L, "demo", 4L, "synthetic", 193L));
        assertThat(count(u -> (String) u.get("pool"))).containsExactlyInAnyOrderEntriesOf(Map.of("bj", 130L, "sh", 70L));
        assertThat(count(u -> (String) answers(u).get("gender")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("man", 92L, "woman", 92L, "nonbinary", 12L, "other", 4L));
        long students = users.stream().filter(u -> "yes".equals(answers(u).get("isStudent"))).count();
        assertThat(students).isBetween(150L, 170L);
        assertThat(users.subList(0, 3)).extracting(u -> u.get("handle")).containsExactly("linxia", "zhouyu", "xumu");
        assertThat(users.stream().map(u -> u.get("handle")).collect(Collectors.toSet())).hasSize(200);
        long dealbreakers = users.stream().filter(u -> u.containsKey("aiConstraints")).count();
        assertThat(dealbreakers).isEqualTo(58);
    }

    @Test
    void preferenceMixIsRealistic() {
        long opposite = 0;
        long same = 0;
        long everyone = 0;
        for (Map<String, Object> u : users) {
            Map<String, Object> a = answers(u);
            List<?> wants = (List<?>) a.get("expectedGender");
            if (wants.equals(List.of("everyone"))) everyone++;
            else if (wants.equals(List.of("women")) && "man".equals(a.get("gender")) || wants.equals(List.of("men")) && "woman".equals(a.get("gender"))) opposite++;
            else if (wants.equals(List.of("men")) && "man".equals(a.get("gender")) || wants.equals(List.of("women")) && "woman".equals(a.get("gender"))) same++;
        }
        assertThat(opposite).isBetween(140L, 160L);
        assertThat(same).isBetween(10L, 20L);
        assertThat(everyone).isBetween(25L, 40L);
    }

    @Test
    void peopleAreAdultsAgedEighteenToTwentySix() {
        for (Candidate c : candidates) {
            assertThat(Period.between(c.birthday(), SyntheticPopulation.REFERENCE_WEEK).getYears()).as("user " + c.id()).isBetween(18, 26);
        }
        for (Map<String, Object> u : users) {
            int age = (Integer) card(u).get("age");
            assertThat(Period.between(LocalDate.parse((String) answers(u).get("birthday")), SyntheticPopulation.REFERENCE_WEEK).getYears())
                    .as("card age of " + u.get("handle")).isEqualTo(age);
        }
    }

    @Test
    void everythingIsFictional() {
        Set<String> wechat = new HashSet<>();
        for (Map<String, Object> u : users) {
            String json = Json.write(card(u)) + Json.write(answers(u));
            for (String real : REAL_UNIVERSITIES) assertThat(json).as(u.get("handle") + " mentions " + real).doesNotContain(real);
            assertThat((String) answers(u).get("name")).matches("\\p{IsHan}{2,3}");
            String w = (String) card(u).get("wechat");
            assertThat(w).matches("[a-z0-9]+_demo_\\d{4}");
            assertThat(wechat.add(w)).as("duplicate wechat " + w).isTrue();
            String email = (String) answers(u).getOrDefault("schoolEmail", answers(u).get("personalEmail"));
            assertThat(email).matches("[a-z0-9]+@(demo\\.[a-z]+\\.edu\\.cn|example\\.com)");
            assertThat(u.containsKey("phone")).isEqualTo("demo".equals(u.get("kind")));
        }
    }

    @Test
    void cardCopyIsClean() {
        for (Map<String, Object> u : users) {
            for (String s : strings(card(u))) {
                assertThat(BannedWords.find(s)).as(u.get("handle") + ": " + s).isEmpty();
            }
            assertThat(BannedWords.find((String) answers(u).get("firstMessage"))).isEmpty();
        }
    }

    @Test
    void answersUseFrontendOptionIds() {
        assertThat(candidates).hasSize(200);
        Set<String> places = new HashSet<>();
        for (Map<String, Object> u : users) places.add((String) ((Map<?, ?>) answers(u).get("location")).get("placeId"));
        assertThat(places).isSubsetOf("pku", "tsinghua", "ruc", "bnu", "wudaokou", "sanlitun", "fudan", "tongji", "ecnu", "wukang");
    }

    @Test
    void twoHundredUsersSolveFastWithNoViolations() {
        MatchingInput in = MatchingInput.of(SyntheticPopulation.REFERENCE_WEEK, candidates, 1);
        long t0 = System.nanoTime();
        MatchingResult r = new MatchingSolver().solve(in);
        Duration took = Duration.ofNanos(System.nanoTime() - t0);
        List<String> violations = MatchingInvariants.violations(in, r);
        System.out.printf("[synthetic-200] solved in %d ms (build %d ms, solve %d ms): %d pairs, %d unmatched, %d edges, solver %s, violations %d%n",
                took.toMillis(), r.stats().buildMillis(), r.stats().solveMillis(), r.pairs().size(), r.unmatched().size(), r.stats().edges(),
                r.stats().solver(), violations.size());
        assertThat(violations).isEmpty();
        assertThat(took).isLessThan(Duration.ofSeconds(10));
        assertThat(r.stats().solver()).isEqualTo(SolverKind.BLOSSOM);
        assertThat(r.pairs().size()).as("most of the pool gets a match").isGreaterThanOrEqualTo(60);
    }

    @Test
    void everyDemoAccountGetsAMatchEveryWeek() {
        Map<String, Long> demoIds = new HashMap<>();
        for (int i = 0; i < users.size(); i++) if ("demo".equals(users.get(i).get("kind"))) demoIds.put((String) users.get(i).get("phone"), i + 1L);
        assertThat(demoIds.keySet()).containsExactlyInAnyOrderElementsOf(SyntheticPopulation.DEMO_PHONES);

        for (long seed : new long[] {1, 2, 3, 20261118}) {
            Set<PairKey> history = new HashSet<>();
            for (int week = 0; week < 4; week++) {
                LocalDate weekStart = SyntheticPopulation.REFERENCE_WEEK.plusWeeks(week);
                MatchingInput in = new MatchingInput(weekStart, candidates, history, Set.of(), Weights.W1, seed + week, false);
                MatchingResult r = new MatchingSolver().solve(in);
                assertThat(MatchingInvariants.violations(in, r)).isEmpty();
                Set<Long> matched = new HashSet<>();
                for (MatchedPair p : r.pairs()) {
                    matched.add(p.low());
                    matched.add(p.high());
                    history.add(p.key());
                }
                for (Map.Entry<String, Long> d : demoIds.entrySet()) {
                    assertThat(matched).as("demo " + d.getKey() + " in week " + week + " with seed " + seed).contains(d.getValue());
                }
            }
        }
    }

    @Test
    void fixedPartnersCanBeMatched() {
        MatchingResult r = new MatchingSolver().solve(MatchingInput.of(SyntheticPopulation.REFERENCE_WEEK, candidates, 1));
        List<Long> edgesTouching = new ArrayList<>();
        for (var e : MatchingSolver.buildEdges(MatchingInput.of(SyntheticPopulation.REFERENCE_WEEK, candidates, 1))) {
            if (e.low() <= 3) edgesTouching.add(e.low());
        }
        assertThat(edgesTouching).as("linxia, zhouyu and xumu each have compatible people").contains(1L, 2L, 3L);
        assertThat(r.pairs()).isNotEmpty();
    }

    // ---- helpers

    @SuppressWarnings("unchecked")
    static Map<String, Object> answers(Map<String, Object> u) {
        return (Map<String, Object>) u.get("answers");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> card(Map<String, Object> u) {
        return (Map<String, Object>) u.get("card");
    }

    static Map<String, Long> count(Function<Map<String, Object>, String> key) {
        return users.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
    }

    static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof String s) out.add(s);
        else if (o instanceof Map<?, ?> m) m.values().forEach(v -> out.addAll(strings(v)));
        else if (o instanceof List<?> l) l.forEach(v -> out.addAll(strings(v)));
        return out;
    }
}
