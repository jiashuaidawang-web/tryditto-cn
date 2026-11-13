package cn.aiditto.domain.matching.synthetic;

import cn.aiditto.domain.matching.AiConstraints;
import cn.aiditto.domain.matching.Budget;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.EducationLevel;
import cn.aiditto.domain.matching.Gender;
import cn.aiditto.domain.matching.GenderPref;
import cn.aiditto.domain.matching.HeightPref;
import cn.aiditto.domain.matching.Importance;
import cn.aiditto.domain.matching.Looking;
import cn.aiditto.domain.matching.MatchingStyle;
import cn.aiditto.domain.matching.SchoolYear;
import cn.aiditto.domain.matching.TestPlaces;
import cn.aiditto.domain.matching.TravelMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Fixture user → {@link Candidate}, reading the answers the same way the app's profile projection does. */
public final class FixtureCandidates {
    private FixtureCandidates() {}

    /** Ids are 1-based fixture positions. */
    public static List<Candidate> all(Map<String, Object> doc) {
        List<Candidate> out = new ArrayList<>();
        List<Map<String, Object>> users = SyntheticPopulation.users(doc);
        for (int i = 0; i < users.size(); i++) out.add(of(users.get(i), i + 1));
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Candidate of(Map<String, Object> user, long id) {
        Map<String, Object> a = (Map<String, Object>) user.get("answers");
        Map<String, Object> height = (Map<String, Object>) a.get("height");
        Map<String, Object> range = (Map<String, Object>) a.get("heightRange");
        Map<String, Object> loc = (Map<String, Object>) a.get("location");
        List<Integer> ages = (List<Integer>) a.get("expectedAgeRange");
        boolean student = "yes".equals(a.get("isStudent"));

        Set<GenderPref> wants = new HashSet<>();
        for (String w : (List<String>) a.get("expectedGender")) wants.add(GenderPref.fromId(w));
        Set<Looking> looking = new HashSet<>();
        for (String l : (List<String>) a.get("relationshipLooking")) looking.add(Looking.fromId(l));
        List<String> hobbies = (List<String>) a.get("hobbies");
        Map<String, Set<String>> details = new TreeMap<>();
        for (String h : hobbies) {
            String q = SyntheticPopulation.MULTI_BRANCH.get(h);
            if (q != null && a.get(q) instanceof List<?> opts) details.put(q, new LinkedHashSet<>((List<String>) opts));
        }

        Candidate.Builder b = Candidate.builder(id)
                .pool((String) user.get("pool"))
                .gender(Gender.fromId((String) a.get("gender")))
                .wants(wants)
                .birthday(LocalDate.parse((String) a.get("birthday")))
                .ageRange(ages.get(0), ages.get(1))
                .heightCm((Integer) height.get("cm"))
                .heightPref(Boolean.TRUE.equals(range.get("any")) ? HeightPref.ANY
                        : HeightPref.between((Integer) range.get("min"), (Integer) range.get("max")))
                .student(student)
                .education(EducationLevel.fromId((String) a.get("educationLevel")), Importance.fromId((String) a.get("educationImportance")))
                .budget(Budget.fromId((String) a.get("firstDateBudget")))
                .style(MatchingStyle.fromId((String) a.get("matchingStyle")))
                .looking(looking)
                .hobbies(new HashSet<>(hobbies))
                .hobbyDetails(details)
                .location(TestPlaces.get((String) loc.get("placeId"))
                        .at(TravelMode.fromId((String) loc.get("mode")), (Integer) loc.get("minutes")))
                .contactHashes(Set.of("handle:" + user.get("handle")))
                .ai(ai((Map<String, Object>) user.get("aiConstraints")));
        if (student) b.schoolYear(SchoolYear.fromId((String) a.get("schoolYear")));
        return b.build();
    }

    private static AiConstraints ai(Map<String, Object> m) {
        if (m == null) return AiConstraints.NONE;
        return new AiConstraints((Integer) m.get("minHeight"), (Integer) m.get("maxHeight"), (Integer) m.get("minAge"),
                (Integer) m.get("maxAge"), Boolean.TRUE.equals(m.get("studentsOnly")), Boolean.TRUE.equals(m.get("nonStudentsOnly")));
    }
}
