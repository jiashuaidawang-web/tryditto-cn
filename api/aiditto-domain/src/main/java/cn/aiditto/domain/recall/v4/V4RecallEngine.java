package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.matching.EligibilityGate;
import cn.aiditto.domain.matching.HardFilter;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.recall.RrfFusion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline multi-route recall. It reuses {@link RrfFusion}, {@link EligibilityGate}, and {@link HardFilter}.
 * It is not called by the production week runner and it does not score a final match.
 */
public final class V4RecallEngine {
    public static final double RRF_K = 60.0;
    public static final int ROUTE_TOP_K = 50;
    public static final int EXPLORE_TOP_K = 10;
    public static final Map<String, Double> WEIGHTS = Map.of(
            "R1", 1.0,
            "R2", 0.8,
            "R3", 1.0,
            "R4", 1.0,
            "R5", 0.7,
            "R6", 0.3);

    public record RouteHit(String route, int rank, double rawScore) {}

    public record Hit(
            long userId,
            long candidateId,
            String recallRoute,
            int routeRank,
            double rawRouteScore,
            int fusedRank,
            double fusedScore,
            long sourceProfileVersion,
            String eligibilityResult,
            String recallReasonCode,
            List<RouteHit> routes) {
        public Hit {
            routes = List.copyOf(routes);
        }
    }

