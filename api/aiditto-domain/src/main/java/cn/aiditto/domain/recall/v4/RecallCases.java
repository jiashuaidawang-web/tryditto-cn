package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PoolGate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Six fixture stories for Match Lab. They are not production users. */
public final class RecallCases {
    public static final String SOURCE = "synthetic-fixture:v4-recall-case-v1";
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 25);
    private static final Instant NOW = Instant.parse("2026-11-27T06:00:00Z");

    public record Story(String id, String question, V4RecallEngine.Result result, String note) {}

    private RecallCases() {}

    public static List<Story> six() {
        return List.of(sharedHobby(), selfRoute(), fusedRoutes(), hardRejection(), budgetLoss(), oneWayAndVersion());
    }

    private static Story sharedHobby() {
        RecallSubject ada = subject(1, Set.of("reading"), Map.of(), Map.of(), true);
        RecallSubject bao = subject(2, Set.of("reading"), Map.of(), Map.of(), true);
        V4RecallEngine.Result result = run(ada, List.of(ada, bao), Set.of(RecallRoute.R1), 10);
        return new Story("C1", "某个用户为什么被召回", result, "共同爱好 reading 进入 R1");
    }

    private static Story selfRoute() {
        RecallSubject ada = subject(1, Set.of(), Map.of("self.pace", "slow"), Map.of(), true);
        RecallSubject bao = subject(2, Set.of(), Map.of("self.pace", "slow"), Map.of(), true);
        V4RecallEngine.Result result = run(ada, List.of(ada, bao), Set.of(RecallRoute.R2), 10);
        return new Story("C2", "是哪条路线发现该用户", result, "R2 只比较自身节奏，未知不算一致");
    }

    private static Story fusedRoutes() {
        RecallSubject ada = subject(1, Set.of("music"), Map.of(), Map.of("partner.communication", "frequent"), true);
        RecallSubject bao = subject(2, Set.of("music"), Map.of("self.communication", "frequent"), Map.of(), true);
        V4RecallEngine.Result result = run(ada, List.of(ada, bao), Set.of(RecallRoute.R1, RecallRoute.R3), 10);
        return new Story("C3", "多条路线如何融合", result, "R1 与 R3 都命中，融合原因是 FUSED");
    }

    private static Story hardRejection() {
        RecallSubject ada = subject(1, Set.of("reading"), Map.of(), Map.of(), true);
        Candidate old = Candidate.builder(2).birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60).hobbies("reading").build();
        RecallSubject bao = copy(2, old, Set.of("reading"), Map.of(), Map.of(), true);
        V4RecallEngine.Result result = run(ada, List.of(ada, bao), Set.of(RecallRoute.R1), 10);
        return new Story("C4", "哪些候选被硬约束拒绝", result, "年龄双向不兼容，候选被拒绝且不进入列表");
    }

    private static Story budgetLoss() {
        List<RecallSubject> pool = new java.util.ArrayList<>();
        RecallSubject seeker = subject(1, Set.of("cooking"), Map.of(), Map.of(), true);
        pool.add(seeker);
        for (int id = 2; id <= 6; id++) pool.add(subject(id, Set.of("cooking"), Map.of(), Map.of(), true));
        V4RecallEngine.Result result = run(seeker, pool, Set.of(RecallRoute.R1), 2);
        return new Story("C5", "哪些正确候选因 Top-K 预算丢失", result, "五名同好里只保留两人，其余三人在预算外");
    }

    private static Story oneWayAndVersion() {
        RecallSubject ada = subject(1, Set.of(), Map.of("self.communication", "quiet"), Map.of(), true);
        RecallSubject bao = subject(2, Set.of(), Map.of(), Map.of("partner.communication", "quiet"), true);
        V4RecallEngine.Result forward = run(bao, List.of(ada, bao), Set.of(RecallRoute.R3), 10);
        V4RecallEngine.Result reverse = run(ada, List.of(ada, bao), Set.of(RecallRoute.R3), 10);
        RecallSubject withdrawn = new RecallSubject(ada.userId(), "case", ada.profileVersion(), true, true, SOURCE,
                ada.candidate(), ada.applicant(), ada.consent(), ada.selfEnums(), ada.hobbies(), ada.preferenceEnums(),
                ada.generation(), ada.ingress());
        V4RecallEngine.Result after = run(bao, List.of(withdrawn, bao), Set.of(RecallRoute.R3), 10);
        String note = "正向列表 " + forward.hits().size() + "，反向列表 " + reverse.hits().size()
                + "，撤回后列表 " + after.hits().size();
        return new Story("C6", "A 召回了 B 吗，画像撤回后候选池如何变化", forward.hits().isEmpty() ? reverse : forward, note);
    }

    private static V4RecallEngine.Result run(RecallSubject seeker, List<RecallSubject> pool, Set<RecallRoute> routes, int budget) {
        List<Candidate> candidates = pool.stream().map(RecallSubject::candidate).toList();
        MatchingInput input = MatchingInput.of(WEEK, candidates, 1);
        return V4RecallEngine.recall(seeker, pool, input, NOW, budget, routes, 1);
    }

    private static RecallSubject subject(long id, Set<String> hobbies, Map<String, String> self, Map<String, String> preference, boolean current) {
        Candidate candidate = Candidate.builder(id).hobbies(hobbies.toArray(String[]::new)).build();
        return copy(id, candidate, hobbies, self, preference, current);
    }

    private static RecallSubject copy(long id, Candidate candidate, Set<String> hobbies, Map<String, String> self,
                                      Map<String, String> preference, boolean current) {
        PoolGate.Applicant applicant = new PoolGate.Applicant(candidate.birthday(), true, true, false, 2, false, false, null, false);
        MatchingConsent consent = new MatchingConsent(id, ConsentStatus.GRANTED, 1, NOW, null, ConsentPolicy.POLICY_VERSION, NOW);
        return new RecallSubject(id, "case", current ? 2 : 1, current, false, SOURCE, candidate, applicant, consent,
                self, hobbies, preference, 1, RecallIngress.SYNTHETIC_FIXTURE);
    }
}
