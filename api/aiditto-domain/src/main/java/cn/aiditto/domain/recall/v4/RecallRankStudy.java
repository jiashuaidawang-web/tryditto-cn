package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.CompatibilityDecision;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.compatibility.v4.PairCompatibilityResult;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.recall.RrfFusion;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline candidate budgets and M02 coarse rank. The frozen M01 weights and the userId tie
 * in {@link RrfFusion} stay as they are. This study does not write a second compatibility formula.
 */
public final class RecallRankStudy {
    /** Resource cap fixed before any timing run. A larger size that would pass it is not executed. */
    public static final long SCALE_WALL_MS = 90_000L;
    public static final int[] SCALE_SIZES = {100, 500, 1_000, 5_000, 10_000};

    public enum Scheme { A_RRF, B_FORWARD, C_BIDIRECTIONAL, D_COVERAGE, E_FULL_POOL, F_PREFILTER }
    public enum Miss { COVERED, NO_ROUTE_SIGNAL, ROUTE_BUDGET, FUSION_TRUNCATED, COARSE_INPUT_CUT, COARSE_RANK_CUT }

    public record Budgets(int route, int fusion, int coarse, int fin) {}

    public static final Budgets NARROW = new Budgets(50, 50, 20, 20);
    public static final Budgets WIDE = new Budgets(200, 200, 100, 20);
    /** Declared before reading results. These points are reported; they are not used to pick a new weight. */
    public static final List<Budgets> SWEEP = List.of(
            new Budgets(20, 20, 20, 20),
            new Budgets(50, 50, 50, 20),
            new Budgets(100, 100, 100, 20),
            new Budgets(200, 200, 200, 20));

    public record Placement(long candidateId, RecallRankFixture.Label label, Miss miss, int recallRank, int finalRank) {}

    public record Quality(int fused10, int fused20, int fused50, int scheme10, int scheme20, int scheme50,
                          int lostToRecall, int lostToCoarse, int strongAt20, int decoyAt20, int otherAt20, int slots20,
                          double ndcg20, double mrr, int zeroQueries, int authHits, int staleHits, int hardHits,
                          int legal, int scored, int poolEligible, int poolInsufficient, String stages) {}

    public record Run(String split, Scheme scheme, Budgets budgets, List<Placement> placements,
                      int strong, int strongCovered, int recommended, int insufficient, int hardRejected,
                      int safetyViolations, int tiePairs, int fused, long elapsedMs, Quality quality) {}

    public record Edge(long lowId, long highId, long versionLow, long versionHigh, long generationLow, long generationHigh,
                       String routes, Integer recallRank, Integer coarseRank, Integer finalRank,
                       Double scoreLowToHigh, Double scoreHighToLow, Double coverageLowToHigh, Double coverageHighToLow,
                       boolean recommendationEligible, String legality, String miss, String policy) {}

    public record ScalePoint(int users, int legalEdges, Long fullPoolMs, Long engineRecallMs, Long coarseMs,
                             Long scoreP50Ms, Long scoreP95Ms, Long scoreP50Us, Long scoreP95Us,
                             Long cpuMs, Long heapSampleBytes,
                             String dataRead, String vector, String note) {}

    public record TieReport(String policy, int weeks, int uniqueExposed, int maxAppearances, int strongTruncated,
                            int sameFeatureExposed, long exposureKey, boolean reproducible) {}

    private RecallRankStudy() {}

    public static Run run(RecallRankFixture.Cohort cohort, Scheme scheme, Budgets budgets) {
        long started = System.nanoTime();
        Prepared prepared = prepare(cohort);
        Ranked ranked = rank(prepared, scheme, budgets, 1L);
        int strong = 0;
        int covered = 0;
        int recommended = 0;
        int insufficient = 0;
        for (Placement placement : ranked.placements) {
            if (placement.label() != RecallRankFixture.Label.STRONG) continue;
            strong++;
            if (placement.miss() == Miss.COVERED) covered++;
        }
        for (Scored row : ranked.retained) {
            if (row.decision != null && row.decision.recommendationEligible()) recommended++;
            else if (row.decision != null && row.decision.pair().hardFilterResult().isEmpty()) insufficient++;
        }
        return new Run(cohort.split(), scheme, budgets, ranked.placements, strong, covered, recommended, insufficient,
                ranked.hardRejected, ranked.safetyViolations, ranked.tiePairs, ranked.fused,
                (System.nanoTime() - started) / 1_000_000L, ranked.quality);
    }

