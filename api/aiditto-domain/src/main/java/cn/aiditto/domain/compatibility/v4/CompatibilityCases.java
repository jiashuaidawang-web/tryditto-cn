package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Eight fictional Match Lab stories. The JSON is the offline contract; this round does not build a UI. */
public final class CompatibilityCases {
    public static final String SOURCE = "synthetic-fixture:v4-m02-case";

    public record Story(String id, String title, PairCompatibilityResult result, String judgement) {}

    private CompatibilityCases() {}

    public static List<Story> eight() {
        BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();
        return List.of(
                story(engine, "M02-C1", "双方兴趣相似且偏好满足",
                        person(1, ideal(), ideal(), "music"),
                        person(2, ideal(), ideal(), "music"),
                        "两个方向都有定义的已知分，实验聚合可以比较。这不是喜欢概率。"),
                story(engine, "M02-C2", "兴趣不同但双向偏好满足",
                        person(3, ideal(), ideal(), "reading"),
                        person(4, ideal(), ideal(), "gym"),
                        "兴趣交集不是双向偏好满足的条件。"),
                story(engine, "M02-C3", "A 高分、B 低分",
                        person(5, ideal(), ideal(), "reading"),
                        person(6, ideal(), new String[]{"slow", "frequent", "high", "late"}, "reading"),
                        "只看 A→B 会把单向高分当成双向契合。最低方向分和调和平均都会压低它。"),
                story(engine, "M02-C4", "双方均为中等分",
                        person(7, ideal(), ideal(), "travel"),
                        person(8, new String[]{"slow", "frequent", "high", "early"},
                                new String[]{"slow", "frequent", "high", "early"}, "travel"),
                        "双方都只有一半强偏好被满足。实验门槛 60 不会把它当成通过。"),
                story(engine, "M02-C5", "共同兴趣但强偏好冲突",
                        person(9, ideal(), ideal(), "reading"),
                        person(10, new String[]{"fast", "frequent", "high", "late"},
                                new String[]{"fast", "frequent", "high", "late"}, "reading"),
                        "共同兴趣不能抵消强偏好冲突。硬约束没有失败，软冲突仍然是冲突。"),
                story(engine, "M02-C6", "高分但证据覆盖不足",
                        person(11, ideal(), ideal(), "art"),
                        person(12, new String[]{"slow", null, null, null}, ideal(), "art"),
                        "已知项可以很高，覆盖率仍然单独报告。覆盖不足时不发布高确定性的实验通过。"),
                story(engine, "M02-C7", "画像存在矛盾",
                        person(13, ideal(), ideal(), "music"),
                        contested(14),
                        "矛盾特征不记满足，也不记不满足，不能换成 0 分或 50 分。"),
                story(engine, "M02-C8", "硬约束否决",
                        person(15, ideal(), ideal(), "gym"),
                        veto(16),
                        "年龄硬约束失败后不生成可供后续推理使用的画像明细。"));
    }

