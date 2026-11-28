package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.CompatibilityDecision;
import cn.aiditto.domain.compatibility.v4.CompatibilityPolicies;
import cn.aiditto.domain.compatibility.v4.FeatureMatchContribution;
import cn.aiditto.domain.compatibility.v4.PairCompatibilityResult;
import cn.aiditto.domain.feedback.v4.strategy.StrategyDataset;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.Cohort;
import cn.aiditto.domain.reasoning.v4.DirectionalReasoning;
import cn.aiditto.domain.reasoning.v4.ReasoningClaim;
import cn.aiditto.domain.reasoning.v4.ReasoningDataset;
import cn.aiditto.domain.reasoning.v4.ReasoningExperiment;
import cn.aiditto.domain.reasoning.v4.ReasoningPrompt;
import cn.aiditto.domain.reasoning.v4.RuleEchoModel;
import cn.aiditto.domain.recall.v4.RecallRankFixture;
import cn.aiditto.domain.recall.v4.RecallRankStudy;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.ValueType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Match Lab. Profiles stay in this object. Nothing here reads a production user table,
 * calls a model, or writes a match.
 */
public final class LabWorkbench {
    public static final int MAX_PROFILES = 24;
    public static final String SOURCE = "synthetic-fixture:match-lab";
    public static final String MODE = "SYNTHETIC_SANDBOX";

    private static final String[] SELF = {"self.pace", "self.communication", "self.social.initiative", "self.schedule"};
    private static final String[] PREF = {"partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule"};

    private final FeatureRegistry registry = FeatureRegistry.v1();
    private final BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();
    private final Map<Long, Stored> profiles = new LinkedHashMap<>();
    private final Map<String, LabRun> runs = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(71000);

    public LabWorkbench() {
        seed("甲·双向", ideal(), ideal());
        seed("乙·双向", ideal(), ideal());
        seed("丙·单向", ideal(), ideal());
        seed("丁·反向偏好", ideal(), new String[]{"slow", "frequent", "high", "late"});
        seed("戊·证据不足", new String[]{"slow", null, null, null}, ideal());
        seed("己·硬约束", ideal(), ideal());
        Stored veto = profiles.get(71006L);
        Candidate older = veto.subject.candidate().toBuilder().birthday(java.time.LocalDate.of(1980, 1, 1)).ageRange(40, 60).build();
        profiles.put(71006L, new Stored(veto.name, veto.version, rebuild(veto.subject, older), veto.writes));
    }

    public List<CatalogEntry> catalog() {
        return List.of(
                new CatalogEntry("f03-synthetic-1", "F03", "群体观察", "128 条已发布合成匹配", StrategyDataset.VERSION,
                        "FROZEN_RESULT", "FROZEN_EXPERIMENT", "PASS_OFFLINE", "真实校准 NOT_RUN",
                        List.of("128 条观察", "开发集 96", "保留集 32")),
                new CatalogEntry("m02-b5-cases", "M02", "双向评分案例", "八个虚构故事，分数来自冻结引擎输出", "m02-b5",
                        "FROZEN_RESULT", "FROZEN_EXPERIMENT", "PASS_OFFLINE", "不是喜欢概率",
                        List.of("8 个案例")),
                new CatalogEntry("m01-rank-dev", "M01", "召回排序开发集", "冻结人群。页面上的对照会在本次进程里重新调用引擎", "recall-rank-1",
                        "FROZEN_RESULT", "FROZEN_EXPERIMENT", "PASS_OFFLINE", "不外推到生产用户",
                        List.of("策略 E", "开发集与保留集分开")),
                new CatalogEntry("m03-rule-echo", "M03", "关系推理解释", "确定性夹具回放。没有 LongCat、DeepSeek、混元或千问的逐样本输出",
                        ReasoningPrompt.VERSION, "MODEL_REPLAY", "FROZEN_EXPERIMENT", "REAL_MODEL_NO_GO",
                        "C1 与 C2 加分关闭", List.of("规则回声夹具")),
                new CatalogEntry("production-gate", "PROD", "生产启用", "生产匹配、生产画像和生产发布保持关闭", "none",
                        "ILLUSTRATIVE", "SYNTHETIC_SANDBOX", "NO_GO", "真实关系质量 NOT_RUN",
                        List.of("生产启用 NO-GO")));
    }