    public static List<Edge> graph(RecallRankFixture.Cohort cohort) {
        Prepared prepared = prepare(cohort);
        Ranked ranked = rank(prepared, Scheme.F_PREFILTER, WIDE, 1L);
        Map<Long, Scored> byId = new LinkedHashMap<>();
        for (Scored row : ranked.scored) byId.put(row.id, row);
        List<Edge> edges = new ArrayList<>();
        long seeker = cohort.seeker().profile().userId();
        for (RecallRankFixture.Person person : cohort.people()) {
            if (person.profile().userId() == seeker) continue;
            Scored row = byId.get(person.profile().userId());
            long low = Math.min(seeker, person.profile().userId());
            long high = Math.max(seeker, person.profile().userId());
            CompatibilitySubject lowPerson = low == seeker ? cohort.seeker().profile() : person.profile();
            CompatibilitySubject highPerson = high == seeker ? cohort.seeker().profile() : person.profile();
            CompatibilityDecision decision = row == null ? null : row.decision;
            PairCompatibilityResult pair = decision == null ? null : decision.pair();
            boolean lowIsChooserAb = pair != null && pair.userA() == low;
            edges.add(new Edge(low, high, lowPerson.profileVersion(), highPerson.profileVersion(),
                    lowPerson.generation(), highPerson.generation(),
                    row == null ? "" : row.routes,
                    row == null ? null : row.recallRank,
                    row == null ? null : row.coarseRank,
                    row == null ? null : row.finalRank,
                    pair == null ? null : lowIsChooserAb ? pair.scoreAB() : pair.scoreBA(),
                    pair == null ? null : lowIsChooserAb ? pair.scoreBA() : pair.scoreAB(),
                    pair == null ? null : lowIsChooserAb ? pair.coverageAB() : pair.coverageBA(),
                    pair == null ? null : lowIsChooserAb ? pair.coverageBA() : pair.coverageAB(),
                    decision != null && decision.recommendationEligible(),
                    legality(person, decision),
                    decision == null ? legality(person, null) : row.miss == null ? "NOT_SCORED" : row.miss.name(),
                    RecallRankFixture.POLICY));
        }
        return List.copyOf(edges);
    }

    public static List<TieReport> ties(RecallRankFixture.Cohort cohort) {
        Prepared prepared = prepare(cohort);
        List<TieReport> out = new ArrayList<>();
        out.add(tie(prepared, "userId", false));
        out.add(tie(prepared, "seeded-hash", false));
        out.add(tie(prepared, "week-hash", false));
        out.add(tie(prepared, "mutual-coverage", false));
        out.add(tie(prepared, "controlled-explore", true));
        return out;
    }

    public static List<ScalePoint> scale() {
        List<ScalePoint> out = new ArrayList<>();
        Double perEdge = null;
        for (int size : SCALE_SIZES) {
            long edges = (long) size * (size - 1) / 2;
            if (perEdge != null && perEdge * edges > SCALE_WALL_MS) {
                out.add(new ScalePoint(size, (int) Math.min(Integer.MAX_VALUE, edges), null, null, null, null, null, null, null, null, null,
                        "not-run", "NOT_RUN", "skipped: measured edge time would pass the 90s cap"));
                continue;
            }
            List<CompatibilitySubject> people = scalePeople(size);
            CompatibilityContext context = CompatibilityContext.experiment(
                    BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW, people);
            BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();
            long cpuStart = cpuNanos();
            long readStart = System.nanoTime();
            List<Candidate> candidates = new ArrayList<>();
            for (CompatibilitySubject person : people) candidates.add(person.candidate());
            MatchingInput input = MatchingInput.of(BidirectionalCompatibilityEngine.WEEK, candidates, 1);
            long readMs = (System.nanoTime() - readStart) / 1_000_000L;
            long wideStart = System.nanoTime();
            RecallSubject seeker = scaleRecall(people.get(0));
            List<RecallSubject> pool = new ArrayList<>();
            for (CompatibilitySubject person : people) pool.add(scaleRecall(person));
            V4RecallEngine.recall(seeker, pool, input, BidirectionalCompatibilityEngine.NOW, 50,
                    V4RecallEngine.through(RecallRoute.R5), 1);
            long wideMs = (System.nanoTime() - wideStart) / 1_000_000L;
            List<Long> samples = new ArrayList<>();
            long scoreStart = System.nanoTime();
            int scored = 0;
            long deadline = scoreStart + SCALE_WALL_MS * 1_000_000L;
            boolean stopped = false;
            for (int i = 0; i < people.size() && !stopped; i++) {
                for (int j = i + 1; j < people.size(); j++) {
                    if (System.nanoTime() > deadline) {
                        stopped = true;
                        break;
                    }
                    long one = System.nanoTime();
                    engine.decide(people.get(i), people.get(j), context);
                    samples.add((System.nanoTime() - one) / 1_000L);
                    scored++;
                }
            }
            long scoreMs = (System.nanoTime() - scoreStart) / 1_000_000L;
            long cpuMs = Math.max(0, (cpuNanos() - cpuStart) / 1_000_000L);
            long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (!stopped && scored > 0) perEdge = scoreMs / (double) scored;
            String note = stopped
                    ? "stopped at the 90s cap after " + scored + " edges"
                    : "completed; recall is one seeker through V4RecallEngine with route cap 50 and fused budget 50";
            long p50Us = percentile(samples, 0.50);
            long p95Us = percentile(samples, 0.95);
            out.add(new ScalePoint(size, scored, stopped ? null : scoreMs, wideMs, scoreMs,
                    p50Us / 1_000L, p95Us / 1_000L, p50Us, p95Us, cpuMs, heap,
                    "in-memory " + readMs + "ms", "NOT_RUN", note));
            if (stopped) break;
        }
        return out;
    }

