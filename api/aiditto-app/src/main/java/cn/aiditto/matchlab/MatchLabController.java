package cn.aiditto.matchlab;

import cn.aiditto.auth.AuthUser;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.matchlab.LabWorkbench;
import cn.aiditto.domain.matchlab.LabWorkbench.FeatureWrite;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.matchlab.LabWorkbench.LabRejection;
import cn.aiditto.domain.matchlab.WorkspaceBoard;
import cn.aiditto.infra.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/match-lab")
public class MatchLabController {
    private final WorkspaceBoard board = new WorkspaceBoard();
    private final MatchLabProperties properties;

    public MatchLabController(MatchLabProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/overview")
    ResponseEntity<Object> overview(HttpServletRequest request) {
        return guard(() -> {
        WorkspaceBoard.Workspace workspace = workspace(request);
        LabWorkbench lab = workspace.workbench();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("environment", "SYNTHETIC_SANDBOX");
        body.put("researchEnvironment", properties.getEnvironment());
        body.put("enabled", properties.admits());
        body.put("commitSha", properties.getCommitSha());
        body.put("frozenCommit", LabWorkbench.INTEGRATION_COMMIT);
        body.put("ciIndex", "https://github.com/jiashuaidawang-web/tryditto-cn/actions?query=branch%3Afeature%2Fv4-match-lab");
        body.put("statusDocument", "docs/v4/V4-PROJECT-STATUS.md");
        body.put("production", "NO-GO");
        body.put("realRelationshipQuality", "NOT_RUN");
        body.put("storage", WorkspaceBoard.STORAGE);
        body.put("workspace", workspaceView(workspace));
        body.put("policies", lab.policies());
        body.put("catalog", lab.catalog());
        body.put("profiles", lab.profiles().size());
        body.put("runs", lab.runs().size());
        body.put("limits", List.of("最多 24 份虚构画像", "最多 48 次实验运行", "请求体最多 64KiB", "服务重启后工作区丢失", "不读取生产用户", "不调用真实模型", "不发布匹配"));
        return body;
        });
    }

    @PostMapping("/workspace/reset")
    ResponseEntity<Object> reset(HttpServletRequest request) {
        return guard(() -> workspaceView(board.reset(caller().userId(), request.getHeader("X-Match-Lab-Workspace"))));
    }

    @GetMapping("/registry")
    List<Map<String, Object>> registry(HttpServletRequest request) {
        List<Map<String, Object>> out = new ArrayList<>();
        lab(request).registry().forEach(definition -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("featureCode", definition.featureCode());
            row.put("displayName", definition.displayName());
            row.put("valueType", definition.valueType().name());
            row.put("allowedValues", definition.allowedValues());
            row.put("sections", definition.sections());
            out.add(row);
        });
        return out;
    }

    @GetMapping("/profiles")
    List<LabWorkbench.StoredView> profiles(HttpServletRequest request) { return lab(request).profiles(); }

    @PostMapping("/profiles")
    ResponseEntity<Object> create(@RequestBody WriteBody body, HttpServletRequest request) {
        return guard(() -> lab(request).create(body.name(), writes(body.self()), writes(body.preferences()), writes(body.behavior())));
    }

    @PutMapping("/profiles/{userId}")
    ResponseEntity<Object> update(@PathVariable long userId, @RequestBody WriteBody body, HttpServletRequest request) {
        return guard(() -> {
            if (body.expectedVersion() == null) throw new LabRejection("VERSION");
            return lab(request).update(userId, body.name(), writes(body.self()), writes(body.preferences()),
                    writes(body.behavior()), body.expectedVersion());
        });
    }

    @GetMapping("/diff")
    ResponseEntity<Object> diff(@RequestParam long left, @RequestParam long right, HttpServletRequest request) {
        return guard(() -> lab(request).diff(left, right));
    }

