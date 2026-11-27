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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-labeled stress set. Labels are id tables written before any recall run.
 * A shared hobby is not enough to mark two people relevant.
 *
 * Frozen reading, written before the run: safety counts must be 0. B3 is ahead of the random
 * legal pool when dev Recall@20 is higher by at least {@link #BEAT_RANDOM}. B3 must also be at
 * least the unchanged production scorer. Weights are not retuned from this set.
 */
public final class RecallChallenge {
    public static final String SOURCE = "synthetic-fixture:v4-recall-challenge-v1";
    public static final double BEAT_RANDOM = 0.05;
    public static final LocalDate WEEK = RecallBench.WEEK;
    public static final Instant NOW = RecallBench.NOW;

    public record Dataset(List<RecallSubject> subjects, List<RecallMetrics.Label> labels, MatchingInput input) {
        public List<RecallSubject> split(String split) {
            return subjects.stream().filter(subject -> split.equals(subject.split())).toList();
        }

        public List<RecallMetrics.Label> labels(String split) {
            return labels.stream().filter(label -> split.equals(label.split())).toList();
        }
    }

    public record Outcome(RecallMetrics.Summary random, RecallMetrics.Summary scorer,
                          RecallMetrics.Summary structured, RecallMetrics.Summary multi,
                          int scorerTiePairs, int scorerSmallerIdWins, String verdict) {}

    private RecallChallenge() {}

    public static Dataset load() {
        List<RecallSubject> people = new ArrayList<>();
        add(people, "dev", 1, 8, Set.of("reading"), Map.of("self.pace", "slow"), Map.of("partner.pace", "fast"));
        add(people, "dev", 9, 28, Set.of("reading"), Map.of("self.pace", "slow"), Map.of("partner.pace", "slow"));
        add(people, "dev", 29, 36, Set.of("gym"), Map.of("self.pace", "fast"), Map.of());
        add(people, "dev", 37, 40, Set.of("music"), Map.of("self.communication", "quiet"), Map.of("partner.communication", "frequent"));
        add(people, "dev", 41, 44, Set.of("gym"), Map.of("self.communication", "frequent"), Map.of("partner.communication", "quiet"));
        add(people, "dev", 45, 60, Set.of("music"), Map.of("self.communication", "quiet"), Map.of("partner.communication", "quiet"));
        add(people, "dev", 61, 61, Set.of("reading", "music"), Map.of("self.pace", "slow", "self.communication", "frequent"),
                Map.of("partner.pace", "slow", "partner.communication", "frequent"));
        add(people, "dev", 62, 64, Set.of("reading", "music"), Map.of("self.pace", "slow", "self.communication", "frequent"), Map.of());
        add(people, "dev", 65, 69, Set.of("art"), Map.of("self.pace", "slow", "self.communication", "quiet"), Map.of());
        add(people, "dev", 70, 84, Set.of("reading"), Map.of("self.pace", "fast", "self.communication", "quiet"), Map.of());
        add(people, "dev", 85, 88, Set.of("cooking"), Map.of("self.schedule", "late"), Map.of("partner.social.initiative", "high"));
        add(people, "dev", 89, 92, Set.of("travel"), Map.of("self.social.initiative", "high"), Map.of("partner.schedule", "early"));
        add(people, "dev", 93, 96, Set.of("movies"), Map.of(), Map.of("partner.support", "close"));
        add(people, "dev", 97, 100, Set.of("gaming"), Map.of("self.communication", "frequent"), Map.of());
        add(people, "dev", 101, 121, Set.of("reading"), Map.of("self.communication", "quiet"), Map.of());
        add(people, "dev", 122, 131, Set.of(), Map.of(), Map.of());
        people.add(older("dev", 132));
        people.add(older("dev", 133));
        people.add(special("dev", 134, Set.of("reading", "music"), Map.of("self.pace", "slow", "self.communication", "frequent"), Map.of(), true, false, ConsentStatus.GRANTED, Cohort.REAL, true));
        people.add(special("dev", 135, Set.of("reading"), Map.of("self.communication", "quiet"), Map.of(), false, true, ConsentStatus.GRANTED, Cohort.REAL, true));
        add(people, "dev", 136, 136, Set.of("reading"), Map.of("self.pace", "slow"), Map.of("partner.pace", "slow", "partner.communication", "frequent"));
        people.add(special("dev", 137, Set.of("art"), Map.of("self.pace", "slow", "self.communication", "frequent"), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.REAL, true));
        people.add(special("dev", 138, Set.of("art"), Map.of("self.pace", "slow", "self.communication", "quiet"), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.REAL, true));
        people.add(special("dev", 139, Set.of("art"), Map.of("self.pace", "fast", "self.communication", "frequent"), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.REAL, true));
        add(people, "dev", 140, 145, Set.of("reading"), Map.of("self.pace", "fast", "self.communication", "quiet"), Map.of());
        add(people, "dev", 146, 147, Set.of(), Map.of(), Map.of());
        people.add(special("dev", 148, Set.of("reading"), Map.of(), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.SYNTHETIC, true));
        people.add(special("dev", 149, Set.of("reading"), Map.of(), Map.of(), true, true, ConsentStatus.REVOKED, Cohort.REAL, true));
        people.add(special("dev", 150, Set.of(), Map.of(), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.REAL, false));
        add(people, "holdout", 151, 154, Set.of("art"), Map.of("self.pace", "fast"), Map.of("partner.pace", "slow"));
        add(people, "holdout", 155, 164, Set.of("art"), Map.of("self.pace", "fast"), Map.of());
        add(people, "holdout", 165, 168, Set.of("gaming"), Map.of("self.pace", "slow"), Map.of());
        add(people, "holdout", 169, 170, Set.of("music"), Map.of("self.communication", "quiet"), Map.of("partner.communication", "frequent"));
        add(people, "holdout", 171, 172, Set.of("gym"), Map.of("self.communication", "frequent"), Map.of("partner.communication", "quiet"));
        add(people, "holdout", 173, 173, Set.of("reading", "music"), Map.of("self.pace", "slow"), Map.of("partner.communication", "frequent"));
        add(people, "holdout", 174, 176, Set.of("reading", "music"), Map.of("self.pace", "slow", "self.communication", "frequent"), Map.of());
        add(people, "holdout", 177, 186, Set.of("reading"), Map.of("self.pace", "fast"), Map.of());
        add(people, "holdout", 187, 188, Set.of("cooking"), Map.of("self.schedule", "late"), Map.of("partner.social.initiative", "high"));
        add(people, "holdout", 189, 190, Set.of("travel"), Map.of("self.social.initiative", "high"), Map.of("partner.schedule", "early"));
        add(people, "holdout", 191, 192, Set.of("movies"), Map.of(), Map.of("partner.support", "close"));
        add(people, "holdout", 193, 194, Set.of("gaming"), Map.of("self.communication", "frequent"), Map.of());
        add(people, "holdout", 195, 205, Set.of("reading"), Map.of("self.communication", "quiet"), Map.of());
        add(people, "holdout", 206, 210, Set.of(), Map.of(), Map.of());
        people.add(older("holdout", 211));
        people.add(special("holdout", 212, Set.of("gaming"), Map.of("self.pace", "slow"), Map.of(), true, false, ConsentStatus.GRANTED, Cohort.REAL, true));
        people.add(special("holdout", 213, Set.of("art"), Map.of("self.pace", "slow"), Map.of(), false, true, ConsentStatus.GRANTED, Cohort.REAL, true));
        add(people, "holdout", 214, 218, Set.of(), Map.of(), Map.of());
        people.add(special("holdout", 219, Set.of("art"), Map.of(), Map.of(), true, true, ConsentStatus.GRANTED, Cohort.SYNTHETIC, true));
        people.add(special("holdout", 220, Set.of("art"), Map.of(), Map.of(), true, true, ConsentStatus.REVOKED, Cohort.REAL, true));
        if (people.size() != 220) throw new IllegalStateException("challenge size " + people.size());
        List<Candidate> candidates = people.stream().map(RecallSubject::candidate).toList();
        MatchingInput input = new MatchingInput(WEEK, candidates, Set.of(PairKey.of(1, 29)), Set.of(), Weights.W1, RecallBench.SEED, false);
        return new Dataset(List.copyOf(people), labels(), input);
    }

    public static String verdict(RecallMetrics.Summary random, RecallMetrics.Summary scorer, RecallMetrics.Summary multi) {
        if (multi.safetyAuditViolations() != 0 || multi.hardViolations() != 0
                || multi.expiredReturned() != 0 || multi.withdrawnReturned() != 0) return "NO-GO";
        if (multi.recallAt20() == null || random.recallAt20() == null || scorer.recallAt20() == null) return "NO-GO";
        if (multi.recallAt20() >= random.recallAt20() + BEAT_RANDOM && multi.recallAt20() >= scorer.recallAt20()) return "GO";
        return "NO-GO";
    }

    private static List<RecallMetrics.Label> labels() {
        List<RecallMetrics.Label> labels = new ArrayList<>();
        relevant("dev", ids(1, 8), ids(29, 36), "FORWARD", 2, labels);
        replace(labels, "dev", 1, 29, "HARD_VETO");
        irrelevant("dev", ids(1, 8), ids(9, 28), labels);
        veto("dev", ids(1, 8), List.of(132L, 133L), labels);
        both("dev", ids(37, 40), ids(41, 44), 3, labels);
        irrelevant("dev", ids(37, 40), ids(45, 60), labels);
        relevant("dev", List.of(61L), ids(62, 64), "STRONG", 3, labels);
        relevant("dev", List.of(61L), ids(65, 69), "PACE", 2, labels);
        irrelevant("dev", List.of(61L), ids(70, 84), labels);
        relevant("dev", ids(85, 88), ids(89, 92), "FORWARD", 2, labels);
        irrelevant("dev", ids(89, 92), ids(85, 88), labels);
        relevant("dev", ids(93, 96), ids(97, 100), "COMPLEMENT", 2, labels);
        irrelevant("dev", ids(97, 100), ids(93, 96), labels);
        relevant("dev", List.of(101L), ids(102, 121), "SIMILAR", 1, labels);
        labels.add(new RecallMetrics.Label(122, 101, "dev", "UNKNOWN", "NONE", 0));
        relevant("dev", List.of(136L), List.of(137L), "BOTH", 3, labels);
        relevant("dev", List.of(136L), List.of(138L, 139L), "PARTIAL", 1, labels);
        irrelevant("dev", List.of(136L), ids(140, 145), labels);
        relevant("holdout", ids(151, 154), ids(165, 168), "FORWARD", 2, labels);
        irrelevant("holdout", ids(151, 154), ids(155, 164), labels);
        veto("holdout", ids(151, 154), List.of(211L), labels);
        both("holdout", ids(169, 170), ids(171, 172), 3, labels);
        relevant("holdout", List.of(173L), ids(174, 176), "STRONG", 3, labels);
        irrelevant("holdout", List.of(173L), ids(177, 186), labels);
        relevant("holdout", ids(187, 188), ids(189, 190), "FORWARD", 2, labels);
        irrelevant("holdout", ids(189, 190), ids(187, 188), labels);
        relevant("holdout", ids(191, 192), ids(193, 194), "COMPLEMENT", 2, labels);
        irrelevant("holdout", ids(193, 194), ids(191, 192), labels);
        relevant("holdout", List.of(195L), ids(196, 205), "SIMILAR", 1, labels);
        labels.add(new RecallMetrics.Label(206, 195, "holdout", "UNKNOWN", "NONE", 0));
        return List.copyOf(labels);
    }

    private static void add(List<RecallSubject> people, String split, int from, int to, Set<String> hobbies,
                            Map<String, String> self, Map<String, String> preference) {
        for (int id = from; id <= to; id++) {
            people.add(special(split, id, hobbies, self, preference, true, true, ConsentStatus.GRANTED, Cohort.REAL, true));
        }
    }

    private static RecallSubject older(String split, long id) {
        Candidate candidate = Candidate.builder(id).birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60).hobbies("reading").build();
        return subject(split, id, candidate, true, true, ConsentStatus.GRANTED, true, Set.of("reading"), Map.of(), Map.of());
    }

    private static RecallSubject special(String split, long id, Set<String> hobbies, Map<String, String> self,
                                         Map<String, String> preference, boolean current, boolean notWithdrawn,
                                         ConsentStatus consent, Cohort cohort, boolean active) {
        Candidate candidate = Candidate.builder(id).cohort(cohort).hobbies(hobbies.toArray(String[]::new)).build();
        return subject(split, id, candidate, current, notWithdrawn, consent, active, hobbies, self, preference);
    }

    private static RecallSubject subject(String split, long id, Candidate candidate, boolean current, boolean notWithdrawn,
                                         ConsentStatus consent, boolean active, Set<String> hobbies,
                                         Map<String, String> self, Map<String, String> preference) {
        boolean synthetic = candidate.cohort() == Cohort.SYNTHETIC;
        PoolGate.Applicant applicant = new PoolGate.Applicant(
                candidate.birthday(), active, true, synthetic, synthetic ? 0 : 2, false, false, null, false);
        MatchingConsent matching = new MatchingConsent(id, consent, consent == ConsentStatus.UNKNOWN ? 0 : 1,
                consent == ConsentStatus.GRANTED ? NOW : null, consent == ConsentStatus.REVOKED ? NOW : null,
                consent == ConsentStatus.GRANTED ? ConsentPolicy.POLICY_VERSION : null, NOW);
        return new RecallSubject(id, split, current ? 2 : 1, current, !notWithdrawn, SOURCE, candidate, applicant,
                matching, self, hobbies, preference, 1, RecallIngress.SYNTHETIC_FIXTURE);
    }

    private static void relevant(String split, List<Long> seekers, List<Long> targets, String direction, int grade,
                                 List<RecallMetrics.Label> labels) {
        for (long seeker : seekers) {
            for (long target : targets) {
                labels.add(new RecallMetrics.Label(seeker, target, split, "RELEVANT", direction, grade));
            }
        }
    }

    private static void both(String split, List<Long> left, List<Long> right, int grade, List<RecallMetrics.Label> labels) {
        relevant(split, left, right, "MUTUAL", grade, labels);
        relevant(split, right, left, "MUTUAL", grade, labels);
    }

    private static void irrelevant(String split, List<Long> seekers, List<Long> targets, List<RecallMetrics.Label> labels) {
        for (long seeker : seekers) {
            for (long target : targets) labels.add(new RecallMetrics.Label(seeker, target, split, "IRRELEVANT", "NONE", 0));
        }
    }

    private static void veto(String split, List<Long> left, List<Long> right, List<RecallMetrics.Label> labels) {
        for (long one : left) {
            for (long other : right) {
                labels.add(new RecallMetrics.Label(one, other, split, "HARD_VETO", "NONE", 0));
                labels.add(new RecallMetrics.Label(other, one, split, "HARD_VETO", "NONE", 0));
            }
        }
    }

    private static void replace(List<RecallMetrics.Label> labels, String split, long seeker, long candidate, String kind) {
        labels.removeIf(label -> label.seeker() == seeker && label.candidate() == candidate);
        labels.removeIf(label -> label.seeker() == candidate && label.candidate() == seeker);
        labels.add(new RecallMetrics.Label(seeker, candidate, split, kind, "NONE", 0));
        labels.add(new RecallMetrics.Label(candidate, seeker, split, kind, "NONE", 0));
    }

    private static List<Long> ids(int from, int to) {
        List<Long> ids = new ArrayList<>();
        for (int id = from; id <= to; id++) ids.add((long) id);
        return ids;
    }
}
