package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.compatibility.v4.CompatibilityPolicies;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackProjection;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.learning.ConfirmationSources;
import cn.aiditto.domain.feedback.v4.learning.ExplicitPreferenceCommand;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningEngine;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal;
import cn.aiditto.domain.matching.v4.GlobalCandidateGraphBuilder;
import cn.aiditto.domain.matching.v4.GlobalMatchFixture;
import cn.aiditto.domain.matching.v4.GlobalMatchingGraph;
import cn.aiditto.domain.matching.v4.GlobalMatchingInput;
import cn.aiditto.domain.matching.v4.GlobalMatchingResult;
import cn.aiditto.domain.matching.v4.Objective;
import cn.aiditto.domain.matching.v4.PreparedEdge;
import cn.aiditto.domain.matching.v4.V4GlobalMatchingEngine;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.matchlab.LabWorkbench.FeatureWrite;
import cn.aiditto.domain.matchlab.LabWorkbench.LabRejection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One workspace's fictional loop. It calls the existing M04, F01 and F02 types.
 * It does not write a production match, call WeekRunner, or replace a historical snapshot.
 */
public final class SandboxLoop {
    static final int MAX_PLANS = 48;
    static final int MAX_LEDGERS = 48;
    static final int MAX_EVENTS = 240;
    static final int MAX_PROPOSALS = 48;
    private static final String LIMIT = "分数变化只说明这一次沙箱计算变了。它不是真实关系质量提升。";
    private static final String CANDIDATE_STRATEGY = "recall-rank-1";
    private final LabWorkbench lab;
    private final ProfileLearningEngine learning = ProfileLearningEngine.v1();
    private final FeatureRegistry registry = FeatureRegistry.v1();
    private final Map<String, Held> plans = new LinkedHashMap<>();
    private final Map<String, FeedbackLedger> ledgers = new LinkedHashMap<>();
    private final Map<Long, List<ProfileEvidence>> evidence = new LinkedHashMap<>();
    private final Map<Long, ConfirmationSources> sources = new LinkedHashMap<>();
    private final Map<String, ProfileLearningProposal> proposals = new LinkedHashMap<>();
    private final Map<String, ProposalBinding> bindings = new LinkedHashMap<>();
    private final Map<Long, Long> evidenceAtVersion = new LinkedHashMap<>();
    private final AtomicLong matchIds = new AtomicLong(91000);
    private final AtomicLong commands = new AtomicLong(1);

    public SandboxLoop(LabWorkbench lab) {
        this.lab = lab;
    }

    public synchronized GlobalView solve(String objectiveName, List<Long> requested, String commitSha) {
        return solve(objectiveName, requested, commitSha, null, "ENGINE_EXECUTED");
    }

    public synchronized GlobalView compare(String commitSha) {
        GlobalView greedy = hold("G0", GlobalMatchFixture.localOptimum(), Objective.G0_GREEDY, commitSha, "MATH_COMPARE");
        GlobalView global = hold("G2", GlobalMatchFixture.localOptimum(), Objective.G2_CARDINALITY_THEN_WEIGHT, commitSha, "MATH_COMPARE");
        return global.withComparison(greedy);
    }

