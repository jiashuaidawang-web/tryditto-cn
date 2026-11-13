package cn.aiditto.domain.matching;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Adversarial random pools for property tests: every field the hard filter reads is varied, including
 * under-18s, unknown school years, open matches, block lists, past pairs and reports. Same seed → same input.
 */
public final class RandomPopulation {
    public static final LocalDate WEEK = LocalDate.of(2026, 11, 18);

    private static final String[] HOBBIES = {"cooking", "music", "travel", "reading", "gym", "gaming", "movies", "art"};
    private static final Map<String, String[]> BRANCH = new TreeMap<>(Map.of(
            "musicSound", new String[] {"pop", "indie", "rock", "jazz"},
            "moviesContent", new String[] {"indie", "scifi", "anime", "romcom"},
            "travelStyle", new String[] {"city", "foodie", "solo"}));

    private RandomPopulation() {}

    public static MatchingInput input(long seed, int size) {
        Random r = new Random(seed);
        List<Candidate> people = new ArrayList<>();
        for (int i = 0; i < size; i++) people.add(candidate(r, 1000L + 7L * i, size));
        Set<PairKey> past = new HashSet<>();
        Set<PairKey> reported = new HashSet<>();
        for (int k = 0; k < size; k++) {
            long a = people.get(r.nextInt(size)).id();
            long b = people.get(r.nextInt(size)).id();
            if (a == b) continue;
            (r.nextInt(5) == 0 ? reported : past).add(PairKey.of(a, b));
        }
        return new MatchingInput(WEEK, people, past, reported, Weights.W1, r.nextLong(), r.nextInt(4) == 0);
    }

    static Candidate candidate(Random r, long id, int size) {
        String pool = r.nextInt(5) == 0 ? "sh" : "bj";
        List<TestPlaces.Place> places = TestPlaces.inPool(pool);
        TestPlaces.Place place = places.get(r.nextInt(places.size()));
        TravelMode mode = TravelMode.values()[r.nextInt(TravelMode.values().length)];
        int minutes = new int[] {10, 20, 30, 45, 60, 90}[r.nextInt(6)];

        Gender gender = pick(r, Gender.values(), 46, 46, 6, 2);
        Set<GenderPref> wants = new HashSet<>();
        if (r.nextInt(6) == 0) wants.add(GenderPref.EVERYONE);
        else for (GenderPref g : GenderPref.values()) if (g != GenderPref.EVERYONE && r.nextInt(3) == 0) wants.add(g);
        if (wants.isEmpty()) wants.add(gender == Gender.MAN ? GenderPref.WOMEN : GenderPref.MEN);

        int age = 16 + r.nextInt(13);
        LocalDate birthday = WEEK.minusYears(age).minusDays(r.nextInt(365));
        int ageMin = 18 + r.nextInt(6);
        int ageMax = ageMin + r.nextInt(9);

        boolean student = r.nextInt(4) != 0;
        SchoolYear year = !student ? null : r.nextInt(10) == 0 ? null : SchoolYear.values()[r.nextInt(SchoolYear.values().length)];
        EducationLevel edu = r.nextInt(8) == 0 ? null : EducationLevel.values()[r.nextInt(EducationLevel.values().length)];
        Importance imp = Importance.values()[r.nextInt(Importance.values().length)];

        int hMin = 150 + r.nextInt(30);
        HeightPref hp = r.nextInt(3) != 0 ? HeightPref.ANY : HeightPref.between(hMin, hMin + r.nextInt(35));

        Set<String> hobbies = new HashSet<>();
        for (String h : HOBBIES) if (r.nextInt(3) == 0) hobbies.add(h);
        if (r.nextInt(10) == 0) hobbies.add("custom:桌游");
        Map<String, Set<String>> details = new HashMap<>();
        for (Map.Entry<String, String[]> e : BRANCH.entrySet()) {
            if (r.nextBoolean()) continue;
            Set<String> opts = new HashSet<>();
            for (String o : e.getValue()) if (r.nextBoolean()) opts.add(o);
            details.put(e.getKey(), opts);
        }

        Set<Looking> looking = new HashSet<>();
        for (Looking l : Looking.values()) if (r.nextInt(3) == 0) looking.add(l);
        if (looking.isEmpty()) looking.add(Looking.values()[r.nextInt(Looking.values().length)]);

        Set<String> blocked = new HashSet<>();
        if (r.nextInt(10) == 0) blocked.add("h" + (1000L + 7L * r.nextInt(Math.max(1, size))));

        AiConstraints ai = r.nextInt(6) != 0 ? AiConstraints.NONE : new AiConstraints(
                r.nextBoolean() ? 160 + r.nextInt(25) : null,
                r.nextInt(4) == 0 ? 175 + r.nextInt(20) : null,
                r.nextInt(4) == 0 ? 18 + r.nextInt(5) : null,
                r.nextInt(4) == 0 ? 22 + r.nextInt(6) : null,
                r.nextInt(8) == 0, r.nextInt(12) == 0);

        Candidate.Builder b = Candidate.builder(id)
                .pool(pool)
                .gender(gender)
                .wants(wants)
                .birthday(birthday)
                .ageRange(ageMin, ageMax)
                .heightCm(150 + r.nextInt(46))
                .heightPref(hp)
                .student(student)
                .education(edu, imp)
                .budget(Budget.values()[r.nextInt(Budget.values().length)])
                .style(MatchingStyle.values()[r.nextInt(MatchingStyle.values().length)])
                .looking(looking)
                .hobbies(hobbies)
                .hobbyDetails(details)
                .location(place.at(mode, minutes))
                .emptyWeekStreak(r.nextInt(5))
                .contactHashes(Set.of("h" + id))
                .blockedHashes(blocked)
                .ai(ai)
                .hasOpenMatch(r.nextInt(20) == 0);
        if (student) b.schoolYear(year);
        return b.build();
    }

    private static <T> T pick(Random r, T[] values, int... weights) {
        int total = 0;
        for (int w : weights) total += w;
        int x = r.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            x -= weights[i];
            if (x < 0) return values[i];
        }
        return values[values.length - 1];
    }
}