    private static TieReport tie(Prepared prepared, String policy, boolean explore) {
        Map<Long, Integer> appearances = new LinkedHashMap<>();
        List<Long> first = null;
        int strongTruncated = 0;
        for (int week = 0; week < 4; week++) {
            long seed = 100L + week;
            List<Long> kept = select(prepared, policy, seed, explore);
            if (week == 0) first = kept;
            Set<Long> keptIds = Set.copyOf(kept);
            for (Long id : kept) appearances.merge(id, 1, Integer::sum);
            for (RecallRankFixture.Person person : prepared.cohort.people()) {
                if (person.label() != RecallRankFixture.Label.STRONG || person.profile().userId() == prepared.seekerId) continue;
                if (!keptIds.contains(person.profile().userId())) strongTruncated++;
            }
        }
        List<Long> again = select(prepared, policy, 100L, explore);
        boolean same = first != null && first.equals(again);
        int max = appearances.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int sameFeature = 0;
        long key = 1L;
        List<Long> exposed = new ArrayList<>(appearances.keySet());
        exposed.sort(Long::compare);
        for (Long id : exposed) {
            key = key * 131L + id;
            if (prepared.labels.get(id) == RecallRankFixture.Label.DECOY) sameFeature++;
        }
        return new TieReport(policy, 4, appearances.size(), max, strongTruncated, sameFeature, key, same);
    }

    /**
     * Equal route scores are the tie. {@link RrfFusion} still breaks a residual fused-score tie by lower userId.
     * A fixed seed stays on seed 100. A week seed uses the cycle. Coverage reads the full profile, not the recall map.
     */
    private static List<Long> select(Prepared prepared, String policy, long seed, boolean explore) {
        List<RrfFusion.Fused> fused = fuseWithPolicy(prepared, explore ? "userId" : policy, seed);
        List<Long> kept = new ArrayList<>();
        int slots = explore ? 16 : 20;
        for (int i = 0; i < Math.min(slots, fused.size()); i++) kept.add(fused.get(i).userId());
        if (explore) {
            List<RrfFusion.Fused> rest = new ArrayList<>();
            for (int i = slots; i < fused.size(); i++) rest.add(fused.get(i));
            rest.sort(Comparator.comparingLong(row -> V4RecallEngine.mix(seed, prepared.seekerId, row.userId())));
            for (int i = 0; i < Math.min(4, rest.size()); i++) kept.add(rest.get(i).userId());
        }
        return kept;
    }

