package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.Cohort;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.matching.Weights;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-built 100-person bench. Group membership is the annotation.
 * The recall engine is not asked to invent these labels.
 */
public final class RecallBench {
    public static final String SOURCE = "synthetic-fixture:v4-recall-bench-v1";
    public static final LocalDate WEEK = LocalDate.of(2026, 11, 25);
    public static final Instant NOW = Instant.parse("2026-11-27T06:00:00Z");
    public static final long SEED = 20261118L;
    public static final int LIST_SIZE = 50;

    private final List<RecallSubject> subjects;
    private final List<RecallMetrics.Label> labels;
    private final MatchingInput input;

    private RecallBench(List<RecallSubject> subjects, List<RecallMetrics.Label> labels, MatchingInput input) {
        this.subjects = List.copyOf(subjects);
        this.labels = List.copyOf(labels);
        this.input = input;
    }

    public static RecallBench load() {
        List<RecallSubject> people = new ArrayList<>();
        people.addAll(cluster("dev", 1, 8, Set.of("reading", "music"), Map.of(), Map.of(), false));
        people.addAll(cluster("dev", 9, 14, Set.of(), Map.of("self.communication", "quiet", "self.pace", "slow"), Map.of(), false));
        people.addAll(cluster("dev", 15, 18, Set.of(), Map.of(), Map.of("partner.communication", "frequent"), false));
        people.addAll(cluster("dev", 19, 22, Set.of(), Map.of("self.communication", "frequent"), Map.of(), false));
        people.addAll(cluster("dev", 23, 26, Set.of(), Map.of("self.social.initiative", "high"), Map.of(), false));
        people.addAll(cluster("dev", 27, 30, Set.of(), Map.of(), Map.of("partner.social.initiative", "high"), false));
        people.add(person("dev", 31, Set.of(), Map.of("self.pace", "fast"), Map.of("partner.pace", "slow"), true, true, ConsentStatus.GRANTED, Cohort.REAL));
        people.add(person("dev", 32, Set.of(), Map.of("self.pace", "slow"), Map.of("partner.pace", "fast"), true, true, ConsentStatus.GRANTED, Cohort.REAL));
        people.addAll(cluster("dev", 33, 34, Set.of(), Map.of(), Map.of("partner.support", "close"), false));
        people.addAll(cluster("dev", 35, 36, Set.of(), Map.of("self.communication", "frequent"), Map.of(), false));
        people.add(older("dev", 37));
        people.add(older("dev", 38));
        people.add(person("dev", 39, Set.of("reading", "music"), Map.of(), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.REAL));
        people.add(person("dev", 40, Set.of("reading", "music"), Map.of(), Map.of(), true, false, ConsentStatus.GRANTED, Cohort.REAL));
        people.add(stale("dev", 41));
        people.addAll(cluster("dev", 42, 46, Set.of(), Map.of(), Map.of(), false));
        people.add(person("dev", 47, Set.of("reading"), Map.of(), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.SYNTHETIC));
        people.add(inactive("dev", 48));
        people.add(person("dev", 49, Set.of("reading"), Map.of(), Map.of(), true, true, ConsentStatus.REVOKED, Cohort.REAL));
        people.addAll(cluster("dev", 50, 61, Set.of("cooking"), Map.of(), Map.of(), false));
        people.addAll(cluster("dev", 62, 70, Set.of(), Map.of(), Map.of(), false));
        people.addAll(cluster("holdout", 71, 76, Set.of("art", "movies"), Map.of(), Map.of(), false));
        people.addAll(cluster("holdout", 77, 80, Set.of(), Map.of("self.exploration", "high"), Map.of(), false));
        people.addAll(cluster("holdout", 81, 82, Set.of(), Map.of(), Map.of("partner.pace", "slow"), false));
        people.addAll(cluster("holdout", 83, 84, Set.of(), Map.of("self.pace", "slow"), Map.of(), false));
        people.add(person("holdout", 85, Set.of(), Map.of("self.schedule", "early"), Map.of("partner.schedule", "late"), true, true, ConsentStatus.GRANTED, Cohort.REAL));
        people.add(person("holdout", 86, Set.of(), Map.of("self.schedule", "late"), Map.of("partner.schedule", "early"), true, true, ConsentStatus.GRANTED, Cohort.REAL));
        people.add(person("holdout", 87, Set.of(), Map.of(), Map.of("partner.communication", "direct"), true, true, ConsentStatus.GRANTED, Cohort.REAL));
        people.add(person("holdout", 88, Set.of(), Map.of("self.social.initiative", "high"), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.REAL));
        people.add(older("holdout", 89));
        people.add(person("holdout", 90, Set.of("art", "movies"), Map.of(), Map.of(), true, false, ConsentStatus.GRANTED, Cohort.REAL));
        people.addAll(cluster("holdout", 91, 100, Set.of(), Map.of(), Map.of(), false));
        people = withBlock(people);
        if (people.size() != 100) throw new IllegalStateException("bench size " + people.size());
        List<RecallMetrics.Label> labels = buildLabels();
        List<Candidate> candidates = people.stream().map(RecallSubject::candidate).toList();
        MatchingInput input = new MatchingInput(WEEK, candidates, Set.of(PairKey.of(1, 2)), Set.of(), Weights.W1, SEED, false);
        return new RecallBench(people, labels, input);
    }

