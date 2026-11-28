package cn.aiditto.matchlab;

import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.matchlab.LabWorkbench;
import cn.aiditto.domain.matchlab.LabWorkbench.FeatureWrite;
import cn.aiditto.domain.matchlab.LabWorkbench.LabRejection;
import cn.aiditto.infra.web.Problems;
import org.springframework.http.ResponseEntity;
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
    private final LabWorkbench lab = new LabWorkbench();
    private final MatchLabProperties properties;

    public MatchLabController(MatchLabProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/overview")
    Map<String, Object> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("environment", "SYNTHETIC_SANDBOX");
        body.put("enabled", properties.isEnabled());
        body.put("commitSha", properties.getCommitSha());
        body.put("production", "NO-GO");
        body.put("realRelationshipQuality", "NOT_RUN");
        body.put("catalog", lab.catalog());
        body.put("profiles", lab.profiles().size());
        body.put("runs", lab.runs().size());
        body.put("limits", List.of("最多 24 份虚构画像", "不读取生产用户", "不调用真实模型", "不发布匹配"));
        return body;
    }

    @GetMapping("/registry")
    List<Map<String, Object>> registry() {
        List<Map<String, Object>> out = new ArrayList<>();
        lab.registry().forEach(definition -> {
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
    List<LabWorkbench.StoredView> profiles() { return lab.profiles(); }

    @PostMapping("/profiles")
    ResponseEntity<Object> create(@RequestBody WriteBody body) {
        return guard(() -> lab.create(body.name(), writes(body.self()), writes(body.preferences())));
    }

    @PutMapping("/profiles/{userId}")
    ResponseEntity<Object> update(@PathVariable long userId, @RequestBody WriteBody body) {
        return guard(() -> lab.update(userId, body.name(), writes(body.self()), writes(body.preferences())));
    }

    @GetMapping("/diff")
    ResponseEntity<Object> diff(@RequestParam long left, @RequestParam long right) {
        return guard(() -> lab.diff(left, right));
    }

    @PostMapping("/score")
    ResponseEntity<Object> score(@RequestBody ScoreBody body) {
        return guard(() -> lab.score(body.userA(), body.userB(), properties.getCommitSha()));
    }

    @PostMapping("/recall")
    ResponseEntity<Object> recall() {
        return guard(() -> lab.recall(properties.getCommitSha()));
    }

    @PostMapping("/reasoning")
    ResponseEntity<Object> reasoning() {
        return guard(() -> lab.reasoning(properties.getCommitSha()));
    }

    @GetMapping("/runs")
    List<LabWorkbench.LabRun> runs() { return lab.runs(); }

    private ResponseEntity<Object> guard(SupplierCall call) {
        try {
            return ResponseEntity.ok(call.get());
        } catch (LabRejection rejection) {
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

    record WriteBody(String name, List<FeatureBody> self, List<FeatureBody> preferences) {}
    record FeatureBody(String code, String value, String status, String importance, String operator) {}
    record ScoreBody(long userA, long userB) {}
}