    public List<FeatureDefinition> registry() {
        return List.copyOf(registry.all());
    }

    public List<StoredView> profiles() {
        List<StoredView> out = new ArrayList<>();
        for (var entry : profiles.entrySet()) out.add(view(entry.getKey(), entry.getValue()));
        return List.copyOf(out);
    }

    public StoredView profile(long userId) {
        return view(userId, require(userId));
    }

    public StoredView create(String name, List<FeatureWrite> self, List<FeatureWrite> preferences) {
        if (profiles.size() >= MAX_PROFILES) throw new LabRejection("SANDBOX_LIMIT");
        long id = sequence.incrementAndGet();
        while (profiles.containsKey(id)) id = sequence.incrementAndGet();
        Stored stored = store(id, name, 1, self, preferences);
        profiles.put(id, stored);
        return view(id, stored);
    }

    public StoredView update(long userId, String name, List<FeatureWrite> self, List<FeatureWrite> preferences) {
        Stored current = require(userId);
        Stored stored = store(userId, name == null || name.isBlank() ? current.name : name, current.version + 1, self, preferences);
        profiles.put(userId, stored);
        return view(userId, stored);
    }

    public List<FeatureDiff> diff(long leftId, long rightId) {
        StoredView left = profile(leftId);
        StoredView right = profile(rightId);
        Map<String, LabFeature> rightByCode = new LinkedHashMap<>();
        for (LabFeature feature : right.features()) rightByCode.put(feature.featureCode() + ":" + feature.section(), feature);
        List<FeatureDiff> out = new ArrayList<>();
        for (LabFeature feature : left.features()) {
            LabFeature other = rightByCode.remove(feature.featureCode() + ":" + feature.section());
            String after = other == null ? "" : other.value();
            if (!feature.value().equals(after) || (other != null && !feature.status().equals(other.status()))) {
                out.add(new FeatureDiff(feature.featureCode(), feature.section(), feature.value(), after));
            }
        }
        for (LabFeature feature : rightByCode.values()) {
            out.add(new FeatureDiff(feature.featureCode(), feature.section(), "", feature.value()));
        }
        return List.copyOf(out);
    }

