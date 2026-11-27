package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilityDecision;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry;
import cn.aiditto.domain.compatibility.v4.FeatureMatchContribution;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.MatchingFeatureGate;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.Sensitivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * The legal snapshot a directional agent may see. It is built only from synthetic profiles
 * and the M02 decision. Withheld values, sensitive references, and free text on the candidate
 * are not copied. A sealed context has no profile rows.
 */
public record RelationshipReasoningContext(
        long userA,
        long userB,
        long profileVersionA,
        long profileVersionB,
        long generationA,
        long generationB,
        List<EvidenceItem> evidence,
        List<ItemView> items,
        List<String> conflicts,
        List<String> unknown,
        List<String> contradictions,
        List<String> semanticLimits,
        String promptVersion,
        String digest,
        boolean sealed,
        List<String> refusalReasons) {

    public RelationshipReasoningContext {
        evidence = List.copyOf(evidence);
        items = List.copyOf(items);
        conflicts = List.copyOf(conflicts);
        unknown = List.copyOf(unknown);
        contradictions = List.copyOf(contradictions);
        semanticLimits = List.copyOf(semanticLimits);
        refusalReasons = List.copyOf(refusalReasons);
        if (promptVersion == null || digest == null) throw new IllegalArgumentException("context");
    }

    public record EvidenceItem(long userId, String role, String code, String value, String importance, String ref) {}

    public record ItemView(String direction, String preferenceCode, String targetFeatureCode,
                           String satisfaction, String reasonCode, String importance) {}

    public static RelationshipReasoningContext from(CompatibilitySubject left, CompatibilitySubject right,
                                                    CompatibilityDecision decision, FeatureAlignmentRegistry alignments) {
        List<String> refusal = new ArrayList<>(decision.reasons());
        if (!decision.reasoningEligible()) {
            return sealed(left, right, refusal);
        }
        FeatureRegistry registry = alignments.features();
        List<EvidenceItem> evidence = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        addPerson(registry, left, evidence, unknown, contradictions);
        addPerson(registry, right, evidence, unknown, contradictions);
        List<ItemView> items = new ArrayList<>();
        for (FeatureMatchContribution row : decision.pair().evidenceContributions()) {
            items.add(new ItemView(row.direction(), row.preferenceCode(), row.targetFeatureCode(),
                    row.satisfaction().name(), row.reasonCode(), row.importance()));
            if ("UNKNOWN".equals(row.satisfaction().name())) unknown.add(row.direction() + ":" + row.preferenceCode());
            if ("CONTESTED".equals(row.satisfaction().name())) contradictions.add(row.direction() + ":" + row.preferenceCode());
        }
        List<String> limits = new ArrayList<>();
        limits.add("潜在互补不是已验证契合。");
        limits.add("潜在冲突不是硬约束，模型不能新增硬约束。");
        limits.add("缺失特征不能推断成性格、性别角色或职业形象。");
        limits.add("不能声称一方一定会喜欢另一方，也不能给出恋爱成功概率。");
        for (FeatureAlignmentRegistry.Hypothesis hypothesis : alignments.unverifiedHypotheses()) {
            limits.add("未验证假设：" + hypothesis.preferenceCode() + "=" + hypothesis.preferenceValue()
                    + " 不能当成 " + hypothesis.selfCode() + "=" + hypothesis.selfValue() + "。原因：" + hypothesis.reason());
        }
        String canonical = canonical(left, right, evidence, items, unknown, contradictions, limits);
        return new RelationshipReasoningContext(left.userId(), right.userId(), left.profileVersion(), right.profileVersion(),
                left.generation(), right.generation(), evidence, items,
                decision.pair().strongPreferenceConflicts(), distinct(unknown), distinct(contradictions), limits,
                ReasoningPrompt.VERSION, sha256(canonical), false, List.copyOf(refusal));
    }

    public View view(String direction) {
        long chooser = "AB".equals(direction) ? userA : userB;
        long target = "AB".equals(direction) ? userB : userA;
        List<EvidenceItem> visible = evidence.stream()
                .filter(item -> ("preference".equals(item.role()) && item.userId() == chooser)
                        || ("self".equals(item.role()) && item.userId() == target))
                .toList();
        List<ItemView> rows = items.stream().filter(item -> direction.equals(item.direction())).toList();
        return new View(direction, chooser, target, visible, rows);
    }

    public record View(String direction, long chooserId, long targetId, List<EvidenceItem> evidence, List<ItemView> items) {
        public boolean allows(String ref) {
            return evidence.stream().anyMatch(item -> item.ref().equals(ref));
        }
    }

    private static void addPerson(FeatureRegistry registry, CompatibilitySubject person, List<EvidenceItem> evidence,
                                  List<String> unknown, List<String> contradictions) {
        for (ProfileFeature feature : person.self().features()) {
            FeatureDefinition definition = find(registry, feature.featureCode());
            if (definition == null || definition.sensitivity() == Sensitivity.SENSITIVE_REF) continue;
            if (!definition.sections().contains(Section.SELF)) continue;
            if (feature.value() instanceof FeatureValue.Withheld || feature.status() == FeatureStatus.PROVISIONAL
                    || !MatchingFeatureGate.eligibleForOfficialScore(person.selfSupport().get(feature.featureCode()))) {
                unknown.add("self:" + person.userId() + ":" + feature.featureCode());
                continue;
            }
            if (feature.status() == FeatureStatus.CONTESTED) {
                contradictions.add("self:" + person.userId() + ":" + feature.featureCode());
                continue;
            }
            if (feature.status() != FeatureStatus.CONFIRMED) continue;
            evidence.add(new EvidenceItem(person.userId(), "self", feature.featureCode(), text(feature.value()), null,
                    ref(person, feature.featureCode(), "self")));
        }
        for (PreferenceTarget preference : person.preference().targets()) {
            FeatureDefinition definition = find(registry, preference.featureCode());
            if (definition == null || definition.sensitivity() == Sensitivity.SENSITIVE_REF) continue;
            if (preference.targetValue() instanceof FeatureValue.Withheld || preference.status() == FeatureStatus.PROVISIONAL
                    || !MatchingFeatureGate.eligibleForOfficialScore(person.preferenceSupport().get(preference.featureCode()))) {
                continue;
            }
            if (preference.status() != FeatureStatus.CONFIRMED || preference.importance() == null) continue;
            evidence.add(new EvidenceItem(person.userId(), "preference", preference.featureCode(), text(preference.targetValue()),
                    preference.importance().name(), ref(person, preference.featureCode(), "preference")));
        }
    }

    static String ref(CompatibilitySubject person, String code, String role) {
        return "u" + person.userId() + ":" + code + ":v" + person.profileVersion() + ":" + role;
    }

    static String text(FeatureValue value) {
        if (value == null) return "";
        if (value instanceof FeatureValue.EnumOne one) return one.value();
        if (value instanceof FeatureValue.EnumMany many) return String.join(",", many.values());
        return "";
    }

    private static RelationshipReasoningContext sealed(CompatibilitySubject left, CompatibilitySubject right, List<String> reasons) {
        return new RelationshipReasoningContext(left.userId(), right.userId(), left.profileVersion(), right.profileVersion(),
                left.generation(), right.generation(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                ReasoningPrompt.VERSION, sha256("sealed:" + left.userId() + ":" + right.userId()), true, reasons);
    }

    private static String canonical(CompatibilitySubject left, CompatibilitySubject right, List<EvidenceItem> evidence,
                                    List<ItemView> items, List<String> unknown, List<String> contradictions, List<String> limits) {
        StringBuilder out = new StringBuilder();
        out.append(left.userId()).append('|').append(left.profileVersion()).append('|').append(left.generation()).append('\n');
        out.append(right.userId()).append('|').append(right.profileVersion()).append('|').append(right.generation()).append('\n');
        evidence.stream().sorted(Comparator.comparing(EvidenceItem::ref)).forEach(item -> out.append(item.ref()).append('|')
                .append(item.value()).append('|').append(item.importance()).append('\n'));
        items.forEach(item -> out.append(item.direction()).append('|').append(item.preferenceCode()).append('|')
                .append(item.satisfaction()).append('\n'));
        unknown.forEach(item -> out.append("unknown|").append(item).append('\n'));
        contradictions.forEach(item -> out.append("contradiction|").append(item).append('\n'));
        limits.forEach(item -> out.append("limit|").append(item).append('\n'));
        return out.toString();
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().distinct().toList();
    }

    private static FeatureDefinition find(FeatureRegistry registry, String code) {
        return registry.all().stream().filter(item -> item.featureCode().equals(code)).findFirst().orElse(null);
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