    public List<RecallSubject> subjects() { return subjects; }

    public List<RecallMetrics.Label> labels() { return labels; }

    public MatchingInput input() { return input; }

    public List<RecallSubject> split(String split) {
        return subjects.stream().filter(subject -> split.equals(subject.split())).toList();
    }

    public List<RecallMetrics.Label> labels(String split) {
        return labels.stream().filter(label -> split.equals(label.split())).toList();
    }

    private static List<RecallMetrics.Label> buildLabels() {
        List<RecallMetrics.Label> labels = new ArrayList<>();
        similar(labels, "dev", ids(1, 8), 2);
        vetoPair(labels, "dev", 1, 2);
        similar(labels, "dev", List.of(39L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), 2);
        vetoPair(labels, "dev", 1, 39);
        similar(labels, "dev", ids(9, 14), 2);
        directed(labels, "dev", ids(15, 18), ids(19, 22), "FORWARD", 1);
        directed(labels, "dev", ids(23, 26), ids(27, 30), "REVERSE", 1);
        directed(labels, "dev", List.of(31L), List.of(32L), "MUTUAL", 2);
        directed(labels, "dev", List.of(32L), List.of(31L), "MUTUAL", 2);
        directed(labels, "dev", ids(33, 34), ids(35, 36), "COMPLEMENT", 1);
        similar(labels, "dev", ids(50, 61), 2);
        for (long young : ids(1, 8)) {
            vetoPair(labels, "dev", young, 37);
            vetoPair(labels, "dev", young, 38);
        }
        similar(labels, "holdout", ids(71, 76), 2);
        similar(labels, "holdout", ids(77, 80), 2);
        directed(labels, "holdout", ids(81, 82), ids(83, 84), "FORWARD", 1);
        directed(labels, "holdout", List.of(85L), List.of(86L), "MUTUAL", 2);
        directed(labels, "holdout", List.of(86L), List.of(85L), "MUTUAL", 2);
        directed(labels, "holdout", List.of(87L), List.of(88L), "COMPLEMENT", 1);
        for (long young : ids(71, 76)) vetoPair(labels, "holdout", young, 89);
        irrelevant(labels, "dev", 1, 9);
        irrelevant(labels, "dev", 1, 19);
        irrelevant(labels, "dev", 9, 19);
        irrelevant(labels, "dev", 15, 1);
        irrelevant(labels, "dev", 50, 1);
        irrelevant(labels, "holdout", 71, 77);
        irrelevant(labels, "holdout", 81, 71);
        unknown(labels, "dev", 42, 1);
        unknown(labels, "dev", 42, 9);
        unknown(labels, "holdout", 91, 71);
        Map<String, RecallMetrics.Label> unique = new java.util.LinkedHashMap<>();
        for (RecallMetrics.Label label : labels) unique.put(label.seeker() + ":" + label.candidate(), label);
        return List.copyOf(unique.values());
    }

    private static void similar(List<RecallMetrics.Label> labels, String split, List<Long> ids, int grade) {
        for (int i = 0; i < ids.size(); i++) {
            for (int j = 0; j < ids.size(); j++) {
                if (i == j) continue;
                labels.add(new RecallMetrics.Label(ids.get(i), ids.get(j), split, "RELEVANT", "SIMILAR", grade));
            }
        }
    }

    private static void directed(List<RecallMetrics.Label> labels, String split, List<Long> seekers, List<Long> targets,
                                 String direction, int grade) {
        for (long seeker : seekers) {
            for (long target : targets) {
                labels.add(new RecallMetrics.Label(seeker, target, split, "RELEVANT", direction, grade));
            }
        }
    }

    private static void vetoPair(List<RecallMetrics.Label> labels, String split, long left, long right) {
        labels.add(new RecallMetrics.Label(left, right, split, "HARD_VETO", "NONE", 0));
        labels.add(new RecallMetrics.Label(right, left, split, "HARD_VETO", "NONE", 0));
    }

