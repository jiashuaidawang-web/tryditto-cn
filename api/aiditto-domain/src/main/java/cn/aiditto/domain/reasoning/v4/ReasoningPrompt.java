package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;

import java.util.ArrayList;
import java.util.List;

/** Prompt contract for one direction. The other direction's conclusions are not included. */
public final class ReasoningPrompt {
    public static final String VERSION = "reasoning-prompt-1";

    private static final String SYSTEM = """
            你是单向关系推理助手。你只根据提示中的一方已确认偏好，查看另一方的合法自身特征。
            你不代表两个人，不判断谁一定会喜欢谁，也不预测恋爱成功概率。
            潜在互补不是已验证契合。潜在冲突不是硬约束。你不能新增用户没有声明的硬约束。
            不能根据性别、职业或人口统计特征编造人格。不能把缺失特征说成某种性格。
            每条判断必须给出偏好码、目标特征码和提示里真实存在的证据引用。证据引用存在不等于语义成立。
            只输出一个 JSON 对象，不要 markdown。字段为：
            direction, chooserId, targetId, supportedMatches, potentialComplements, possibleConflicts,
            missingInformation, contradictions, unsupportedClaims, confidence, reasoningStatus。
            每条判断的字段为 preferenceCode, targetFeatureCode, evidenceRefs, statement。
            confidence 只是你对这份输出完整度的自我说明，取值 0 到 1，它不是匹配概率。
            reasoningStatus 只能是 COMPLETED。
            """;

    private ReasoningPrompt() {}

    public static String system() { return SYSTEM; }

    public static String hash() { return RelationshipReasoningContext.sha256(VERSION + "\n" + SYSTEM); }

    public record Rendered(String version, String hash, String system, String user, String digest,
                           RelationshipReasoningContext.View view) {}

    public static Rendered render(RelationshipReasoningContext context, String direction, FeatureRegistry registry) {
        RelationshipReasoningContext.View view = context.view(direction);
        StringBuilder user = new StringBuilder();
        user.append("DIRECTION ").append(direction).append('\n');
        user.append("CHOOSER ").append(view.chooserId()).append('\n');
        user.append("TARGET ").append(view.targetId()).append('\n');
        user.append("PROMPT ").append(VERSION).append('\n');
        user.append("DIGEST ").append(context.digest()).append('\n');
        user.append("FEATURES\n");
        List<String> codes = new ArrayList<>();
        for (RelationshipReasoningContext.EvidenceItem item : view.evidence()) codes.add(item.code());
        for (RelationshipReasoningContext.ItemView item : view.items()) {
            codes.add(item.preferenceCode());
            codes.add(item.targetFeatureCode());
        }
        codes.stream().distinct().sorted().forEach(code -> describe(user, registry, code));
        user.append("EVIDENCE\n");
        for (RelationshipReasoningContext.EvidenceItem item : view.evidence()) {
            user.append(item.role().toUpperCase()).append(" owner=").append(item.userId())
                    .append(" code=").append(item.code())
                    .append(" value=").append(item.value() == null ? "" : item.value())
                    .append(" importance=").append(item.importance() == null ? "" : item.importance())
                    .append(" ref=").append(item.ref()).append('\n');
        }
        user.append("ITEMS\n");
        for (RelationshipReasoningContext.ItemView item : view.items()) {
            user.append("ITEM direction=").append(item.direction())
                    .append(" preference=").append(item.preferenceCode())
                    .append(" target=").append(item.targetFeatureCode())
                    .append(" satisfaction=").append(item.satisfaction())
                    .append(" reason=").append(item.reasonCode())
                    .append(" importance=").append(item.importance()).append('\n');
        }
        user.append("LIMITS\n");
        for (String limit : context.semanticLimits()) user.append("LIMIT ").append(limit).append('\n');
        return new Rendered(VERSION, hash(), SYSTEM, user.toString(), context.digest(), view);
    }

    private static void describe(StringBuilder user, FeatureRegistry registry, String code) {
        FeatureDefinition definition = registry.all().stream().filter(item -> item.featureCode().equals(code)).findFirst().orElse(null);
        if (definition == null) {
            user.append("FEATURE code=").append(code).append(" legal=false\n");
            return;
        }
        user.append("FEATURE code=").append(code)
                .append(" name=").append(definition.displayName())
                .append(" legalValues=").append(String.join("|", definition.allowedValues().stream().sorted().toList()))
                .append(" sensitivity=").append(definition.sensitivity().name())
                .append('\n');
    }

    public static String registryVersion(FeatureAlignmentRegistry alignments) {
        return alignments.featureRegistryVersion();
    }
}