    public static String json() {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"formulaVersion\": ").append(q(CompatibilityFormula.VERSION));
        out.append(",\n  \"registryVersion\": ").append(q(FeatureAlignmentRegistry.v1().featureRegistryVersion()));
        out.append(",\n  \"alignmentVersion\": ").append(q(FeatureAlignmentRegistry.VERSION));
        out.append(",\n  \"cases\": [\n");
        List<Story> stories = eight();
        for (int i = 0; i < stories.size(); i++) {
            Story story = stories.get(i);
            PairCompatibilityResult result = story.result();
            out.append("    {\n");
            field(out, 3, "id", q(story.id()), true);
            field(out, 3, "title", q(story.title()), true);
            field(out, 3, "userA", Long.toString(result.userA()), true);
            field(out, 3, "userB", Long.toString(result.userB()), true);
            field(out, 3, "profileVersionA", Long.toString(result.profileVersionA()), true);
            field(out, 3, "profileVersionB", Long.toString(result.profileVersionB()), true);
            field(out, 3, "generationA", Long.toString(result.generationA()), true);
            field(out, 3, "generationB", Long.toString(result.generationB()), true);
            field(out, 3, "status", q(result.status().name()), true);
            field(out, 3, "hardFilterResult", stringList(result.hardFilterResult()), true);
            field(out, 3, "scoreAB", num(result.scoreAB()), true);
            field(out, 3, "scoreBA", num(result.scoreBA()), true);
            field(out, 3, "coverageAB", num(result.coverageAB()), true);
            field(out, 3, "coverageBA", num(result.coverageBA()), true);
            field(out, 3, "arithmeticMean", num(result.arithmeticMean()), true);
            field(out, 3, "harmonicMean", num(result.harmonicMean()), true);
            field(out, 3, "minDirection", num(result.minDirection()), true);
            field(out, 3, "geometricMean", num(result.geometricMean()), true);
            field(out, 3, "harmonicWithCoverage", num(result.harmonicWithCoverage()), true);
            field(out, 3, "harmonicWithCoverageAndStrength", num(result.harmonicWithCoverageAndStrength()), true);
            field(out, 3, "strongPreferenceConflicts", stringList(result.strongPreferenceConflicts()), true);
            field(out, 3, "unknownFeatures", stringList(result.unknownFeatures()), true);
            out.append("      \"contributions\": [\n");
            List<FeatureMatchContribution> rows = result.evidenceContributions();
            for (int r = 0; r < rows.size(); r++) {
                FeatureMatchContribution row = rows.get(r);
                out.append("        {");
                out.append("\"direction\": ").append(q(row.direction()));
                out.append(", \"preferenceCode\": ").append(q(row.preferenceCode()));
                out.append(", \"targetFeatureCode\": ").append(q(row.targetFeatureCode()));
                out.append(", \"expectedValue\": ").append(q(row.expectedValue()));
                out.append(", \"actualValue\": ").append(q(row.actualValue()));
                out.append(", \"importance\": ").append(q(row.importance()));
                out.append(", \"operator\": ").append(q(row.operator()));
                out.append(", \"evidenceStatus\": ").append(q(row.evidenceStatus()));
                out.append(", \"satisfaction\": ").append(q(row.satisfaction().name()));
                out.append(", \"weight\": ").append(row.weight());
                out.append(", \"satisfactionValue\": ").append(num(row.satisfactionValue()));
                out.append(", \"reasonCode\": ").append(q(row.reasonCode()));
                out.append('}');
                if (r + 1 < rows.size()) out.append(',');
                out.append('\n');
            }
            out.append("      ],\n");
            field(out, 3, "judgement", q(story.judgement()), false);
            out.append("    }");
            if (i + 1 < stories.size()) out.append(',');
            out.append('\n');
        }
        out.append("  ]\n}\n");
        return out.toString();
    }

    public static void main(String[] args) {
        System.out.print(json());
    }

    private static Story story(BidirectionalCompatibilityEngine engine, String id, String title,
                               CompatibilitySubject left, CompatibilitySubject right, String judgement) {
        return new Story(id, title, engine.score(left, right, CompatibilityContext.experiment(
                BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW, List.of(left, right))), judgement);
    }

    private static String[] ideal() { return new String[]{"slow", "quiet", "low", "early"}; }

    private static CompatibilitySubject person(long id, String[] self, String[] preference, String hobby) {
        List<ProfileFeature> features = new ArrayList<>();
        List<PreferenceTarget> targets = new ArrayList<>();
        String[] selfCodes = {"self.pace", "self.communication", "self.social.initiative", "self.schedule"};
        String[] prefCodes = {"partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule"};
        for (int i = 0; i < selfCodes.length; i++) {
            if (self[i] == null) continue;
            features.add(CompatibilitySubject.feature(selfCodes[i], new FeatureValue.EnumOne(self[i]), FeatureStatus.CONFIRMED));
        }
        for (int i = 0; i < prefCodes.length; i++) {
            targets.add(CompatibilitySubject.preference(prefCodes[i], preference[i], Importance.SUPER));
        }
        return CompatibilitySubject.synthetic(id, id, 1, true, false, SOURCE, features, targets,
                Candidate.builder(id).hobbies(hobby).build());
    }

    private static CompatibilitySubject contested(long id) {
        List<ProfileFeature> features = new ArrayList<>();
        List<PreferenceTarget> targets = new ArrayList<>();
        for (String code : List.of("self.pace", "self.communication", "self.social.initiative", "self.schedule")) {
            features.add(CompatibilitySubject.feature(code, null, FeatureStatus.CONTESTED));
        }
        for (String code : List.of("partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule")) {
            targets.add(CompatibilitySubject.preference(code, Operator.EQ, null, null, ConstraintType.SOFT, FeatureStatus.CONTESTED));
        }
        return CompatibilitySubject.synthetic(id, id, 1, true, false, SOURCE, features, targets,
                Candidate.builder(id).hobbies("music").build());
    }

    private static CompatibilitySubject veto(long id) {
        CompatibilitySubject matched = person(id, ideal(), ideal(), "gym");
        Candidate older = matched.candidate().toBuilder().birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60).build();
        return new CompatibilitySubject(id, id, 1, true, false, SOURCE, matched.self(), matched.preference(),
                matched.selfSupport(), matched.preferenceSupport(), older);
    }

    private static void field(StringBuilder out, int indent, String name, String value, boolean comma) {
        out.append("  ".repeat(indent)).append('"').append(name).append("\": ").append(value);
        if (comma) out.append(',');
        out.append('\n');
    }

    private static String stringList(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(q(values.get(i)));
        }
        return out.append(']').toString();
    }

    private static String q(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String num(Double value) {
        if (value == null) return "null";
        if (value == Math.rint(value) && !value.isInfinite()) return Long.toString(value.longValue());
        return Double.toString(value);
    }
}
