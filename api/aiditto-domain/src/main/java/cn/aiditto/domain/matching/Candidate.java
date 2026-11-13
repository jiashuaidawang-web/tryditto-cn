package cn.aiditto.domain.matching;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * One pool member as the solver sees them: structured profile fields only, no phone, email, photos
 * or free text (技术方案 §7.1). Field values use the frontend option ids via the enums.
 *
 * @param pool city pool ({@code bj}, {@code sh}); only same-pool pairs connect
 * @param hobbies {@code hobbies} answer, custom hobbies as {@code custom:文字}
 * @param hobbyDetails multi-choice hobby branch answers, e.g. {@code musicSound → {indie, rock}}
 * @param emptyWeekStreak consecutive empty weeks, drives the fairness bonus
 * @param contactHashes this person's own phone/email hashes (hex)
 * @param blockedHashes hashes from this person's block list
 * @param ai constraints from dealbreakers; {@link AiConstraints#NONE} unless {@code ai_status = ok}
 * @param hasOpenMatch has a non-terminal match from an earlier run (excluded in manual runs)
 * @param cohort real users only match real users; synthetic users only match demo accounts
 */
public record Candidate(
        long id,
        String pool,
        Gender gender,
        Set<GenderPref> wants,
        LocalDate birthday,
        int ageMin,
        int ageMax,
        int heightCm,
        HeightPref heightPref,
        boolean student,
        SchoolYear schoolYear,
        EducationLevel education,
        Importance educationImportance,
        Budget budget,
        MatchingStyle style,
        Set<Looking> looking,
        Set<String> hobbies,
        Map<String, Set<String>> hobbyDetails,
        Location location,
        int emptyWeekStreak,
        Set<String> contactHashes,
        Set<String> blockedHashes,
        AiConstraints ai,
        boolean hasOpenMatch,
        Cohort cohort) {

    public Candidate {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(birthday, "birthday");
        Objects.requireNonNull(heightPref, "heightPref");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(location, "location");
        wants = Set.copyOf(wants);
        looking = Set.copyOf(looking);
        hobbies = Set.copyOf(hobbies);
        TreeMap<String, Set<String>> details = new TreeMap<>();
        hobbyDetails.forEach((k, v) -> details.put(k, Set.copyOf(v)));
        hobbyDetails = Collections.unmodifiableMap(details);
        contactHashes = Set.copyOf(contactHashes);
        blockedHashes = Set.copyOf(blockedHashes);
        ai = ai == null ? AiConstraints.NONE : ai;
        cohort = cohort == null ? Cohort.REAL : cohort;
        budget = budget == null ? Budget.NO_PREF : budget;
        educationImportance = educationImportance == null ? Importance.NONE : educationImportance;
        if (ageMin > ageMax) throw new IllegalArgumentException("ageMin > ageMax");
    }

    /** Age in whole years on the drop Wednesday. */
    public int ageOn(LocalDate weekStart) {
        return Period.between(birthday, weekStart).getYears();
    }

    public static Builder builder(long id) {
        return new Builder(id);
    }

    public Builder toBuilder() {
        Builder b = new Builder(id);
        b.pool = pool; b.gender = gender; b.wants = wants; b.birthday = birthday; b.ageMin = ageMin;
        b.ageMax = ageMax; b.heightCm = heightCm; b.heightPref = heightPref; b.student = student;
        b.schoolYear = schoolYear; b.education = education; b.educationImportance = educationImportance;
        b.budget = budget; b.style = style; b.looking = looking; b.hobbies = hobbies; b.hobbyDetails = hobbyDetails;
        b.location = location; b.emptyWeekStreak = emptyWeekStreak; b.contactHashes = contactHashes;
        b.blockedHashes = blockedHashes; b.ai = ai; b.hasOpenMatch = hasOpenMatch; b.cohort = cohort;
        return b;
    }

    /** Builder with permissive defaults (Beijing, 20 years old in 2026, wants everyone, steady). */
    public static final class Builder {
        private final long id;
        private String pool = "bj";
        private Gender gender = Gender.WOMAN;
        private Set<GenderPref> wants = Set.of(GenderPref.EVERYONE);
        private LocalDate birthday = LocalDate.of(2006, 1, 1);
        private int ageMin = 18;
        private int ageMax = 30;
        private int heightCm = 170;
        private HeightPref heightPref = HeightPref.ANY;
        private boolean student = true;
        private SchoolYear schoolYear = SchoolYear.JUNIOR;
        private EducationLevel education = EducationLevel.COLLEGE;
        private Importance educationImportance = Importance.NONE;
        private Budget budget = Budget.NO_PREF;
        private MatchingStyle style = MatchingStyle.STEADY;
        private Set<Looking> looking = Set.of(Looking.SERIOUS);
        private Set<String> hobbies = Set.of("music", "movies");
        private Map<String, Set<String>> hobbyDetails = Map.of();
        private Location location = new Location("wudaokou", 39.9925, 116.3378, TravelMode.TRANSIT, 45);
        private int emptyWeekStreak;
        private Set<String> contactHashes = Set.of();
        private Set<String> blockedHashes = Set.of();
        private AiConstraints ai = AiConstraints.NONE;
        private boolean hasOpenMatch;
        private Cohort cohort = Cohort.REAL;

        private Builder(long id) {
            this.id = id;
        }

        public Builder pool(String v) { pool = v; return this; }
        public Builder gender(Gender v) { gender = v; return this; }
        public Builder wants(GenderPref... v) { wants = Set.of(v); return this; }
        public Builder wants(Set<GenderPref> v) { wants = v; return this; }
        public Builder birthday(LocalDate v) { birthday = v; return this; }
        public Builder ageRange(int min, int max) { ageMin = min; ageMax = max; return this; }
        public Builder heightCm(int v) { heightCm = v; return this; }
        public Builder heightPref(HeightPref v) { heightPref = v; return this; }
        public Builder student(boolean v) { student = v; if (!v) schoolYear = null; return this; }
        public Builder schoolYear(SchoolYear v) { schoolYear = v; return this; }
        public Builder education(EducationLevel v, Importance importance) { education = v; educationImportance = importance; return this; }
        public Builder budget(Budget v) { budget = v; return this; }
        public Builder style(MatchingStyle v) { style = v; return this; }
        public Builder looking(Looking... v) { looking = Set.of(v); return this; }
        public Builder looking(Set<Looking> v) { looking = v; return this; }
        public Builder hobbies(String... v) { hobbies = Set.of(v); return this; }
        public Builder hobbies(Set<String> v) { hobbies = v; return this; }
        public Builder hobbyDetails(Map<String, Set<String>> v) { hobbyDetails = v; return this; }
        public Builder location(Location v) { location = v; return this; }
        public Builder emptyWeekStreak(int v) { emptyWeekStreak = v; return this; }
        public Builder contactHashes(Set<String> v) { contactHashes = v; return this; }
        public Builder blockedHashes(Set<String> v) { blockedHashes = v; return this; }
        public Builder ai(AiConstraints v) { ai = v; return this; }
        public Builder hasOpenMatch(boolean v) { hasOpenMatch = v; return this; }
        public Builder cohort(Cohort v) { cohort = v; return this; }

        public Candidate build() {
            return new Candidate(id, pool, gender, wants, birthday, ageMin, ageMax, heightCm, heightPref, student,
                    schoolYear, education, educationImportance, budget, style, looking, hobbies, hobbyDetails,
                    location, emptyWeekStreak, contactHashes, blockedHashes, ai, hasOpenMatch, cohort);
        }
    }
}