    public ScoreView score(long leftId, long rightId, String commitSha) {
        Stored left = require(leftId);
        Stored right = require(rightId);
        if (leftId == rightId) throw new LabRejection("SAME_USER");
        CompatibilityContext context = CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK,
                BidirectionalCompatibilityEngine.NOW, List.of(left.subject, right.subject));
        CompatibilityDecision decision = engine.decide(left.subject, right.subject, context);
        String runId = remember(run("BIDIRECTIONAL_SCORE", "match-lab-sandbox", hash(left.digest() + "|" + right.digest()),
                CompatibilityPolicies.VERSION, commitSha, "ENGINE_EXECUTED", "COMPLETED",
                decision.pair().harmonicWithCoverage() == null ? "undefined" : Double.toString(decision.pair().harmonicWithCoverage()),
                null, "分数变化只说明算法行为。它不是真实关系效果。"));
        return scoreView(runId, leftId, rightId, left, right, decision);
    }

    public RecallComparison recall(String commitSha) {
        RecallRankFixture.Cohort cohort = RecallRankFixture.dev();
        if (cohort.people().size() > 80) throw new LabRejection("SANDBOX_LIMIT");
        List<SchemeResult> schemes = new ArrayList<>();
        for (SchemeRequest request : List.of(
                new SchemeRequest("RRF", RecallRankStudy.Scheme.A_RRF),
                new SchemeRequest("RRF_M02", RecallRankStudy.Scheme.C_BIDIRECTIONAL),
                new SchemeRequest("FULL_POOL_M02", RecallRankStudy.Scheme.E_FULL_POOL),
                new SchemeRequest("SHORTLIST_M02", RecallRankStudy.Scheme.F_PREFILTER))) {
            schemes.add(oneScheme(cohort, request));
        }
        String runId = remember(run("CANDIDATE_RECALL", "recall-rank-dev", hash(cohort.split()),
                RecallRankFixture.POLICY, commitSha, "ENGINE_EXECUTED", "COMPLETED",
                schemes.size() + " schemes", null,
                "冻结开发集上的本次引擎执行。不是生产召回，也没有跑 10000 人规模点。"));
        return new RecallComparison(runId, "ENGINE_EXECUTED", cohort.split(), cohort.people().size(), schemes);
    }

    public ReasoningView reasoning(String commitSha) {
        ReasoningDataset.Relation relation = ReasoningDataset.all().stream()
                .filter(item -> item.kind() == ReasoningDataset.Kind.MUTUAL && "dev".equals(item.split()) && item.copy() == 0)
                .findFirst().orElseThrow();
        ReasoningExperiment experiment = ReasoningExperiment.v1();
        ReasoningExperiment.PairResult result = experiment.reason(relation.seeker(), relation.candidate(),
                CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW,
                        List.of(relation.seeker(), relation.candidate())),
                new RuleEchoModel(), cn.aiditto.domain.reasoning.v4.ReasoningBudget.experiment());
        Double m02 = result.decision().pair().harmonicWithCoverage();
        String runId = remember(run("REASONING_REPLAY", ReasoningPrompt.VERSION, relation.id(),
                ReasoningExperiment.POLICY, commitSha, "MODEL_REPLAY", "COMPLETED",
                m02 == null ? "undefined" : Double.toString(m02), null,
                "确定性夹具回放。C1 与 C2 不加分。没有真实模型输出。"));
        return new ReasoningView(runId, "MODEL_REPLAY", RuleEchoModel.NAME, "NOT_RUN", ReasoningPrompt.VERSION,
                relation.kind().name(), relation.note(), m02, false,
                side(result.agentA()), side(result.agentB()), result.agentA().status().name(), result.agentB().status().name());
    }

    public List<LabRun> runs() {
        return List.copyOf(runs.values());
    }

    private SchemeResult oneScheme(RecallRankFixture.Cohort cohort, SchemeRequest request) {
        RecallRankStudy.Run run = RecallRankStudy.run(cohort, request.scheme(), RecallRankStudy.NARROW);
        Map<Long, RecallRankFixture.Person> people = new LinkedHashMap<>();
        for (RecallRankFixture.Person person : cohort.people()) people.put(person.profile().userId(), person);
        CompatibilityContext context = CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK,
                BidirectionalCompatibilityEngine.NOW, cohort.people().stream().map(RecallRankFixture.Person::profile).toList(),
                cohort.pastPairs(), java.util.Set.of());
        List<RecallRow> rows = new ArrayList<>();
        for (RecallRankStudy.Placement placement : run.placements()) {
            if (placement.candidateId() == cohort.seeker().profile().userId()) continue;
            RecallRankFixture.Person person = people.get(placement.candidateId());
            CompatibilityDecision decision = null;
            if (person != null && placement.miss() == RecallRankStudy.Miss.COVERED) {
                decision = engine.decide(cohort.seeker().profile(), person.profile(), context);
            }
            PairCompatibilityResult pair = decision == null ? null : decision.pair();
            rows.add(new RecallRow(placement.candidateId(), person == null ? "" : person.label().name(),
                    placement.recallRank(), placement.finalRank(),
                    pair == null ? null : pair.scoreAB(), pair == null ? null : pair.scoreBA(),
                    pair == null ? null : pair.coverageAB(), pair == null ? null : pair.coverageBA(),
                    decision != null && decision.recommendationEligible(), placement.miss().name()));
            if (rows.size() >= 40) break;
        }
        return new SchemeResult(request.name(), run.recommended(), run.insufficient(), run.hardRejected(),
                run.safetyViolations(), rows);
    }

    private ScoreView scoreView(String runId, long leftId, long rightId, Stored left, Stored right, CompatibilityDecision decision) {
        PairCompatibilityResult pair = decision.pair();
        List<ScoreItem> items = new ArrayList<>();
        for (FeatureMatchContribution row : pair.evidenceContributions()) {
            items.add(new ScoreItem(row.direction(), row.preferenceCode(), row.targetFeatureCode(), row.importance(),
                    row.satisfaction().name(), row.satisfactionValue(), row.evidenceStatus(), row.reasonCode()));
        }
        return new ScoreView(runId, leftId, rightId, left.version, right.version, left.digest(), right.digest(),
                pair.scoreAB(), pair.scoreBA(), pair.coverageAB(), pair.coverageBA(), pair.harmonicWithCoverage(),
                decision.recommendationEligible(), decision.reasons(), pair.formulaVersion(), decision.policyVersion(),
                pair.hardFilterResult(), items, "ENGINE_EXECUTED");
    }

    private static AgentSide side(DirectionalReasoning reasoning) {
        return new AgentSide(claims(reasoning.supportedMatches()), claims(reasoning.potentialComplements()),
                claims(reasoning.possibleConflicts()), claims(reasoning.missingInformation()),
                claims(reasoning.unsupportedClaims()), reasoning.evidenceRefs());
    }

    private static List<String> claims(List<ReasoningClaim> claims) {
        List<String> out = new ArrayList<>();
        for (ReasoningClaim claim : claims) out.add(claim.preferenceCode() + " → " + claim.targetFeatureCode() + "：" + claim.statement());
        return out;
    }

    private Stored store(long id, String name, long version, List<FeatureWrite> self, List<FeatureWrite> preferences) {
        String clean = cleanName(name);
        List<ProfileFeature> features = new ArrayList<>();
        List<PreferenceTarget> targets = new ArrayList<>();
        List<FeatureWrite> writes = new ArrayList<>();
        for (FeatureWrite write : self) {
            features.add(selfFeature(write));
            writes.add(write);
        }
        for (FeatureWrite write : preferences) {
            targets.add(preference(write));
            writes.add(write);
        }
        Candidate candidate = Candidate.builder(id).cohort(Cohort.DEMO).build();
        cn.aiditto.domain.compatibility.v4.CompatibilitySubject subject = cn.aiditto.domain.compatibility.v4.CompatibilitySubject.synthetic(
                id, version, 1, true, false, SOURCE, features, targets, candidate);
        return new Stored(clean, version, subject, writes);
    }

    private ProfileFeature selfFeature(FeatureWrite write) {
        FeatureDefinition definition = requireFeature(write.code(), Section.SELF);
        FeatureStatus status = status(write.status());
        FeatureValue value = status == FeatureStatus.CONTESTED ? null : value(definition, write.value());
        registry.checkValue(definition, value == null ? new FeatureValue.Unbounded() : value);
        if (value != null) registry.checkValue(definition, value);
        return cn.aiditto.domain.compatibility.v4.CompatibilitySubject.feature(definition.featureCode(), value, status);
    }

    private PreferenceTarget preference(FeatureWrite write) {
        FeatureDefinition definition = requireFeature(write.code(), Section.PREFERENCE);
        FeatureStatus status = status(write.status());
        Importance importance = importance(write.importance());
        Operator operator = operator(write.operator());
        FeatureValue value = status == FeatureStatus.CONTESTED ? null : value(definition, write.value());
        if (value != null) registry.checkValue(definition, value);
        return cn.aiditto.domain.compatibility.v4.CompatibilitySubject.preference(definition.featureCode(), operator, value,
                importance, ConstraintType.SOFT, status);
    }

    private FeatureDefinition requireFeature(String code, Section section) {
        if (code == null || code.isBlank() || code.indexOf(' ') >= 0 || code.indexOf('/') >= 0) {
            throw new LabRejection("FEATURE_CODE");
        }
        FeatureDefinition definition;
        try {
            definition = registry.require(code);
        } catch (IllegalArgumentException failure) {
            throw new LabRejection("FEATURE_CODE");
        }
        if (!definition.sections().contains(section)) throw new LabRejection("FEATURE_SECTION");
        return definition;
    }

    private FeatureValue value(FeatureDefinition definition, String raw) {
        if (raw == null || raw.isBlank()) throw new LabRejection("FEATURE_VALUE");
        if (raw.contains("://") || raw.contains("..") || raw.contains("'") || raw.contains(";")) {
            throw new LabRejection("FEATURE_VALUE");
        }
        if (definition.valueType() == ValueType.ENUM) {
            if (!definition.allowedValues().contains(raw)) throw new LabRejection("FEATURE_VALUE");
            return new FeatureValue.EnumOne(raw);
        }
        if (definition.valueType() == ValueType.ENUM_SET) {
            List<String> values = List.of(raw.split(","));
            if (!definition.allowedValues().containsAll(values)) throw new LabRejection("FEATURE_VALUE");
            return new FeatureValue.EnumMany(values);
        }
        throw new LabRejection("FEATURE_VALUE");
    }

    private static FeatureStatus status(String raw) {
        if (raw == null || raw.isBlank()) return FeatureStatus.CONFIRMED;
        try {
            return FeatureStatus.valueOf(raw);
        } catch (IllegalArgumentException failure) {
            throw new LabRejection("FEATURE_STATUS");
        }
    }

    private static Importance importance(String raw) {
        if (raw == null || raw.isBlank()) return Importance.SUPER;
        try {
            return Importance.valueOf(raw);
        } catch (IllegalArgumentException failure) {
            throw new LabRejection("FEATURE_VALUE");
        }
    }

    private static Operator operator(String raw) {
        if (raw == null || raw.isBlank()) return Operator.EQ;
        try {
            return Operator.valueOf(raw);
        } catch (IllegalArgumentException failure) {
            throw new LabRejection("FEATURE_VALUE");
        }
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank() || name.length() > 40) throw new LabRejection("NAME");
        if (name.contains("/") || name.contains("\\") || name.contains("://") || name.contains("..")) {
            throw new LabRejection("NAME");
        }
        return name;
    }

    private void seed(String name, String[] self, String[] preference) {
        long id = sequence.incrementAndGet();
        profiles.put(id, store(id, name, 1, writes(SELF, self, "CONFIRMED", null), writes(PREF, preference, "CONFIRMED", "SUPER")));
    }

    private static List<FeatureWrite> writes(String[] codes, String[] values, String status, String importance) {
        List<FeatureWrite> out = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            if (values[i] == null) continue;
            out.add(new FeatureWrite(codes[i], values[i], status, importance, "EQ"));
        }
        return out;
    }

    private static String[] ideal() { return new String[]{"slow", "quiet", "low", "early"}; }

    private Stored require(long userId) {
        Stored stored = profiles.get(userId);
        if (stored == null) throw new LabRejection("PROFILE");
        return stored;
    }

    private StoredView view(long userId, Stored stored) {
        List<LabFeature> features = new ArrayList<>();
        for (ProfileFeature feature : stored.subject.self().features()) features.add(describe(feature));
        for (PreferenceTarget target : stored.subject.preference().targets()) features.add(describe(target));
        return new StoredView(userId, stored.name, stored.version, stored.digest(), SOURCE, MODE, features);
    }

    private LabFeature describe(ProfileFeature feature) {
        FeatureDefinition definition = registry.require(feature.featureCode());
        String value = feature.value() == null ? "" : text(feature.value());
        boolean usable = feature.status() == FeatureStatus.CONFIRMED && feature.value() != null;
        return new LabFeature(feature.featureCode(), definition.displayName(), section(definition, Section.SELF),
                value, "", "", EvidenceSource.SELF_REPORTED.name(), feature.status().name(), usable);
    }

    private LabFeature describe(PreferenceTarget target) {
        FeatureDefinition definition = registry.require(target.featureCode());
        String value = target.targetValue() == null ? "" : text(target.targetValue());
        boolean usable = target.status() == FeatureStatus.CONFIRMED && target.targetValue() != null;
        return new LabFeature(target.featureCode(), definition.displayName(), section(definition, Section.PREFERENCE),
                value, target.operator() == null ? "" : target.operator().name(),
                target.importance() == null ? "" : target.importance().name(),
                EvidenceSource.SELF_REPORTED.name(), target.status().name(), usable);
    }

    private static String section(FeatureDefinition definition, Section preferred) {
        if (definition.sections().contains(preferred)) return preferred.name();
        return definition.sections().iterator().next().name();
    }

    private static String text(FeatureValue value) {
        if (value instanceof FeatureValue.EnumOne one) return one.value();
        if (value instanceof FeatureValue.EnumMany many) return String.join(",", many.values());
        return value.toString();
    }

    private cn.aiditto.domain.compatibility.v4.CompatibilitySubject rebuild(
            cn.aiditto.domain.compatibility.v4.CompatibilitySubject subject, Candidate candidate) {
        return new cn.aiditto.domain.compatibility.v4.CompatibilitySubject(subject.userId(), subject.profileVersion(),
                subject.generation(), subject.current(), subject.withdrawn(), subject.fixtureSource(),
                subject.self(), subject.preference(), subject.selfSupport(), subject.preferenceSupport(), candidate);
    }

    private String remember(LabRun run) {
        runs.put(run.runId(), run);
        return run.runId();
    }

    private LabRun run(String type, String dataset, String inputHash, String policy, String commitSha, String mode,
                       String status, String result, String error, String limitations) {
        Instant now = Instant.now();
        String id = "run-" + sequence.incrementAndGet();
        return new LabRun(id, type, dataset, inputHash, policy, commitSha == null ? "UNSPECIFIED" : commitSha,
                mode, status, now, now, result, error, limitations);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception failure) {
            throw new LabRejection("RUN");
        }
    }

    private record Stored(String name, long version, cn.aiditto.domain.compatibility.v4.CompatibilitySubject subject,
                          List<FeatureWrite> writes) {
        private String digest() {
            StringBuilder text = new StringBuilder();
            List<FeatureWrite> sorted = new ArrayList<>(writes);
            sorted.sort(Comparator.comparing(FeatureWrite::code));
            for (FeatureWrite write : sorted) {
                text.append(write.code()).append('=').append(write.value()).append(':').append(write.status()).append('\n');
            }
            text.append("v=").append(version);
            return hash(text.toString());
        }
    }

    private record SchemeRequest(String name, RecallRankStudy.Scheme scheme) {}

    public record FeatureWrite(String code, String value, String status, String importance, String operator) {}

    public record CatalogEntry(String experimentId, String module, String title, String description, String datasetVersion,
                               String sourceType, String executionMode, String qualityStatus, String limitations,
                               List<String> metrics) {}

    public record LabFeature(String featureCode, String displayName, String section, String value, String operator,
                             String importance, String evidenceSource, String status, boolean usableForMatching) {}

    public record StoredView(long userId, String name, long version, String digest, String fixtureSource, String mode,
                             List<LabFeature> features) {}

    public record FeatureDiff(String featureCode, String section, String left, String right) {}

    public record ScoreItem(String direction, String preferenceCode, String targetFeatureCode, String importance,
                            String satisfaction, Double score, String evidenceStatus, String reasonCode) {}

    public record ScoreView(String runId, long userA, long userB, long versionA, long versionB, String digestA, String digestB,
                            Double scoreAB, Double scoreBA, Double coverageAB, Double coverageBA, Double aggregate,
                            boolean recommendationEligible, List<String> reasons, String formulaVersion, String policyVersion,
                            List<String> hardFilter, List<ScoreItem> items, String executionMode) {}

    public record RecallRow(long userId, String label, int recallRank, int finalRank, Double scoreAB, Double scoreBA,
                            Double coverageAB, Double coverageBA, boolean recommendationEligible, String miss) {}

    public record SchemeResult(String scheme, int recommended, int insufficient, int hardRejected, int safetyViolations,
                               List<RecallRow> rows) {}

    public record RecallComparison(String runId, String executionMode, String dataset, int people, List<SchemeResult> schemes) {}

    public record AgentSide(List<String> supported, List<String> complements, List<String> conflicts, List<String> unknown,
                            List<String> rejected, List<String> evidence) {}

    public record ReasoningView(String runId, String executionMode, String modelName, String realModelStatus,
                                String promptVersion, String kind, String note, Double m02Score, boolean aiAdjustment,
                                AgentSide agentA, AgentSide agentB, String statusA, String statusB) {}

    public record LabRun(String runId, String experimentType, String datasetVersion, String inputHash, String policyVersion,
                         String commitSha, String executionMode, String status, Instant startedAt, Instant completedAt,
                         String result, String error, String limitations) {}

    public static final class LabRejection extends RuntimeException {
        private final String code;

        public LabRejection(String code) {
            super(code);
            this.code = code;
        }

        public String code() { return code; }
    }
}
