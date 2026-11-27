package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.recall.v4.RecallIngress;
import cn.aiditto.domain.recall.v4.RecallRoute;
import cn.aiditto.domain.recall.v4.RecallSubject;
import cn.aiditto.domain.recall.v4.V4RecallEngine;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Separates a missing candidate from a candidate that was scored in the wrong order.
 * Recall uses the frozen M01 routes and weights. The full profile is scored only after a candidate is in the pool.
 */
public final class CompatibilityRecallStudy {
    public static final Instant NOW = Instant.parse("2026-11-27T06:00:00Z");
    private static final String SOURCE = "synthetic-fixture:v4-m02-recall-split";
    private static final String[] IDEAL = {"slow", "quiet", "low", "early"};
    private static final String[] SELF = {"self.pace", "self.communication", "self.social.initiative", "self.schedule"};
    private static final String[] PREF = {"partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule"};

    public record Report(int preferred, int recalledAt10, int recalledAt20, int recalledAt50, int scoredInFullPool,
                         int top20PreferredScored, int fullPoolPreferredRankedFirst, String note) {
        public String ceiling(int recalled) { return recalled + "/" + preferred; }
    }

    private CompatibilityRecallStudy() {}

    public static Report run() {
        CompatibilitySubject seeker = profile(900, IDEAL, IDEAL, "reading");
        List<CompatibilitySubject> preferred = new ArrayList<>();
        List<RecallSubject> pool = new ArrayList<>();
        pool.add(recall(seeker, Map.of("self.pace", "slow"), Map.of("partner.pace", "slow"), Set.of("reading")));
        for (int id = 1; id <= 45; id++) {
            CompatibilitySubject decoy = profile(id, new String[]{"fast", "frequent", "high", "late"},
                    new String[]{"fast", "frequent", "high", "late"}, "reading");
            pool.add(recall(decoy, Map.of("self.pace", "slow"), Map.of("partner.pace", "slow"), Set.of("reading")));
        }
        for (int id = 801; id <= 804; id++) {
            CompatibilitySubject match = profile(id, IDEAL, IDEAL, "reading");
            preferred.add(match);
            pool.add(recall(match, Map.of("self.pace", "slow"), Map.of("partner.pace", "slow"), Set.of("reading")));
        }
        for (int id = 805; id <= 808; id++) {
            CompatibilitySubject hidden = profile(id, IDEAL, IDEAL, "gym");
            preferred.add(hidden);
            pool.add(recall(hidden, Map.of(), Map.of(), Set.of("gym")));
        }
        List<Candidate> candidates = pool.stream().map(RecallSubject::candidate).toList();
        MatchingInput input = MatchingInput.of(BidirectionalCompatibilityEngine.WEEK, candidates, 1);
        RecallSubject seekerRecall = pool.get(0);
        Set<RecallRoute> routes = V4RecallEngine.through(RecallRoute.R5);
        V4RecallEngine.Result top50 = V4RecallEngine.recall(seekerRecall, pool, input, NOW, 50, routes, 1);
        int at10 = recalled(preferred, top50, 10);
        int at20 = recalled(preferred, top50, 20);
        int at50 = recalled(preferred, top50, 50);
        BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();
        int fullRankedFirst = 0;
        int scored = 0;
        for (CompatibilitySubject match : preferred) {
            PairCompatibilityResult result = engine.score(seeker, match);
            if (result.status() == CompatibilityStatus.SCORED && result.minDirection() != null && result.minDirection() >= 60) {
                scored++;
                fullRankedFirst++;
            }
        }
        int top20Scored = 0;
        Set<Long> top20Ids = top50.hits().stream().limit(20).map(V4RecallEngine.Hit::candidateId).collect(java.util.stream.Collectors.toSet());
        for (CompatibilitySubject match : preferred) {
            if (!top20Ids.contains(match.userId())) continue;
            PairCompatibilityResult result = engine.score(seeker, match);
            if (result.status() == CompatibilityStatus.SCORED) top20Scored++;
        }
        String note = "M01 挑战保留集 Recall@20 仍是 7/45，本实验没有重调它的权重。"
                + "这里的 Top-K 只说明：没进候选列表的人，评分阶段看不到。"
                + "召回 Map 相同的人按较小 userId 排序，这是 M01 已冻结的并列规则。";
        return new Report(preferred.size(), at10, at20, at50, scored, top20Scored, fullRankedFirst, note);
    }

    private static int recalled(List<CompatibilitySubject> preferred, V4RecallEngine.Result result, int k) {
        Set<Long> ids = result.hits().stream().limit(k).map(V4RecallEngine.Hit::candidateId).collect(java.util.stream.Collectors.toSet());
        int count = 0;
        for (CompatibilitySubject match : preferred) if (ids.contains(match.userId())) count++;
        return count;
    }

    private static CompatibilitySubject profile(long id, String[] self, String[] preference, String hobby) {
        List<ProfileFeature> features = new ArrayList<>();
        List<PreferenceTarget> targets = new ArrayList<>();
        for (int i = 0; i < SELF.length; i++) {
            features.add(CompatibilitySubject.feature(SELF[i], new FeatureValue.EnumOne(self[i]), FeatureStatus.CONFIRMED));
        }
        for (int i = 0; i < PREF.length; i++) {
            targets.add(CompatibilitySubject.preference(PREF[i], preference[i], Importance.SUPER));
        }
        return CompatibilitySubject.synthetic(id, 1, 1, true, false, SOURCE, features, targets,
                Candidate.builder(id).hobbies(hobby).build());
    }

    private static RecallSubject recall(CompatibilitySubject subject, Map<String, String> self, Map<String, String> preference, Set<String> hobbies) {
        PoolGate.Applicant applicant = new PoolGate.Applicant(subject.candidate().birthday(), true, true, false, 2, false, false, null, false);
        MatchingConsent consent = new MatchingConsent(subject.userId(), ConsentStatus.GRANTED, 1, NOW, null,
                ConsentPolicy.POLICY_VERSION, NOW);
        return new RecallSubject(subject.userId(), "m02", subject.profileVersion(), true, false, SOURCE,
                subject.candidate(), applicant, consent, self, hobbies, preference, subject.generation(),
                RecallIngress.SYNTHETIC_FIXTURE);
    }
}