    private static List<RrfFusion.Fused> fuseWithPolicy(Prepared prepared, String policy, long seed) {
        RecallSubject seeker = prepared.cohort.seeker().recall();
        List<List<RrfFusion.Ranked>> routes = new ArrayList<>();
        for (RecallRoute route : List.of(RecallRoute.R1, RecallRoute.R2, RecallRoute.R3, RecallRoute.R4, RecallRoute.R5)) {
            List<long[]> order = new ArrayList<>();
            for (RecallSubject other : prepared.legalRecall) {
                double score = scoreOf(route, seeker, other, seed);
                if (score > 0) order.add(new long[]{other.userId(), Double.doubleToLongBits(score)});
            }
            order.sort(routeTie(policy, seed, prepared));
            List<RrfFusion.Ranked> ranked = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) {
                ranked.add(new RrfFusion.Ranked(order.get(i)[0], i + 1, route.code()));
            }
            routes.add(ranked);
        }
        return RrfFusion.merge(routes, V4RecallEngine.WEIGHTS, V4RecallEngine.RRF_K);
    }

    private static Comparator<long[]> routeTie(String policy, long seed, Prepared prepared) {
        Comparator<long[]> score = Comparator.comparingDouble((long[] row) -> Double.longBitsToDouble(row[1])).reversed();
        return switch (policy) {
            case "seeded-hash" -> score.thenComparingLong(row -> V4RecallEngine.mix(100L, prepared.seekerId, row[0]));
            case "week-hash" -> score.thenComparingLong(row -> V4RecallEngine.mix(seed, prepared.seekerId, row[0]));
            case "mutual-coverage" -> score
                    .thenComparing(Comparator.comparingInt((long[] row) -> fullHits(prepared, row[0])).reversed())
                    .thenComparingLong(row -> row[0]);
            default -> score.thenComparingLong(row -> row[0]);
        };
    }

    private static int fullHits(Prepared prepared, long candidateId) {
        CompatibilitySubject seeker = prepared.seekerProfile;
        CompatibilitySubject other = prepared.byProfile.get(candidateId);
        if (other == null) return 0;
        return directionHits(seeker, other) + directionHits(other, seeker);
    }

    private static int directionHits(CompatibilitySubject chooser, CompatibilitySubject target) {
        int hits = 0;
        for (var preference : chooser.preference().targets()) {
            String code = V4RecallEngine.selfCode(preference.featureCode());
            if (code == null || !(preference.targetValue() instanceof cn.aiditto.domain.relationship.FeatureValue.EnumOne wanted)) continue;
            for (var feature : target.self().features()) {
                if (code.equals(feature.featureCode())
                        && feature.value() instanceof cn.aiditto.domain.relationship.FeatureValue.EnumOne actual
                        && wanted.value().equals(actual.value())) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private record Scored(long id, RecallRankFixture.Label label, double rrf, String routes, int recallRank,
                          Integer coarseRank, Integer finalRank, Miss miss, CompatibilityDecision decision) {}

    private record Ranked(List<Scored> scored, List<Scored> retained, List<Placement> placements,
                          int hardRejected, int safetyViolations, int tiePairs, int fused, Quality quality) {}

    private record Prepared(RecallRankFixture.Cohort cohort, long seekerId, CompatibilitySubject seekerProfile,
                            CompatibilityContext context, BidirectionalCompatibilityEngine engine,
                            List<RecallSubject> legalRecall, Map<Long, CompatibilitySubject> byProfile,
                            Map<Long, RecallRankFixture.Label> labels, List<Scored> rrf, int hardRejected) {}

    private static Prepared prepare(RecallRankFixture.Cohort cohort) {
        List<CompatibilitySubject> profiles = new ArrayList<>();
        for (RecallRankFixture.Person person : cohort.people()) profiles.add(person.profile());
        CompatibilityContext context = CompatibilityContext.experiment(
                BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW, profiles, cohort.pastPairs(), Set.of());
        List<RecallSubject> pool = new ArrayList<>();
        Map<Long, CompatibilitySubject> byProfile = new LinkedHashMap<>();
        Map<Long, RecallRankFixture.Label> labels = new LinkedHashMap<>();
        for (RecallRankFixture.Person person : cohort.people()) {
            pool.add(person.recall());
            byProfile.put(person.profile().userId(), person.profile());
            labels.put(person.profile().userId(), person.label());
        }
        List<Candidate> candidates = new ArrayList<>();
        for (RecallSubject subject : pool) candidates.add(subject.candidate());
        MatchingInput input = MatchingInput.of(BidirectionalCompatibilityEngine.WEEK, candidates, 1).withPastPairs(cohort.pastPairs());
        V4RecallEngine.Result safety = V4RecallEngine.recall(cohort.seeker().recall(), pool, input,
                BidirectionalCompatibilityEngine.NOW, 50, V4RecallEngine.through(RecallRoute.R5), 1);
        List<RecallSubject> legalRecall = legal(cohort, input);
        return new Prepared(cohort, cohort.seeker().profile().userId(), cohort.seeker().profile(), context,
                BidirectionalCompatibilityEngine.v1(), legalRecall, byProfile, labels,
                baseline(cohort.seeker().recall(), legalRecall), safety.rejected().size());
    }

    private static List<Scored> baseline(RecallSubject seeker, List<RecallSubject> legal) {
        List<List<RrfFusion.Ranked>> routes = new ArrayList<>();
        for (RecallRoute route : List.of(RecallRoute.R1, RecallRoute.R2, RecallRoute.R3, RecallRoute.R4, RecallRoute.R5)) {
            List<long[]> order = new ArrayList<>();
            for (RecallSubject other : legal) {
                double score = scoreOf(route, seeker, other, 1L);
                if (score > 0) order.add(new long[]{other.userId(), Double.doubleToLongBits(score)});
            }
            order.sort(Comparator.comparingDouble((long[] row) -> Double.longBitsToDouble(row[1])).reversed().thenComparingLong(row -> row[0]));
            List<RrfFusion.Ranked> kept = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) kept.add(new RrfFusion.Ranked(order.get(i)[0], i + 1, route.code()));
            routes.add(kept);
        }
        List<Scored> out = new ArrayList<>();
        for (RrfFusion.Fused row : RrfFusion.merge(routes, V4RecallEngine.WEIGHTS, V4RecallEngine.RRF_K)) {
            out.add(new Scored(row.userId(), null, row.score(), String.join("+", row.sources()), 0, null, null, null, null));
        }
        return out;
    }

    private static List<RecallSubject> legal(RecallRankFixture.Cohort cohort, MatchingInput input) {
        List<RecallSubject> legal = new ArrayList<>();
        RecallSubject seeker = cohort.seeker().recall();
        for (RecallRankFixture.Person person : cohort.people()) {
            RecallSubject other = person.recall();
            if (other.userId() == seeker.userId() || !other.searchable()) continue;
            if (input.pastPairs().contains(PairKey.of(seeker.userId(), other.userId()))) continue;
            if (!cn.aiditto.domain.matching.HardFilter.passes(seeker.candidate(), other.candidate(), input)) continue;
            legal.add(other);
        }
        return legal;
    }

    private static Ranked rank(Prepared prepared, Scheme scheme, Budgets budgets, long seed) {
        Map<Long, List<V4RecallEngine.RouteHit>> detail = new LinkedHashMap<>();
        List<List<RrfFusion.Ranked>> routes = new ArrayList<>();
        for (RecallRoute route : List.of(RecallRoute.R1, RecallRoute.R2, RecallRoute.R3, RecallRoute.R4, RecallRoute.R5)) {
            List<long[]> order = routeOrder(prepared, route, seed);
            List<RrfFusion.Ranked> kept = new ArrayList<>();
            for (int i = 0; i < Math.min(budgets.route(), order.size()); i++) {
                long id = order.get(i)[0];
                kept.add(new RrfFusion.Ranked(id, i + 1, route.code()));
                detail.computeIfAbsent(id, ignored -> new ArrayList<>())
                        .add(new V4RecallEngine.RouteHit(route.code(), i + 1, Double.longBitsToDouble(order.get(i)[1])));
            }
            routes.add(kept);
        }
        List<RrfFusion.Fused> fused = RrfFusion.merge(routes, V4RecallEngine.WEIGHTS, V4RecallEngine.RRF_K);
        int ties = 0;
        for (int i = 1; i < fused.size(); i++) if (Double.compare(fused.get(i).score(), fused.get(i - 1).score()) == 0) ties++;
        Map<Long, Integer> signalRank = signalRanks(prepared, seed);
        List<Long> coarseIds = coarseIds(prepared, scheme, budgets, fused);
        Map<Long, CompatibilityDecision> decisions = new LinkedHashMap<>();
        for (Long id : coarseIds) {
            decisions.put(id, prepared.engine.decide(prepared.seekerProfile, prepared.byProfile.get(id), prepared.context));
        }
        List<Long> ordered = new ArrayList<>(coarseIds);
        if (scheme != Scheme.A_RRF) {
            ordered.sort(Comparator
                    .comparing((Long id) -> decisions.get(id).recommendationEligible() ? 0 : 1)
                    .thenComparing(id -> schemeScore(scheme, decisions.get(id)), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparingLong(id -> id));
        }
        List<Long> retainedIds = ordered.subList(0, Math.min(budgets.fin(), ordered.size()));
        Set<Long> retained = Set.copyOf(retainedIds);
        Set<Long> coarse = Set.copyOf(coarseIds);
        Set<Long> fusedIds = new java.util.LinkedHashSet<>();
        for (int i = 0; i < Math.min(budgets.fusion(), fused.size()); i++) fusedIds.add(fused.get(i).userId());
        Map<Long, Integer> fusedRank = new LinkedHashMap<>();
        for (int i = 0; i < fused.size(); i++) fusedRank.put(fused.get(i).userId(), i + 1);
        Map<Long, Integer> finalRank = new LinkedHashMap<>();
        for (int i = 0; i < retainedIds.size(); i++) finalRank.put(retainedIds.get(i), i + 1);
        Map<Long, Integer> coarseRank = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) coarseRank.put(ordered.get(i), i + 1);
        List<Placement> placements = new ArrayList<>();
        List<Scored> scored = new ArrayList<>();
        List<Scored> retainedRows = new ArrayList<>();
        int safety = 0;
        for (RecallRankFixture.Person person : prepared.cohort.people()) {
            long id = person.profile().userId();
            if (id == prepared.seekerId) continue;
            Miss stage = miss(id, signalRank, fusedRank, fusedIds, coarse, retained, budgets);
            if (person.label() == RecallRankFixture.Label.STRONG) {
                placements.add(new Placement(id, person.label(), stage, fusedRank.getOrDefault(id, 0), finalRank.getOrDefault(id, 0)));
            }
            if (retained.contains(id) && (person.label() == RecallRankFixture.Label.HARD_AGE
                    || person.label() == RecallRankFixture.Label.PAST_PAIR
                    || person.label() == RecallRankFixture.Label.WITHDRAWN
                    || person.label() == RecallRankFixture.Label.STALE)) {
                safety++;
            }
            double rrf = fusedRank.containsKey(id) ? fused.get(fusedRank.get(id) - 1).score() : 0;
            Scored row = new Scored(id, person.label(), rrf, routesOf(detail.get(id)), fusedRank.getOrDefault(id, 0),
                    coarseRank.get(id), finalRank.get(id), stage, decisions.get(id));
            scored.add(row);
            if (retained.contains(id)) retainedRows.add(row);
        }
        return new Ranked(scored, retainedRows, placements, prepared.hardRejected, safety, ties, fused.size(),
                quality(prepared, ordered, retainedIds, placements, scored));
    }

    private static Quality quality(Prepared prepared, List<Long> ordered, List<Long> retainedIds,
                                   List<Placement> placements, List<Scored> scored) {
        int fused10 = 0;
        int fused20 = 0;
        int fused50 = 0;
        int lostRecall = 0;
        int lostCoarse = 0;
        Map<Miss, Integer> stages = new LinkedHashMap<>();
        for (Placement placement : placements) {
            stages.merge(placement.miss(), 1, Integer::sum);
            int rank = placement.recallRank();
            if (rank >= 1 && rank <= 10) fused10++;
            if (rank >= 1 && rank <= 20) fused20++;
            if (rank >= 1 && rank <= 50) fused50++;
            switch (placement.miss()) {
                case NO_ROUTE_SIGNAL, ROUTE_BUDGET, FUSION_TRUNCATED -> lostRecall++;
                case COARSE_INPUT_CUT, COARSE_RANK_CUT -> lostCoarse++;
                case COVERED -> { }
            }
        }
        int scheme10 = 0;
        int scheme20 = 0;
        int scheme50 = 0;
        int first = Integer.MAX_VALUE;
        int strong20 = 0;
        int decoy20 = 0;
        int other20 = 0;
        int slots = Math.min(20, ordered.size());
        double dcg = 0;
        int idealCount = 0;
        for (RecallRankFixture.Person person : prepared.cohort.people()) {
            if (person.label() == RecallRankFixture.Label.STRONG && person.profile().userId() != prepared.seekerId) idealCount++;
        }
        for (int i = 0; i < ordered.size(); i++) {
            RecallRankFixture.Label label = prepared.labels.get(ordered.get(i));
            if (label == RecallRankFixture.Label.STRONG) {
                if (i < 10) scheme10++;
                if (i < 20) scheme20++;
                if (i < 50) scheme50++;
                if (i + 1 < first) first = i + 1;
                if (i < 20) dcg += 3.0 / (Math.log(i + 2) / Math.log(2));
            }
            if (i < slots) {
                if (label == RecallRankFixture.Label.STRONG) strong20++;
                else if (label == RecallRankFixture.Label.DECOY) decoy20++;
                else other20++;
            }
        }
        double idcg = 0;
        for (int i = 0; i < Math.min(20, idealCount); i++) idcg += 3.0 / (Math.log(i + 2) / Math.log(2));
        int auth = 0;
        int stale = 0;
        int hard = 0;
        Set<Long> retained = Set.copyOf(retainedIds);
        for (RecallRankFixture.Person person : prepared.cohort.people()) {
            if (!retained.contains(person.profile().userId())) continue;
            switch (person.label()) {
                case WITHDRAWN -> auth++;
                case STALE -> stale++;
                case HARD_AGE, PAST_PAIR -> hard++;
                default -> { }
            }
        }
        int poolEligible = 0;
        int poolInsufficient = 0;
        int scoredEdges = 0;
        for (Scored row : scored) {
            if (row.decision == null) continue;
            scoredEdges++;
            if (row.decision.recommendationEligible()) poolEligible++;
            else if (row.decision.pair().hardFilterResult().isEmpty()) poolInsufficient++;
        }
        StringBuilder stageText = new StringBuilder();
        for (Map.Entry<Miss, Integer> entry : stages.entrySet()) {
            if (!stageText.isEmpty()) stageText.append(',');
            stageText.append(entry.getKey().name()).append('=').append(entry.getValue());
        }
        return new Quality(fused10, fused20, fused50, scheme10, scheme20, scheme50, lostRecall, lostCoarse,
                strong20, decoy20, other20, slots, idcg == 0 ? 0 : dcg / idcg,
                first == Integer.MAX_VALUE ? 0 : 1.0 / first, retainedIds.isEmpty() ? 1 : 0,
                auth, stale, hard, prepared.legalRecall.size(), scoredEdges, poolEligible, poolInsufficient,
                stageText.toString());
    }

    private static Miss miss(long id, Map<Long, Integer> signalRank, Map<Long, Integer> fusedRank, Set<Long> fusedIds,
                             Set<Long> coarse, Set<Long> retained, Budgets budgets) {
        if (retained.contains(id)) return Miss.COVERED;
        if (!signalRank.containsKey(id)) return Miss.NO_ROUTE_SIGNAL;
        if (signalRank.get(id) > budgets.route()) return Miss.ROUTE_BUDGET;
        if (!fusedIds.contains(id)) return Miss.FUSION_TRUNCATED;
        if (!coarse.contains(id)) return Miss.COARSE_INPUT_CUT;
        return Miss.COARSE_RANK_CUT;
    }

    private static List<Long> coarseIds(Prepared prepared, Scheme scheme, Budgets budgets, List<RrfFusion.Fused> fused) {
        if (scheme == Scheme.E_FULL_POOL) {
            List<Long> ids = new ArrayList<>();
            for (RecallSubject subject : prepared.legalRecall) ids.add(subject.userId());
            return ids;
        }
        if (scheme == Scheme.F_PREFILTER) {
            List<RecallSubject> copy = new ArrayList<>(prepared.legalRecall);
            copy.sort(Comparator.comparingInt((RecallSubject subject) -> fullHits(prepared, subject.userId())).reversed()
                    .thenComparingLong(RecallSubject::userId));
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(budgets.coarse(), copy.size()); i++) ids.add(copy.get(i).userId());
            return ids;
        }
        List<Long> ids = new ArrayList<>();
        int limit = Math.min(budgets.coarse(), Math.min(budgets.fusion(), fused.size()));
        for (int i = 0; i < limit; i++) ids.add(fused.get(i).userId());
        return ids;
    }

    private static Double schemeScore(Scheme scheme, CompatibilityDecision decision) {
        PairCompatibilityResult pair = decision.pair();
        return switch (scheme) {
            case B_FORWARD -> pair.scoreAB();
            case C_BIDIRECTIONAL -> pair.minDirection();
            case D_COVERAGE, E_FULL_POOL, F_PREFILTER -> pair.harmonicWithCoverage();
            case A_RRF -> pair.harmonicWithCoverage();
        };
    }

    private static Map<Long, Integer> signalRanks(Prepared prepared, long seed) {
        Map<Long, Integer> best = new LinkedHashMap<>();
        for (RecallRoute route : List.of(RecallRoute.R1, RecallRoute.R2, RecallRoute.R3, RecallRoute.R4, RecallRoute.R5)) {
            List<long[]> ranked = routeOrder(prepared, route, seed);
            for (int i = 0; i < ranked.size(); i++) {
                long id = ranked.get(i)[0];
                best.merge(id, i + 1, Math::min);
            }
        }
        return best;
    }

    private static String legality(RecallRankFixture.Person person, CompatibilityDecision decision) {
        if (decision != null) return decision.pair().hardFilterResult().isEmpty() ? "LEGAL" : "HARD_BLOCK";
        return switch (person.label()) {
            case WITHDRAWN -> "PROFILE_WITHDRAWN";
            case STALE -> "PROFILE_STALE";
            case HARD_AGE -> "HARD_AGE";
            case PAST_PAIR -> "HARD_HISTORY";
            default -> "NOT_SCORED";
        };
    }

    private static List<long[]> routeOrder(Prepared prepared, RecallRoute route, long seed) {
        RecallSubject seeker = prepared.cohort.seeker().recall();
        List<long[]> scored = new ArrayList<>();
        for (RecallSubject other : prepared.legalRecall) {
            double score = scoreOf(route, seeker, other, seed);
            if (score > 0) scored.add(new long[]{other.userId(), Double.doubleToLongBits(score)});
        }
        scored.sort(Comparator.comparingDouble((long[] row) -> Double.longBitsToDouble(row[1])).reversed().thenComparingLong(row -> row[0]));
        return scored;
    }

    private static double scoreOf(RecallRoute route, RecallSubject seeker, RecallSubject other, long seed) {
        return switch (route) {
            case R1 -> V4RecallEngine.hobbies(seeker, other);
            case R2 -> V4RecallEngine.selfOverlap(seeker, other);
            case R3 -> V4RecallEngine.preferenceHits(seeker.preferenceEnums(), other.selfEnums());
            case R4 -> V4RecallEngine.preferenceHits(other.preferenceEnums(), seeker.selfEnums());
            case R5 -> V4RecallEngine.complement(seeker, other);
            case R6 -> 0;
        };
    }

    private static String routesOf(List<V4RecallEngine.RouteHit> hits) {
        if (hits == null || hits.isEmpty()) return "";
        List<String> codes = new ArrayList<>();
        for (V4RecallEngine.RouteHit hit : hits) codes.add(hit.route());
        return String.join("+", codes);
    }

    private static List<CompatibilitySubject> scalePeople(int size) {
        List<CompatibilitySubject> people = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            people.add(CompatibilitySubject.synthetic(50_000L + i,
                    List.of(CompatibilitySubject.feature("self.pace", new cn.aiditto.domain.relationship.FeatureValue.EnumOne(i % 2 == 0 ? "slow" : "fast"),
                            cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus.CONFIRMED)),
                    List.of(CompatibilitySubject.preference("partner.pace", "slow", cn.aiditto.domain.relationship.RelationshipTypes.Importance.SUPER)),
                    Candidate.builder(50_000L + i).hobbies(i % 2 == 0 ? "reading" : "gym").build()));
        }
        return people;
    }

    private static RecallSubject scaleRecall(CompatibilitySubject subject) {
        String pace = subject.userId() % 2 == 0 ? "slow" : "fast";
        return new RecallSubject(subject.userId(), "scale", 1, true, false, RecallRankFixture.SOURCE, subject.candidate(),
                CompatibilityContext.eligibleApplicant(subject.candidate()),
                CompatibilityContext.granted(subject.userId(), BidirectionalCompatibilityEngine.NOW),
                Map.of("self.pace", pace), Set.of(subject.userId() % 2 == 0 ? "reading" : "gym"),
                Map.of("partner.pace", "slow"), 1, RecallIngress.SYNTHETIC_FIXTURE);
    }

    private static long cpuNanos() {
        var bean = ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : 0L;
    }

    private static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) return 0;
        List<Long> copy = new ArrayList<>(values);
        copy.sort(Long::compare);
        int index = (int) Math.ceil(p * copy.size()) - 1;
        return copy.get(Math.max(0, Math.min(copy.size() - 1, index)));
    }

    public static String line(Run run) {
        Quality quality = run.quality;
        return "split=" + run.split + " scheme=" + run.scheme + " route=" + run.budgets.route
                + " fusion=" + run.budgets.fusion + " coarse=" + run.budgets.coarse + " final=" + run.budgets.fin
                + " strong=" + run.strongCovered + "/" + run.strong
                + " fusedRecall=" + quality.fused10 + "/" + run.strong + "," + quality.fused20 + "/" + run.strong + "," + quality.fused50 + "/" + run.strong
                + " schemeRecall=" + quality.scheme10 + "/" + run.strong + "," + quality.scheme20 + "/" + run.strong + "," + quality.scheme50 + "/" + run.strong
                + " lostRecall=" + quality.lostToRecall + "/" + run.strong
                + " lostCoarse=" + quality.lostToCoarse + "/" + run.strong
                + " precision=" + quality.strongAt20 + "/" + quality.slots20
                + " decoy=" + quality.decoyAt20 + "/" + quality.slots20
                + " other=" + quality.otherAt20 + "/" + quality.slots20
                + " ndcg20=" + String.format(java.util.Locale.ROOT, "%.4f", quality.ndcg20)
                + " mrr=" + String.format(java.util.Locale.ROOT, "%.4f", quality.mrr)
                + " zero=" + quality.zeroQueries + "/1"
                + " retainedEligible=" + run.recommended + "/" + (run.recommended + run.insufficient)
                + " scoredEligible=" + quality.poolEligible + "/" + quality.scored
                + " poolInsufficient=" + quality.poolInsufficient + "/" + quality.scored
                + " legal=" + quality.legal
                + " recommended=" + run.recommended + " insufficient=" + run.insufficient
                + " hardRejected=" + run.hardRejected + " safety=" + run.safetyViolations
                + " auth=" + quality.authHits + " stale=" + quality.staleHits + " hardHits=" + quality.hardHits
                + " ties=" + run.tiePairs + "/" + Math.max(0, run.fused - 1)
                + " ms=" + run.elapsedMs
                + " stages=" + quality.stages;
    }
}