    public synchronized FeedbackView open(String runId, long userA, long userB) {
        Held held = requirePlan(runId);
        if (!usable(held.result())) throw new LabRejection("PLAN");
        long low = Math.min(userA, userB);
        long high = Math.max(userA, userB);
        GlobalMatchingResult.MatchedPair pair = held.result().pairs().stream()
                .filter(item -> item.lowId() == low && item.highId() == high)
                .findFirst().orElseThrow(() -> new LabRejection("PAIR"));
        String key = runId + ":" + low + ":" + high;
        FeedbackLedger existing = ledgers.get(key);
        if (existing != null) return feedbackView(key, existing, runId);
        if (ledgers.size() >= MAX_LEDGERS) throw new LabRejection("SANDBOX_LIMIT");
        PreparedEdge edge = held.graph().solver().stream()
                .filter(item -> item.lowId() == low && item.highId() == high)
                .findFirst().orElseThrow(() -> new LabRejection("PAIR"));
        GlobalMatchingInput input = held.graph().input();
        boolean consentA = "GRANTED".equals(input.consentSnapshot().get(low));
        boolean consentB = "GRANTED".equals(input.consentSnapshot().get(high));
        MatchFeedbackContext context = new MatchFeedbackContext(matchIds.incrementAndGet(), input.matchingRunId(),
                input.matchingPeriod(), low, high, true, edge.versionLow(), edge.versionHigh(),
                input.compatibilityPolicyVersion(), input.objectiveVersion(), input.candidateStrategyVersion(),
                consentA, consentB, edge.generationLow(), edge.generationHigh(),
                edge.generationLow(), edge.generationHigh(), consentA, consentB, MatchFeedbackContext.Origin.SYNTHETIC);
        FeedbackLedger ledger = new FeedbackLedger(context);
        ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, key + ".published", held.solvedAt()), held.solvedAt());
        ledgers.put(key, ledger);
        return feedbackView(key, ledger, runId);
    }

    public synchronized FeedbackView act(String ledgerKey, String action, long actorUserId, String supersedesEventId) {
        FeedbackLedger ledger = ledgers.get(ledgerKey);
        if (ledger == null) throw new LabRejection("LEDGER");
        FeedbackEventType type = actionType(action);
        if (type.system() && !"timeout".equals(action)) throw new LabRejection("SYSTEM_EVENT");
        long actor = type.system() ? 0 : actorUserId;
        if (!type.system() && !ledger.context().participant(actor)) throw new LabRejection("ACTOR");
        if (eventCount() >= MAX_EVENTS) throw new LabRejection("SANDBOX_LIMIT");
        String key = "lab." + ledgerKey + "." + action + "." + actor + "." + commands.incrementAndGet();
        Instant now = Instant.now();
        try {
            ledger.append(new FeedbackCommand(type, actor,
                    type.system() ? FeedbackFacts.SourceType.SYSTEM : FeedbackFacts.SourceType.PARTICIPANT,
                    key, now, blank(supersedesEventId), null, null), now);
        } catch (FeedbackRejection rejection) {
            if (!"DUPLICATE_EFFECT".equals(rejection.code())) throw rejection;
        }
        return feedbackView(ledgerKey, ledger, labRunId(ledgerKey));
    }

    public synchronized LearningView learningView(String ledgerKey, long userId) {
        FeedbackLedger ledger = ledgers.get(ledgerKey);
        if (ledger == null) throw new LabRejection("LEDGER");
        if (!ledger.context().participant(userId)) throw new LabRejection("ACTOR");
        List<SignalView> signals = new ArrayList<>();
        for (LearningSignal signal : learning.signals(ledger, userId)) {
            signals.add(new SignalView(signal.sourceEventId(), signal.category().name(),
                    signal.learnableTarget() == null ? "" : signal.learnableTarget(),
                    signal.evidenceState().name(), signal.authorization().name()));
        }
        return new LearningView(ledgerKey, userId, lab.profile(userId).version(), signals, List.of(), null,
                featureRows(userId), featureRows(userId), ledger.context().profileVersion(userId),
                "对方评价和未确认假设不进入本人事实。");
    }

    public synchronized LearningView propose(long userId, String featureCode, String newValue, String sourceEventId) {
        boolean direct = sourceEventId == null || sourceEventId.isBlank();
        return propose(userId, featureCode, newValue, sourceEventId, null, direct ? "DIRECT" : "FEEDBACK");
    }

    public synchronized LearningView propose(long userId, String featureCode, String newValue, String sourceEventId,
                                             String ledgerKey, String origin) {
        if (!workspaceUser(userId)) throw new LabRejection("PROFILE");
        if (proposals.size() >= MAX_PROPOSALS) throw new LabRejection("SANDBOX_LIMIT");
        String kind = origin == null || origin.isBlank() ? "DIRECT" : origin;
        String boundLedger = "";
        String boundEvent = "";
        List<String> sources = List.of();
        String note;
        if ("DIRECT".equals(kind)) {
            if (sourceEventId != null && !sourceEventId.isBlank()) throw new LabRejection("SOURCE");
            note = "这是用户明确选择的偏好，不是从接受事件推断出来的。提案还没有进入评分。";
        } else if ("FEEDBACK".equals(kind)) {
            RelationshipFeedbackEvent event = requireFeedbackSource(userId, ledgerKey, sourceEventId);
            if (event.eventType() != FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED) {
                throw new LabRejection("INSUFFICIENT_SEMANTICS");
            }
            boundLedger = ledgerKey;
            boundEvent = event.eventId();
            sources = List.of(event.eventId());
            note = "提案绑定这条偏好更正。接受事件本身不是一项具体伴侣偏好的证据。提案还没有进入评分。";
        } else {
            throw new LabRejection("SOURCE");
        }
        String current = currentValue(userId, featureCode);
        ExplicitPreferenceCommand command = command(userId, featureCode, current, newValue,
                ExplicitPreferenceCommand.Action.CONFIRM, sources, Instant.now(), consent(userId), consent(userId));
        ProfileLearningProposal proposal = learning.propose(command);
        proposals.put(proposal.proposalId(), proposal);
        bindings.put(proposal.proposalId(), new ProposalBinding(kind, boundLedger, boundEvent, userId));
        return new LearningView("", userId, lab.profile(userId).version(), List.of(), unlearnable(featureCode),
                proposalView(proposal), featureRows(userId), featureRows(userId), command.lifecycleGeneration(), note);
    }

    public synchronized LearningView decide(String proposalId, String action, long actorUserId) {
        ProfileLearningProposal proposal = proposals.get(proposalId);
        if (proposal == null) throw new LabRejection("PROPOSAL");
        if ("confirm".equals(action) && proposal.status() == ProfileLearningProposal.Status.CONFIRMED
                && proposal.userId() == actorUserId) {
            return new LearningView("", proposal.userId(), lab.profile(proposal.userId()).version(), List.of(),
                    unlearnable(proposal.featureCode()), proposalView(proposal), featureRows(proposal.userId()),
                    featureRows(proposal.userId()), proposal.lifecycleGeneration(), "这份提案已经确认，没有再写一版画像。");
        }
        ProposalBinding binding = bindings.get(proposalId);
        boolean blocked = binding != null && "FEEDBACK".equals(binding.origin()) && sourceBlocked(binding);
        boolean granted = consent(actorUserId);
        ExplicitPreferenceCommand.Action kind = "reject".equals(action) ? ExplicitPreferenceCommand.Action.REJECT
                : ExplicitPreferenceCommand.Action.CONFIRM;
        Instant when = "expire".equals(action) ? proposal.expiresAt().plusSeconds(1) : Instant.now();
        String commandId = proposal.proposalId().startsWith("prop.")
                ? proposal.proposalId().substring("prop.".length()) : "pref." + commands.incrementAndGet();
        List<String> sources = binding == null || binding.sourceEventId().isBlank() ? List.of() : List.of(binding.sourceEventId());
        ExplicitPreferenceCommand command = new ExplicitPreferenceCommand(commandId, actorUserId, proposal.featureCode(),
                proposal.oldValue(), proposal.proposedValue(), proposal.importance(), proposal.operator(), kind,
                proposal.profileVersion(), proposal.lifecycleGeneration(), generation(actorUserId), granted, granted, sources, when);
        long versionBefore = lab.profile(proposal.userId()).version();
        List<ProfileEvidence> before = evidenceFor(proposal.userId());
        List<FeatureRow> previous = featureRows(proposal.userId());
        ProfileLearningEngine.ProfileUpdate update = learning.apply(command, proposal, before,
                (int) versionBefore, proposal.lifecycleGeneration(), "",
                true, false, blocked, sourcesFor(proposal.userId()));
        proposals.put(proposalId, update.proposal());
        boolean wrote = update.snapshot() != null
                && (update.decision() == null || update.decision().kind() != ProfileVersionPolicy.Kind.KEEP_CURRENT);
        if (wrote) adopt(proposal.userId(), update.evidence());
        if (update.proposal() != null && update.proposal().status() == ProfileLearningProposal.Status.STALE_PROFILE) {
            throw new LabRejection("STALE");
        }
        if (blocked) throw new LabRejection("RETRACTED_SOURCE");
        return new LearningView("", proposal.userId(), lab.profile(proposal.userId()).version(), List.of(),
                unlearnable(proposal.featureCode()), proposalView(update.proposal()), previous,
                featureRows(proposal.userId()), proposal.lifecycleGeneration(),
                wrote ? "确认只改这一条证据，其余特征还在。分数变化不是真实关系质量。" : "没有写入新画像。");
    }

    public synchronized LearningView retract(long userId, String sourceRef) {
        if (!workspaceUser(userId)) throw new LabRejection("PROFILE");
        List<FeatureRow> previous = featureRows(userId);
        ProfileLearningEngine.ProfileUpdate update = learning.retractDerived(userId, evidenceFor(userId), sourceRef,
                (int) lab.profile(userId).version(), generation(userId), sourcesFor(userId));
        adopt(userId, update.evidence());
        return new LearningView("", userId, lab.profile(userId).version(), List.of(), List.of(), null,
                previous, featureRows(userId), generation(userId),
                "撤回的是这一条来源。没有把整份旧画像拷回来。");
    }

    public synchronized RematchView rematch(String parentRunId, String objectiveName, String commitSha) {
        Held parent = requirePlan(parentRunId);
        List<String> before = pairKeys(parent.result());
        GlobalView next = solve(objectiveName, parent.graph().input().eligibleUsers().stream().map(CompatibilitySubject::userId).toList(),
                commitSha, parentRunId, "ENGINE_EXECUTED");
        return new RematchView(parentRunId, next, before, next.pairs().stream().map(PairView::key).toList(), LIMIT);
    }

    public synchronized CaseView runCase(String code, String commitSha) {
        return switch (code) {
            case "A" -> caseA(commitSha);
            case "B" -> caseB(commitSha);
            case "C" -> caseC(commitSha);
            case "D" -> caseD(commitSha);
            case "E" -> caseE(commitSha);
            case "F" -> caseF(commitSha);
            default -> throw new LabRejection("CASE");
        };
    }

    private CaseView caseA(String commitSha) {
        GlobalView math = compare(commitSha);
        GlobalView live = solve("G2", List.of(), commitSha);
        boolean differ = !math.pairs().stream().map(PairView::key).toList()
                .equals(math.comparison().pairs().stream().map(PairView::key).toList());
        return new CaseView("A", "MATH_COMPARE", differ, math.runId(), live.runId(),
                "数学夹具上贪心和 G2 的配对不同。夹具结果不能进入反馈。沙箱求解是另一条记录。");
    }

    private CaseView caseB(String commitSha) {
        GlobalView live = solve("G2", List.of(), commitSha);
        PairView pair = firstPair(live);
        FeedbackView opened = open(live.runId(), pair.lowId(), pair.highId());
        act(opened.ledgerKey(), "accept", pair.lowId(), null);
        FeedbackView done = act(opened.ledgerKey(), "decline", pair.highId(), null);
        return new CaseView("B", "ENGINE_EXECUTED", !done.projection().mutualAcceptance(), live.runId(), opened.ledgerKey(),
                "一方接受、另一方拒绝。没有生成确定性偏好，超时也没有被写成拒绝。");
    }

    private CaseView caseC(String commitSha) {
        GlobalView live = solve("G2", List.of(), commitSha);
        PairView pair = firstPair(live);
        FeedbackView opened = open(live.runId(), pair.lowId(), pair.highId());
        act(opened.ledgerKey(), "accept", pair.lowId(), null);
        FeedbackView done = act(opened.ledgerKey(), "accept", pair.highId(), null);
        boolean derived = done.events().stream().anyMatch(event -> "MUTUAL_ACCEPTANCE_REACHED".equals(event.type()));
        return new CaseView("C", "ENGINE_EXECUTED", derived && done.projection().mutualAcceptance(), live.runId(), opened.ledgerKey(),
                "双方接受后的互相接受事件由账本派生，不是页面提交的。");
    }

    private CaseView caseD(String commitSha) {
        GlobalView live = solve("G2", List.of(), commitSha);
        PairView pair = firstPair(live);
        FeedbackView opened = open(live.runId(), pair.lowId(), pair.highId());
        act(opened.ledgerKey(), "accept", pair.lowId(), null);
        act(opened.ledgerKey(), "accept", pair.highId(), null);
        act(opened.ledgerKey(), "attended", pair.lowId(), null);
        FeedbackView done = act(opened.ledgerKey(), "missed", pair.highId(), null);
        return new CaseView("D", "ENGINE_EXECUTED", done.projection().contested(), live.runId(), opened.ledgerKey(),
                "双方见面报告相反。投影保持 CONTESTED，没有升成双方确认。");
    }

    private CaseView caseE(String commitSha) {
        GlobalView live = solve("G2", List.of(), commitSha);
        PairView pair = firstPair(live);
        long beforeVersion = lab.profile(pair.highId()).version();
        open(live.runId(), pair.lowId(), pair.highId());
        LearningView proposal = propose(pair.highId(), "partner.pace", "fast", "");
        LearningView confirmed = decide(proposal.proposal().proposalId(), "confirm", pair.highId());
        RematchView again = rematch(live.runId(), "G2", commitSha);
        boolean versionMoved = confirmed.profileVersion() == beforeVersion + 1;
        boolean parentKept = again.child().parentRunId().equals(live.runId());
        return new CaseView("E", "ENGINE_EXECUTED", versionMoved && parentKept, live.runId(), again.child().runId(),
                "用户明确选择了自己的软偏好，这个值不是从接受事件推断的。旧匹配快照没有被替换。新运行挂在旧运行下面。");
    }

    private CaseView caseF(String commitSha) {
        CaseView prepared = caseE(commitSha);
        long userId = plans.get(prepared.leftRunId()).result().pairs().get(0).highId();
        long before = lab.profile(userId).version();
        String source = evidenceFor(userId).stream()
                .filter(item -> "partner.pace".equals(item.featureCode()) && item.sourceRef().startsWith("confirm."))
                .map(ProfileEvidence::sourceRef).findFirst().orElseThrow(() -> new LabRejection("PROPOSAL"));
        LearningView retracted = retract(userId, source);
        boolean paceGone = retracted.after().stream().noneMatch(row -> "partner.pace".equals(row.featureCode()) && "fast".equals(row.value()));
        boolean othersRemain = retracted.after().stream().anyMatch(row -> row.featureCode().startsWith("self."));
        return new CaseView("F", "ENGINE_EXECUTED", retracted.profileVersion() == before + 1 && paceGone && othersRemain,
                prepared.leftRunId(), prepared.rightRunId(),
                "撤回确认来源后，这条偏好退出评分。其他特征还在，整份确认前画像没有被拷回。");
    }

    private GlobalView solve(String objectiveName, List<Long> requested, String commitSha, String parentRunId, String mode) {
        if (plans.size() >= MAX_PLANS) throw new LabRejection("SANDBOX_LIMIT");
        Objective objective = objective(objectiveName);
        List<CompatibilitySubject> users = select(requested);
        String matchingRunId = "sandbox-" + commands.incrementAndGet();
        GlobalMatchingInput seeded = GlobalMatchingInput.of(matchingRunId, "demo", users, Set.of(), Set.of(), Set.of(), Map.of(), 1);
        String objectiveVersion = objectiveVersion(objective);
        GlobalMatchingInput input = new GlobalMatchingInput(seeded.matchingRunId(), LocalDate.now(ZoneOffset.UTC), seeded.cohort(),
                seeded.eligibleUsers(), seeded.profileSnapshotVersions(), seeded.lifecycleGenerations(), seeded.consentSnapshot(),
                seeded.blockedPairs(), seeded.forbiddenPairs(), seeded.historicalRestrictions(),
                CANDIDATE_STRATEGY, seeded.compatibilityPolicyVersion(), objectiveVersion,
                seeded.syntheticUnmatchedWeeks(), seeded.seed());
        GlobalMatchingGraph graph = GlobalCandidateGraphBuilder.build(input, GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL);
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(graph, objective);
        Instant solvedAt = Instant.now();
        String snapshot = canonicalInput(input);
        String semantic = canonicalResult(result);
        String runId = lab.record("GLOBAL_MATCH", "workspace-sandbox", snapshot, objectiveVersion,
                commitSha, mode, result.status(), semantic, LIMIT, parentRunId);
        plans.put(runId, new Held(graph, result, mode, objectiveVersion, solvedAt));
        return view(runId, graph, result, mode, parentRunId, null, digest(snapshot), digest(semantic), digest(semantic));
    }

    private GlobalView hold(String label, GlobalMatchingGraph graph, Objective objective, String commitSha, String mode) {
        if (plans.size() >= MAX_PLANS) throw new LabRejection("SANDBOX_LIMIT");
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(graph, objective);
        String objectiveVersion = objectiveVersion(objective);
        String snapshot = canonicalInput(graph.input()) + "\nfixture=" + label;
        String semantic = canonicalResult(result);
        String runId = lab.record("GLOBAL_MATCH", "math-fixture:" + label, snapshot, objectiveVersion,
                commitSha, mode, result.status(), semantic, LIMIT, null);
        plans.put(runId, new Held(graph, result, mode, objectiveVersion, Instant.now()));
        return view(runId, graph, result, mode, null, null, digest(snapshot), digest(semantic), digest(semantic));
    }

    private static boolean usable(GlobalMatchingResult result) {
        return result.executablePlan() && result.fullPopulationGraph() && result.safetyViolations() == 0
                && GlobalMatchingGraph.COMPLETE.equals(result.status());
    }

    private List<CompatibilitySubject> select(List<Long> requested) {
        List<CompatibilitySubject> all = lab.subjects();
        if (requested == null || requested.isEmpty()) return all;
        List<CompatibilitySubject> out = new ArrayList<>();
        for (long id : requested) {
            out.add(all.stream().filter(user -> user.userId() == id).findFirst().orElseThrow(() -> new LabRejection("PROFILE")));
        }
        return List.copyOf(out);
    }

    private boolean workspaceUser(long userId) {
        return lab.subjects().stream().anyMatch(user -> user.userId() == userId);
    }

    private FeedbackCommand system(FeedbackEventType type, String key, Instant when) {
        return new FeedbackCommand(type, 0, FeedbackFacts.SourceType.SYSTEM, key, when, null, null, null);
    }

    private static FeedbackEventType actionType(String action) {
        return switch (action) {
            case "view" -> FeedbackEventType.MATCH_VIEWED;
            case "accept" -> FeedbackEventType.MATCH_ACCEPTED;
            case "decline" -> FeedbackEventType.MATCH_DECLINED;
            case "timeout" -> FeedbackEventType.MATCH_EXPIRED;
            case "schedule" -> FeedbackEventType.DATE_SCHEDULED;
            case "cancel" -> FeedbackEventType.DATE_CANCELLED;
            case "attended" -> FeedbackEventType.DATE_REPORTED_ATTENDED;
            case "missed" -> FeedbackEventType.DATE_REPORTED_NOT_ATTENDED;
            case "continue" -> FeedbackEventType.CONTINUE_INTEREST_EXPRESSED;
            case "stop" -> FeedbackEventType.CONTINUE_INTEREST_DECLINED;
            case "correct" -> FeedbackEventType.FEEDBACK_CORRECTED;
            case "preference" -> FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED;
            case "retract" -> FeedbackEventType.FEEDBACK_RETRACTED;
            case "revoke" -> FeedbackEventType.FEEDBACK_PROCESSING_REVOKED;
            default -> throw new LabRejection("ACTION");
        };
    }

    private ExplicitPreferenceCommand command(long userId, String featureCode, String oldValue, String newValue,
                                              ExplicitPreferenceCommand.Action action, List<String> sources, Instant when,
                                              boolean processingGranted, boolean learningGranted) {
        FeatureWrite current = write(userId, featureCode);
        Importance importance = current == null || current.importance() == null || current.importance().isBlank()
                ? Importance.SUPER : Importance.valueOf(current.importance());
        Operator operator = current == null || current.operator() == null || current.operator().isBlank()
                ? Operator.EQ : Operator.valueOf(current.operator());
        return new ExplicitPreferenceCommand("pref." + commands.incrementAndGet(), userId, featureCode, oldValue, newValue,
                importance, operator, action, (int) lab.profile(userId).version(), generation(userId), generation(userId),
                processingGranted, learningGranted, sources, when);
    }

    private long generation(long userId) {
        return lab.subjects().stream().filter(user -> user.userId() == userId).findFirst()
                .orElseThrow(() -> new LabRejection("PROFILE")).generation();
    }

    private String currentValue(long userId, String featureCode) {
        for (FeatureWrite write : lab.writes(userId)) {
            if (featureCode.equals(write.code())) return write.value();
        }
        return "";
    }

    private List<ProfileEvidence> evidenceFor(long userId) {
        long version = lab.profile(userId).version();
        Long pinned = evidenceAtVersion.get(userId);
        if (pinned == null || pinned != version) {
            sources.put(userId, new ConfirmationSources());
            List<ProfileEvidence> seeded = seedEvidence(userId);
            evidence.put(userId, seeded);
            evidenceAtVersion.put(userId, version);
        }
        return evidence.get(userId);
    }

    private List<ProfileEvidence> seedEvidence(long userId) {
        List<ProfileEvidence> rows = new ArrayList<>();
        ConfirmationSources directory = sourcesFor(userId);
        for (FeatureWrite write : lab.writes(userId)) {
            if (write.value() == null || write.value().isBlank()) continue;
            if (write.status() != null && !"CONFIRMED".equals(write.status())) continue;
            try {
                ProfileEvidence seeded = learning.seed(userId, write.code(), write.value());
                directory.put(ConfirmationSources.seed(userId, seeded.sourceRef()));
                rows.add(seeded);
            } catch (FeedbackRejection ignored) {
                // Hard constraints and behavior rows stay on the profile. They are not learning seeds.
            }
        }
        return rows;
    }

    private ConfirmationSources sourcesFor(long userId) {
        return sources.computeIfAbsent(userId, ignored -> new ConfirmationSources());
    }

    private void adopt(long userId, List<ProfileEvidence> next) {
        List<FeatureWrite> self = new ArrayList<>();
        List<FeatureWrite> preferences = new ArrayList<>();
        List<FeatureWrite> behavior = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FeatureWrite write : lab.writes(userId)) {
            seen.add(write.code());
            FeatureWrite kept = merge(write, next);
            if (kept == null) continue;
            place(kept, self, preferences, behavior);
        }
        for (ProfileEvidence item : next) {
            if (seen.contains(item.featureCode()) || item.status() != EvidenceStatus.CONFIRMED) continue;
            if (!item.sourceRef().startsWith("confirm.")) continue;
            place(new FeatureWrite(item.featureCode(), text(item.claim()), "CONFIRMED",
                    item.importance() == null ? null : item.importance().name(),
                    item.operator() == null ? "EQ" : item.operator().name()), self, preferences, behavior);
        }
        lab.update(userId, lab.profile(userId).name(), self, preferences, behavior, lab.profile(userId).version());
        evidence.put(userId, next);
        evidenceAtVersion.put(userId, lab.profile(userId).version());
    }

    private FeatureWrite merge(FeatureWrite write, List<ProfileEvidence> next) {
        ProfileEvidence confirmed = null;
        boolean mentioned = false;
        for (ProfileEvidence item : next) {
            if (!item.featureCode().equals(write.code())) continue;
            mentioned = true;
            if (item.status() == EvidenceStatus.CONFIRMED) confirmed = item;
        }
        if (!mentioned) return write;
        if (confirmed == null) return null;
        if (!confirmed.sourceRef().startsWith("confirm.")) return write;
        return new FeatureWrite(write.code(), text(confirmed.claim()), "CONFIRMED",
                confirmed.importance() == null ? write.importance() : confirmed.importance().name(),
                confirmed.operator() == null ? write.operator() : confirmed.operator().name());
    }

    private void place(FeatureWrite write, List<FeatureWrite> self, List<FeatureWrite> preferences, List<FeatureWrite> behavior) {
        switch (section(write.code())) {
            case BEHAVIOR -> behavior.add(write);
            case PREFERENCE -> preferences.add(write);
            default -> self.add(write);
        }
    }

    private Section section(String code) {
        FeatureDefinition definition = registry.require(code);
        if (definition.sections().contains(Section.BEHAVIOR)) return Section.BEHAVIOR;
        if (definition.sections().contains(Section.PREFERENCE)) return Section.PREFERENCE;
        return Section.SELF;
    }

    private List<String> unlearnable(String featureCode) {
        List<String> reasons = new ArrayList<>();
        if (learning.hypothesisFeature(featureCode)) reasons.add("待确认假设不进入正式评分");
        try {
            registry.require(featureCode);
        } catch (IllegalArgumentException failure) {
            reasons.add("ILLEGAL_FEATURE");
        }
        reasons.add("拒绝和超时不能形成未经确认的偏好");
        reasons.add("对方评价不进入本人自身事实");
        return reasons;
    }

    private GlobalView view(String runId, GlobalMatchingGraph graph, GlobalMatchingResult result, String mode,
                            String parentRunId, GlobalView comparison, String inputHash, String resultHash,
                            String semanticResultHash) {
        List<EdgeView> edges = new ArrayList<>();
        for (PreparedEdge edge : graph.solver()) edges.add(edgeView(edge, true));
        for (PreparedEdge edge : graph.diagnostic()) {
            if (!edge.solverEligible()) edges.add(edgeView(edge, false));
        }
        List<PairView> pairs = new ArrayList<>();
        for (GlobalMatchingResult.MatchedPair pair : result.pairs()) {
            pairs.add(new PairView(pair.lowId(), pair.highId(), pair.key().toString(), pair.fitScore(),
                    pair.scoreLowToHigh(), pair.scoreHighToLow(), pair.coverageLowToHigh(), pair.coverageHighToLow()));
        }
        List<PersonView> unmatched = new ArrayList<>();
        for (GlobalMatchingResult.UnmatchedPerson person : result.unmatched()) {
            unmatched.add(new PersonView(person.userId(), person.finalReason(), person.diagnostics()));
        }
        double average = pairs.stream().mapToDouble(pair -> pair.fit() == null ? 0 : pair.fit()).average().orElse(0);
        double lowest = pairs.stream().mapToDouble(pair -> pair.fit() == null ? 0 : pair.fit()).min().orElse(0);
        int initial = graph.input().eligibleUsers().size();
        return new GlobalView(runId, parentRunId, mode, result.objective().name(), result.status(), result.statusDetail(),
                usable(result), initial, graph.stats().eligible(), graph.stats().consideredPairs(),
                graph.stats().solverEdges(), pairs.size(), unmatched.size(), average, lowest,
                result.totalOptimizationWeight(), graph.coverage().name(), result.solveMs(), result.safetyViolations(),
                result.safetyBreakdown(), pairs, unmatched, edges, LIMIT, inputHash, resultHash, semanticResultHash, comparison);
    }

    private static EdgeView edgeView(PreparedEdge edge, boolean solver) {
        return new EdgeView(edge.lowId(), edge.highId(), edge.fitScore(), edge.scoreLowToHigh(), edge.scoreHighToLow(),
                edge.coverageLowToHigh(), edge.coverageHighToLow(), edge.versionLow(), edge.versionHigh(),
                edge.generationLow(), edge.generationHigh(), solver, edge.exclusion(), edge.diagnostics());
    }

    private FeedbackView feedbackView(String key, FeedbackLedger ledger, String labRunId) {
        Set<String> superseded = new LinkedHashSet<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.supersedesEventId() != null) superseded.add(event.supersedesEventId());
        }
        List<EventView> events = new ArrayList<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            events.add(new EventView(event.eventId(), event.eventType().name(), event.actorUserId(),
                    event.occurredAt().toString(), event.recordedAt().toString(), event.sourceType().name(),
                    !superseded.contains(event.eventId()), event.causationEventId(), event.supersedesEventId(),
                    superseded.contains(event.eventId())));
        }
        FeedbackProjection projection = ledger.project();
        return new FeedbackView(key, ledger.context().matchingRunId(), ledger.context().userA(), ledger.context().userB(),
                ledger.context().profileVersionA(), ledger.context().profileVersionB(), events,
                new ProjectionView(projection.responseA().name(), projection.responseB().name(), projection.mutualAcceptance(),
                        projection.schedule().name(), projection.attendance().name(), projection.contested(),
                        projection.continueA().name(), projection.continueB().name(), projection.unknown(),
                        ledger.eligibility().matchFactEligible(), ledger.eligibility().reasons()),
                labRunId, ledger.context().candidateStrategyVersion(), ledger.context().compatibilityPolicyVersion(),
                ledger.context().matchingObjectiveVersion(), ledger.context().matchingPeriod().toString(),
                ledger.context().lifecycleGenerationA(), ledger.context().lifecycleGenerationB());
    }

    private static ProposalView proposalView(ProfileLearningProposal proposal) {
        return new ProposalView(proposal.proposalId(), proposal.userId(), proposal.featureCode(), proposal.oldValue(),
                proposal.proposedValue(), proposal.status().name(), proposal.profileVersion(), proposal.evidenceStatus());
    }

    private List<FeatureRow> featureRows(long userId) {
        List<FeatureRow> rows = new ArrayList<>();
        for (FeatureWrite write : lab.writes(userId)) {
            rows.add(new FeatureRow(write.code(), write.value() == null ? "" : write.value(),
                    write.status() == null ? "" : write.status()));
        }
        rows.sort(Comparator.comparing(FeatureRow::featureCode));
        return rows;
    }

    private static PairView firstPair(GlobalView view) {
        if (view.pairs().isEmpty()) throw new LabRejection("PAIR");
        return view.pairs().get(0);
    }

    private Held requirePlan(String runId) {
        Held held = plans.get(runId);
        if (held == null) throw new LabRejection("RUN");
        return held;
    }

    private static List<String> pairKeys(GlobalMatchingResult result) {
        List<String> keys = new ArrayList<>();
        for (GlobalMatchingResult.MatchedPair pair : result.pairs()) keys.add(pair.key().toString());
        return keys;
    }

    private String canonicalInput(GlobalMatchingInput input) {
        StringBuilder text = new StringBuilder();
        text.append("schema=sandbox-input-1\n");
        text.append("period=").append(input.matchingPeriod()).append('\n');
        text.append("candidate=").append(input.candidateStrategyVersion()).append('\n');
        text.append("compatibility=").append(input.compatibilityPolicyVersion()).append('\n');
        text.append("objective=").append(input.objectiveVersion()).append('\n');
        text.append("seed=").append(input.seed()).append('\n');
        for (CompatibilitySubject user : input.eligibleUsers()) {
            text.append("user=").append(user.userId())
                    .append(" version=").append(user.profileVersion())
                    .append(" generation=").append(user.generation())
                    .append(" consent=").append(input.consentSnapshot().get(user.userId()))
                    .append(" current=").append(user.current())
                    .append(" age=").append(user.candidate().ageMin()).append('-').append(user.candidate().ageMax())
                    .append('\n');
            for (ProfileFeature feature : user.self().features()) {
                text.append("self ").append(feature.featureCode()).append('=').append(text(feature.value()))
                        .append(" status=").append(feature.status()).append('\n');
            }
            for (PreferenceTarget target : user.preference().targets()) {
                text.append("pref ").append(target.featureCode()).append('=').append(text(target.targetValue()))
                        .append(" op=").append(target.operator())
                        .append(" importance=").append(target.importance())
                        .append(" status=").append(target.status()).append('\n');
            }
        }
        return text.toString();
    }

    static String canonicalResult(GlobalMatchingResult result) {
        StringBuilder text = new StringBuilder();
        text.append("schema=sandbox-result-1\n");
        text.append("objective=").append(result.objective().name()).append('\n');
        text.append("status=").append(result.status()).append('\n');
        text.append("safety=").append(result.safetyViolations()).append('\n');
        text.append("weight=").append(result.totalOptimizationWeight()).append('\n');
        text.append("executable=").append(result.executablePlan()).append('\n');
        for (GlobalMatchingResult.MatchedPair pair : result.pairs()) {
            text.append("pair=").append(pair.key())
                    .append(" fit=").append(pair.fitScore())
                    .append(" ab=").append(pair.scoreLowToHigh())
                    .append(" ba=").append(pair.scoreHighToLow())
                    .append(" cover=").append(pair.coverageLowToHigh()).append('/').append(pair.coverageHighToLow())
                    .append('\n');
        }
        for (GlobalMatchingResult.UnmatchedPerson person : result.unmatched()) {
            text.append("unmatched=").append(person.userId()).append(':').append(person.finalReason()).append('\n');
        }
        return text.toString();
    }

    static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new LabRejection("RUN");
        }
    }

    private static String objectiveVersion(Objective objective) {
        return switch (objective) {
            case G0_GREEDY -> "global-objective-g0-1";
            case G1_MAX_WEIGHT -> "global-objective-g1-1";
            case G2_CARDINALITY_THEN_WEIGHT -> Objective.SELECTED_VERSION;
            case G3_QUALITY_TIER -> "global-objective-g3-1";
            case G4_FAIRNESS -> "global-objective-g4-1";
        };
    }

    private FeatureWrite write(long userId, String featureCode) {
        for (FeatureWrite item : lab.writes(userId)) {
            if (featureCode.equals(item.code())) return item;
        }
        return null;
    }

    private boolean consent(long userId) {
        return lab.subjects().stream().filter(user -> user.userId() == userId).findFirst()
                .map(user -> user.current() && !user.withdrawn()).orElse(false);
    }

    private int eventCount() {
        int count = 0;
        for (FeedbackLedger ledger : ledgers.values()) count += ledger.events().size();
        return count;
    }

    private static String labRunId(String ledgerKey) {
        int split = ledgerKey.indexOf(':');
        return split < 0 ? ledgerKey : ledgerKey.substring(0, split);
    }

    private RelationshipFeedbackEvent requireFeedbackSource(long userId, String ledgerKey, String sourceEventId) {
        if (ledgerKey == null || ledgerKey.isBlank() || sourceEventId == null || sourceEventId.isBlank()) {
            throw new LabRejection("SOURCE");
        }
        FeedbackLedger ledger = ledgers.get(ledgerKey);
        if (ledger == null) throw new LabRejection("LEDGER");
        if (!ledger.context().participant(userId)) throw new LabRejection("ACTOR");
        if (!ledger.context().learningConsent(userId)) throw new LabRejection("NO_LEARNING_CONSENT");
        RelationshipFeedbackEvent found = null;
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.eventId().equals(sourceEventId)) found = event;
        }
        if (found == null) throw new LabRejection("SOURCE");
        if (found.actorUserId() != userId) throw new LabRejection("CROSS_USER");
        if (sourceBlocked(new ProposalBinding("FEEDBACK", ledgerKey, found.eventId(), userId))) {
            throw new LabRejection("RETRACTED_SOURCE");
        }
        return found;
    }

    private boolean sourceBlocked(ProposalBinding binding) {
        FeedbackLedger ledger = ledgers.get(binding.ledgerKey());
        if (ledger == null) return true;
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.eventType() == FeedbackEventType.FEEDBACK_RETRACTED
                    || event.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED) {
                return true;
            }
            if (binding.sourceEventId().equals(event.supersedesEventId())) return true;
        }
        return false;
    }

    private static Objective objective(String name) {
        return switch (name == null || name.isBlank() ? "G2" : name) {
            case "G0", "G0_GREEDY" -> Objective.G0_GREEDY;
            case "G1", "G1_MAX_WEIGHT" -> Objective.G1_MAX_WEIGHT;
            case "G2", "G2_CARDINALITY_THEN_WEIGHT" -> Objective.G2_CARDINALITY_THEN_WEIGHT;
            case "G3", "G3_QUALITY_TIER" -> Objective.G3_QUALITY_TIER;
            case "G4", "G4_FAIRNESS" -> Objective.G4_FAIRNESS;
            default -> throw new LabRejection("OBJECTIVE");
        };
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(FeatureValue value) {
        if (value instanceof FeatureValue.EnumOne one) return one.value();
        if (value instanceof FeatureValue.EnumMany many) return String.join(",", many.values());
        return "";
    }

    private record Held(GlobalMatchingGraph graph, GlobalMatchingResult result, String mode, String objectiveVersion, Instant solvedAt) {}

    private record ProposalBinding(String origin, String ledgerKey, String sourceEventId, long userId) {}

    public record GlobalView(String runId, String parentRunId, String executionMode, String objective, String status,
                             String statusDetail, boolean usablePlan, int initialUsers, int eligibleEdges,
                             long consideredPairs, int solverEdges, int pairsCount, int unmatchedCount,
                             double averageFit, double lowestFit, long totalWeight, String coverage, long solveMs,
                             int safetyViolations, Map<String, Integer> safetyBreakdown, List<PairView> pairs,
                             List<PersonView> unmatched, List<EdgeView> edges, String limitations,
                             String inputHash, String resultHash, String semanticResultHash, GlobalView comparison) {
        private GlobalView withComparison(GlobalView other) {
            return new GlobalView(runId, parentRunId, executionMode, objective, status, statusDetail, usablePlan,
                    initialUsers, eligibleEdges, consideredPairs, solverEdges, pairsCount, unmatchedCount, averageFit,
                    lowestFit, totalWeight, coverage, solveMs, safetyViolations, safetyBreakdown, pairs, unmatched,
                    edges, limitations, inputHash, resultHash, semanticResultHash, other);
        }
    }

    public record PairView(long lowId, long highId, String key, Double fit, Double scoreLowToHigh, Double scoreHighToLow,
                           Double coverageLowToHigh, Double coverageHighToLow) {}

    public record PersonView(long userId, String reason, List<String> diagnostics) {}

    public record EdgeView(long lowId, long highId, Double fit, Double scoreLowToHigh, Double scoreHighToLow,
                           Double coverageLowToHigh, Double coverageHighToLow, long versionLow, long versionHigh,
                           long generationLow, long generationHigh, boolean solver, String exclusion, List<String> diagnostics) {}

    public record FeedbackView(String ledgerKey, String matchingRunId, long userA, long userB, long versionA, long versionB,
                               List<EventView> events, ProjectionView projection, String labRunId,
                               String candidateStrategyVersion, String compatibilityPolicyVersion,
                               String globalObjectiveVersion, String matchingPeriod, long generationA, long generationB) {}

    public record EventView(String eventId, String type, long actorUserId, String occurredAt, String recordedAt,
                            String source, boolean effective, String causationEventId, String supersedesEventId,
                            boolean superseded) {}

    public record ProjectionView(String responseA, String responseB, boolean mutualAcceptance, String schedule,
                                 String attendance, boolean contested, String continueA, String continueB,
                                 List<String> unknown, boolean learningAllowed, List<String> learningReasons) {}

    public record LearningView(String ledgerKey, long userId, long profileVersion, List<SignalView> signals,
                               List<String> unlearnable, ProposalView proposal, List<FeatureRow> before,
                               List<FeatureRow> after, long generation, String limitations) {}

    public record SignalView(String sourceEventId, String category, String target, String evidenceState, String authorization) {}

    public record ProposalView(String proposalId, long userId, String featureCode, String oldValue, String proposedValue,
                               String status, int profileVersion, String evidenceStatus) {}

    public record FeatureRow(String featureCode, String value, String status) {}

    public record RematchView(String parentRunId, GlobalView child, List<String> pairsBefore, List<String> pairsAfter,
                              String limitations) {}

    public record CaseView(String code, String executionMode, boolean observed, String leftRunId, String rightRunId,
                           String note) {}
}