    @PostMapping("/score")
    ResponseEntity<Object> score(@RequestBody ScoreBody body, HttpServletRequest request) {
        return guard(() -> lab(request).score(body.userA(), body.userB(), properties.getCommitSha()));
    }

    @PostMapping("/recall")
    ResponseEntity<Object> recall(@RequestBody(required = false) RecallBody body, HttpServletRequest request) {
        return guard(() -> {
            if (body != null && "SYNTHETIC_SANDBOX".equals(body.mode())) {
                if (body.seekerId() == null) throw new LabRejection("PROFILE");
                return lab(request).recallSandbox(body.seekerId(), properties.getCommitSha());
            }
            String split = body == null || body.split() == null || body.split().isBlank() ? "dev" : body.split();
            return lab(request).recall(split, properties.getCommitSha());
        });
    }

    @PostMapping("/reasoning")
    ResponseEntity<Object> reasoning(HttpServletRequest request) {
        return guard(() -> lab(request).reasoning(properties.getCommitSha()));
    }

    @GetMapping("/runs")
    List<LabWorkbench.LabRun> runs(HttpServletRequest request) { return lab(request).runs(); }

    @PostMapping("/global")
    ResponseEntity<Object> global(@RequestBody GlobalBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().solve(body.objective(), body.userIds(), properties.getCommitSha()));
    }

    @PostMapping("/global/compare")
    ResponseEntity<Object> compare(HttpServletRequest request) {
        return guard(() -> lab(request).loop().compare(properties.getCommitSha()));
    }

    @PostMapping("/feedback/open")
    ResponseEntity<Object> openFeedback(@RequestBody OpenBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().open(body.runId(), body.userA(), body.userB()));
    }

    @PostMapping("/feedback/act")
    ResponseEntity<Object> act(@RequestBody ActBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().act(body.ledgerKey(), body.action(), body.actorUserId(), body.supersedesEventId()));
    }

    @GetMapping("/learning")
    ResponseEntity<Object> learning(@RequestParam String ledgerKey, @RequestParam long userId, HttpServletRequest request) {
        return guard(() -> lab(request).loop().learningView(ledgerKey, userId));
    }

    @PostMapping("/learning/propose")
    ResponseEntity<Object> propose(@RequestBody ProposeBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().propose(body.userId(), body.featureCode(), body.newValue(),
                body.sourceEventId(), body.ledgerKey(), body.origin()));
    }

    @PostMapping("/learning/decide")
    ResponseEntity<Object> decide(@RequestBody DecideBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().decide(body.proposalId(), body.action(), body.actorUserId()));
    }

    @PostMapping("/learning/retract")
    ResponseEntity<Object> retract(@RequestBody RetractBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().retract(body.userId(), body.sourceRef()));
    }

    @PostMapping("/rematch")
    ResponseEntity<Object> rematch(@RequestBody RematchBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().rematch(body.parentRunId(), body.objective(), properties.getCommitSha()));
    }

    @PostMapping("/cases/{code}")
    ResponseEntity<Object> runCase(@PathVariable String code, HttpServletRequest request) {
        return guard(() -> lab(request).loop().runCase(code, properties.getCommitSha()));
    }

    @GetMapping("/strategy")
    ResponseEntity<Object> strategy(@RequestParam(required = false) String source, HttpServletRequest request) {
        return guard(() -> lab(request).loop().strategy(source));
    }

    @PostMapping("/strategy/activate")
    ResponseEntity<Object> activate(HttpServletRequest request) {
        return guard(() -> {
            lab(request).loop().rejectStrategyActivation();
            return Map.of("status", "NO-GO");
        });
    }

    @PostMapping("/replay/{runId}")
    ResponseEntity<Object> replay(@PathVariable String runId, HttpServletRequest request) {
        return guard(() -> lab(request).loop().replay(runId, properties.getCommitSha()));
    }

    @PostMapping("/runs/compare")
    ResponseEntity<Object> compareRuns(@RequestBody CompareBody body, HttpServletRequest request) {
        return guard(() -> lab(request).loop().compareRuns(body.leftRunId(), body.rightRunId()));
    }

    @GetMapping("/safety")
    ResponseEntity<Object> safety(HttpServletRequest request) {
        return guard(() -> lab(request).loop().safety());
    }

    @GetMapping("/runs/{runId}/export")
    ResponseEntity<Object> export(@PathVariable String runId, HttpServletRequest request) {
        return guard(() -> Map.of("schemaVersion", "sandbox-export-1", "document", lab(request).loop().exportRun(runId)));
    }

    @GetMapping("/production-gate")
    ResponseEntity<Object> productionGate(HttpServletRequest request) {
        return guard(() -> Map.of(
                "production", "NO-GO",
                "realRelationshipQuality", "NOT_RUN",
                "realLift", "NOT_IDENTIFIED",
                "algorithmQuality", "NO-GO",
                "conditions", List.of("真实关系质量", "真实校准", "真实策略提升", "生产画像接入", "生产反馈接入", "发布安全复查")));
    }

    private LabWorkbench lab(HttpServletRequest request) {
        return workspace(request).workbench();
    }

    private WorkspaceBoard.Workspace workspace(HttpServletRequest request) {
        return board.resolve(caller().userId(), request.getHeader("X-Match-Lab-Workspace"));
    }

    private static AuthUser caller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user) || !user.admin()) {
            throw new LabRejection("AUTH");
        }
        return user;
    }

    private static Map<String, Object> workspaceView(WorkspaceBoard.Workspace workspace) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workspaceId", workspace.workspaceId());
        body.put("ownerPrincipalId", workspace.ownerPrincipalId());
        body.put("mode", workspace.mode());
        body.put("createdAt", workspace.createdAt().toString());
        body.put("lastAccessAt", workspace.lastAccessAt().toString());
        body.put("revision", workspace.revision());
        body.put("expiresAt", workspace.expiresAt().toString());
        body.put("storage", WorkspaceBoard.STORAGE);
        return body;
    }

    private ResponseEntity<Object> guard(SupplierCall call) {
        try {
            return ResponseEntity.ok(call.get());
        } catch (LabRejection rejection) {
            if ("WORKSPACE".equals(rejection.code()) || "AUTH".equals(rejection.code())) {
                return Problems.entity(ErrorCode.FORBIDDEN, rejection.code(), List.of(rejection.code()), null);
            }
            return Problems.entity(ErrorCode.VALIDATION, rejection.code(), List.of(rejection.code()), null);
        } catch (FeedbackRejection rejection) {
            return Problems.entity(ErrorCode.VALIDATION, rejection.code(), List.of(rejection.code()), null);
        }
    }

    private static List<FeatureWrite> writes(List<FeatureBody> rows) {
        if (rows == null) return List.of();
        List<FeatureWrite> out = new ArrayList<>();
        for (FeatureBody row : rows) {
            out.add(new FeatureWrite(row.code(), row.value(), row.status(), row.importance(), row.operator()));
        }
        return out;
    }

    private interface SupplierCall { Object get(); }

    record WriteBody(String name, List<FeatureBody> self, List<FeatureBody> preferences, List<FeatureBody> behavior,
                     Long expectedVersion) {}
    record RecallBody(String split, String mode, Long seekerId) {}
    record FeatureBody(String code, String value, String status, String importance, String operator) {}
    record ScoreBody(long userA, long userB) {}
    record GlobalBody(String objective, List<Long> userIds) {}
    record OpenBody(String runId, long userA, long userB) {}
    record ActBody(String ledgerKey, String action, long actorUserId, String supersedesEventId) {}
    record ProposeBody(long userId, String featureCode, String newValue, String sourceEventId, String ledgerKey, String origin) {}
    record DecideBody(String proposalId, String action, long actorUserId) {}
    record RetractBody(long userId, String sourceRef) {}
    record RematchBody(String parentRunId, String objective) {}
    record CompareBody(String leftRunId, String rightRunId) {}
}