    public record Rejection(long candidateId, List<String> reasonCodes) {
        public Rejection {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

    public record Result(List<Hit> hits, List<Rejection> rejected, int legalCount,
                         int hardViolations, int expiredReturned, int withdrawnReturned) {
        public Result {
            hits = List.copyOf(hits);
            rejected = List.copyOf(rejected);
        }
    }

    private record Scored(long id, double score) {}

    private V4RecallEngine() {}

    public static Result recall(RecallSubject seeker, List<RecallSubject> pool, MatchingInput input,
                                Instant now, int budget, Set<RecallRoute> routes, long exploreSeed) {
        Map<Long, RecallSubject> byId = new LinkedHashMap<>();
        for (RecallSubject subject : pool) byId.put(subject.userId(), subject);
        List<Rejection> rejected = new ArrayList<>();
        if (!seeker.searchable() || !individualOk(seeker, input.weekStart(), now)) {
            return new Result(List.of(), rejected, 0, 0, 0, 0);
        }
        List<RecallSubject> legal = new ArrayList<>();
        for (RecallSubject other : pool) {
            if (other.userId() == seeker.userId()) continue;
            if (!other.searchable()) {
                rejected.add(new Rejection(other.userId(), List.of(other.withdrawn() ? "PROFILE_WITHDRAWN" : "PROFILE_STALE")));
                continue;
            }
            EligibilityGate.Result gate = gate(seeker, other, input, now);
            if (!gate.eligible()) {
                rejected.add(new Rejection(other.userId(), gate.reasonCodes()));
                continue;
            }
            legal.add(other);
        }
        Map<Long, List<RouteHit>> detail = new LinkedHashMap<>();
        List<List<RrfFusion.Ranked>> rankedRoutes = new ArrayList<>();
        for (RecallRoute route : RecallRoute.values()) {
            if (!routes.contains(route)) continue;
            int limit = route == RecallRoute.R6 ? EXPLORE_TOP_K : ROUTE_TOP_K;
            List<Scored> scored = rank(route, seeker, legal, exploreSeed);
            List<RrfFusion.Ranked> ranked = new ArrayList<>();
            int kept = Math.min(limit, scored.size());
            for (int i = 0; i < kept; i++) {
                Scored item = scored.get(i);
                int rank = i + 1;
                ranked.add(new RrfFusion.Ranked(item.id(), rank, route.code()));
                detail.computeIfAbsent(item.id(), ignored -> new ArrayList<>())
                        .add(new RouteHit(route.code(), rank, item.score()));
            }
            rankedRoutes.add(ranked);
        }
        List<RrfFusion.Fused> fused = routes.isEmpty()
                ? baseline(seeker, legal, exploreSeed)
                : RrfFusion.merge(rankedRoutes, WEIGHTS, RRF_K);
        List<Hit> hits = new ArrayList<>();
        int violations = 0;
        int expired = 0;
        int withdrawn = 0;
        int limit = Math.min(budget, fused.size());
        for (int i = 0; i < limit; i++) {
            RrfFusion.Fused row = fused.get(i);
            RecallSubject other = byId.get(row.userId());
            if (other == null || !HardFilter.passes(seeker.candidate(), other.candidate(), input)) {
                violations++;
                continue;
            }
            if (!other.current()) expired++;
            if (other.withdrawn()) withdrawn++;
            List<RouteHit> routesHit = detail.getOrDefault(row.userId(), List.of());
            RouteHit best = routesHit.stream().min(Comparator.comparingInt(RouteHit::rank)).orElse(null);
            String reason = routesHit.isEmpty() ? "B0_LEGAL_POOL" : routesHit.size() == 1 ? routesHit.get(0).route() : "FUSED";
            hits.add(new Hit(
                    seeker.userId(),
                    row.userId(),
                    routesHit.isEmpty() ? "B0" : String.join("+", row.sources()),
                    best == null ? i + 1 : best.rank(),
                    best == null ? 0 : best.rawScore(),
                    i + 1,
                    row.score(),
                    other.profileVersion(),
                    "PASS",
                    reason,
                    routesHit));
        }
        return new Result(hits, rejected, legal.size(), violations, expired, withdrawn);
    }

    public static Set<RecallRoute> through(RecallRoute last) {
        EnumSet<RecallRoute> set = EnumSet.noneOf(RecallRoute.class);
        for (RecallRoute route : RecallRoute.values()) {
            set.add(route);
            if (route == last) break;
        }
        return set;
    }

    private static List<RrfFusion.Fused> baseline(RecallSubject seeker, List<RecallSubject> legal, long seed) {
        List<RecallSubject> copy = new ArrayList<>(legal);
        copy.sort(Comparator
                .comparingLong((RecallSubject item) -> mix(seed, seeker.userId(), item.userId()))
                .thenComparingLong(RecallSubject::userId));
        List<RrfFusion.Fused> out = new ArrayList<>();
        for (RecallSubject item : copy) out.add(new RrfFusion.Fused(item.userId(), 0, List.of("B0")));
        return out;
    }

    private static List<Scored> rank(RecallRoute route, RecallSubject seeker, List<RecallSubject> legal, long seed) {
        List<Scored> scored = new ArrayList<>();
        for (RecallSubject other : legal) {
            double score = switch (route) {
                case R1 -> hobbies(seeker, other);
                case R2 -> selfOverlap(seeker, other);
                case R3 -> preferenceHits(seeker.preferenceEnums(), other.selfEnums());
                case R4 -> preferenceHits(other.preferenceEnums(), seeker.selfEnums());
                case R5 -> complement(seeker, other);
                case R6 -> explore(seed, seeker.userId(), other.userId());
            };
            if (score > 0) scored.add(new Scored(other.userId(), score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparingLong(Scored::id));
        return scored;
    }

    static double hobbies(RecallSubject left, RecallSubject right) {
        int shared = 0;
        for (String hobby : left.hobbies()) if (right.hobbies().contains(hobby)) shared++;
        return shared;
    }

    static double selfOverlap(RecallSubject left, RecallSubject right) {
        int shared = 0;
        for (Map.Entry<String, String> entry : left.selfEnums().entrySet()) {
            if (!entry.getKey().startsWith("self.")) continue;
            String other = right.selfEnums().get(entry.getKey());
            if (entry.getValue().equals(other)) shared++;
        }
        return shared;
    }

    static double preferenceHits(Map<String, String> preference, Map<String, String> self) {
        int hits = 0;
        for (Map.Entry<String, String> entry : preference.entrySet()) {
            String selfCode = selfCode(entry.getKey());
            if (selfCode == null) continue;
            if (entry.getValue().equals(self.get(selfCode))) hits++;
        }
        return hits;
    }

    static double complement(RecallSubject seeker, RecallSubject other) {
        int hits = 0;
        for (ComplementRule rule : ComplementRule.RULES) {
            if (rule.preferenceValue().equals(seeker.preferenceEnums().get(rule.preferenceCode()))
                    && rule.selfValue().equals(other.selfEnums().get(rule.selfCode()))) {
                hits++;
            }
        }
        return hits;
    }

    static String selfCode(String preferenceCode) {
        if (preferenceCode == null || !preferenceCode.startsWith("partner.")) return null;
        return "self." + preferenceCode.substring("partner.".length());
    }

    private static double explore(long seed, long seeker, long candidate) {
        return (mix(seed, seeker, candidate) >>> 1) / (double) Long.MAX_VALUE + 1e-12;
    }

    static long mix(long seed, long seeker, long candidate) {
        long value = seed * 0x9E3779B97F4A7C15L ^ (seeker << 1) ^ candidate;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return value;
    }

    private static boolean individualOk(RecallSubject subject, java.time.LocalDate week, Instant now) {
        return PoolGate.check(subject.applicant(), week, now).isEmpty()
                && ConsentPolicy.allows(subject.consent());
    }

    /**
     * Same account, consent, and pair rules as {@link EligibilityGate}, without its publish exception.
     * That exception drops the pair being published from the past-pair set. Recall must keep the block.
     */
    private static EligibilityGate.Result gate(RecallSubject left, RecallSubject right, MatchingInput input, Instant now) {
        java.util.Set<String> codes = new java.util.TreeSet<>();
        PoolGate.check(left.applicant(), input.weekStart(), now).ifPresent(reason -> codes.add("POOL_" + reason.name()));
        PoolGate.check(right.applicant(), input.weekStart(), now).ifPresent(reason -> codes.add("POOL_" + reason.name()));
        if (!ConsentPolicy.allows(left.consent()) || !ConsentPolicy.allows(right.consent())) {
            boolean revoked = left.consent().consentStatus() == cn.aiditto.domain.consent.ConsentStatus.REVOKED
                    || right.consent().consentStatus() == cn.aiditto.domain.consent.ConsentStatus.REVOKED;
            codes.add(revoked ? "CONSENT_REVOKED" : "CONSENT_UNKNOWN");
        }
        for (cn.aiditto.domain.matching.HardRule rule : HardFilter.violations(left.candidate(), right.candidate(), input)) {
            codes.add("HARD_" + rule.name());
        }
        java.util.List<String> list = java.util.List.copyOf(codes);
        return new EligibilityGate.Result(list.isEmpty(), list, now, EligibilityGate.POLICY_VERSION);
    }

    public record ComplementRule(String preferenceCode, String preferenceValue, String selfCode, String selfValue) {
        static final List<ComplementRule> RULES = List.of(
                new ComplementRule("partner.support", "close", "self.communication", "frequent"),
                new ComplementRule("partner.communication", "direct", "self.social.initiative", "high"));
    }
}