    private static void irrelevant(List<RecallMetrics.Label> labels, String split, long seeker, long candidate) {
        labels.add(new RecallMetrics.Label(seeker, candidate, split, "IRRELEVANT", "NONE", 0));
    }

    private static void unknown(List<RecallMetrics.Label> labels, String split, long seeker, long candidate) {
        labels.add(new RecallMetrics.Label(seeker, candidate, split, "UNKNOWN", "NONE", 0));
    }

    private static List<Long> ids(int from, int to) {
        List<Long> ids = new ArrayList<>();
        for (int id = from; id <= to; id++) ids.add((long) id);
        return ids;
    }

    private static List<RecallSubject> cluster(String split, int from, int to, Set<String> hobbies,
                                              Map<String, String> self, Map<String, String> preference, boolean withdrawn) {
        List<RecallSubject> out = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            out.add(person(split, id, hobbies, self, preference, true, !withdrawn, ConsentStatus.GRANTED, Cohort.REAL));
        }
        return out;
    }

    private static RecallSubject older(String split, long id) {
        Candidate candidate = Candidate.builder(id).birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60)
                .hobbies("reading", "music", "art", "movies").build();
        return subject(split, id, candidate, true, true, ConsentStatus.GRANTED, true,
                Set.of("reading", "music", "art", "movies"), Map.of(), Map.of());
    }

    private static RecallSubject stale(String split, long id) {
        Candidate candidate = Candidate.builder(id).hobbies("reading", "music").build();
        return subject(split, id, candidate, false, true, ConsentStatus.GRANTED, true,
                Set.of("reading", "music"), Map.of(), Map.of());
    }

    private static RecallSubject inactive(String split, long id) {
        Candidate candidate = Candidate.builder(id).build();
        RecallSubject subject = subject(split, id, candidate, true, true, ConsentStatus.GRANTED, true, Set.of(), Map.of(), Map.of());
        PoolGate.Applicant applicant = new PoolGate.Applicant(candidate.birthday(), false, true, false, 2, false, false, null, false);
        return new RecallSubject(id, split, 2, true, false, SOURCE, candidate, applicant, subject.consent(), Map.of(), Set.of(), Map.of());
    }

    private static RecallSubject person(String split, long id, Set<String> hobbies, Map<String, String> self,
                                        Map<String, String> preference, boolean current, boolean searchableConsent,
                                        ConsentStatus consent, Cohort cohort) {
        Candidate.Builder builder = Candidate.builder(id).cohort(cohort).hobbies(hobbies.toArray(String[]::new));
        Candidate candidate = builder.build();
        return subject(split, id, candidate, current, searchableConsent, consent, true, hobbies, self, preference);
    }

    private static RecallSubject subject(String split, long id, Candidate candidate, boolean current, boolean notWithdrawn,
                                         ConsentStatus consent, boolean active, Set<String> hobbies,
                                         Map<String, String> self, Map<String, String> preference) {
        boolean synthetic = candidate.cohort() == Cohort.SYNTHETIC;
        PoolGate.Applicant applicant = new PoolGate.Applicant(
                candidate.birthday(), active, true, synthetic, synthetic ? 0 : 2, false, false, null, false);
        Instant granted = consent == ConsentStatus.GRANTED ? NOW : null;
        Instant revoked = consent == ConsentStatus.REVOKED ? NOW : null;
        MatchingConsent matching = new MatchingConsent(id, consent, consent == ConsentStatus.UNKNOWN ? 0 : 1,
                granted, revoked, consent == ConsentStatus.GRANTED ? ConsentPolicy.POLICY_VERSION : null, NOW);
        return new RecallSubject(id, split, current ? 2 : 1, current, !notWithdrawn, SOURCE, candidate, applicant,
                matching, self, hobbies, preference);
    }

    private static List<RecallSubject> withBlock(List<RecallSubject> people) {
        Map<Long, RecallSubject> byId = new LinkedHashMap<>();
        for (RecallSubject subject : people) byId.put(subject.userId(), subject);
        RecallSubject seeker = byId.get(1L);
        RecallSubject target = byId.get(39L);
        byId.put(1L, rebuild(seeker, seeker.candidate().toBuilder().blockedHashes(Set.of("block-39")).build()));
        byId.put(39L, rebuild(target, target.candidate().toBuilder().contactHashes(Set.of("block-39")).build()));
        return new ArrayList<>(byId.values());
    }

    private static RecallSubject rebuild(RecallSubject subject, Candidate candidate) {
        return new RecallSubject(subject.userId(), subject.split(), subject.profileVersion(), subject.current(),
                subject.withdrawn(), subject.fixtureSource(), candidate, subject.applicant(), subject.consent(),
                subject.selfEnums(), subject.hobbies(), subject.preferenceEnums());
    }
}
