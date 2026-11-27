package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.Sensitivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Builds the model-facing contract from {@link FeatureRegistry}. Deterministic facts stay out. */
public final class FeatureContractCatalog {
    private static final Map<String, Blurb> BLURBS = blurbs();

    private FeatureContractCatalog() {}

    public static List<FeatureContract> modelExtractable(FeatureRegistry registry) {
        List<FeatureContract> out = new ArrayList<>();
        for (FeatureDefinition definition : registry.all()) {
            if (!extractable(definition)) continue;
            Blurb blurb = BLURBS.getOrDefault(definition.featureCode(), Blurb.fallback(definition));
            out.add(new FeatureContract(
                    definition.featureCode(),
                    definition.displayName(),
                    blurb.definition(),
                    sections(definition),
                    definition.valueType().name(),
                    List.copyOf(definition.allowedValues().stream().sorted().toList()),
                    evidenceKinds(definition),
                    definition.allowedSources().stream().map(Enum::name).sorted().toList(),
                    blurb.examples(),
                    blurb.counters()));
        }
        out.sort(Comparator.comparing(FeatureContract::featureCode));
        return List.copyOf(out);
    }

    public static String render(List<FeatureContract> contracts) {
        StringBuilder out = new StringBuilder();
        for (FeatureContract contract : contracts) {
            out.append("featureCode=").append(contract.featureCode()).append('\n');
            out.append("displayName=").append(contract.displayName()).append('\n');
            out.append("semanticDefinition=").append(contract.semanticDefinition()).append('\n');
            out.append("allowedSections=").append(String.join(",", contract.allowedSections())).append('\n');
            out.append("valueType=").append(contract.valueType()).append('\n');
            out.append("allowedValues=").append(String.join(",", contract.allowedValues())).append('\n');
            out.append("allowedEvidenceKinds=").append(String.join(",", contract.allowedEvidenceKinds())).append('\n');
            out.append("permittedSourceTypes=").append(String.join(",", contract.permittedSourceTypes())).append('\n');
            out.append("explicitExamples=").append(String.join(" | ", contract.explicitExamples())).append('\n');
            out.append("counterExamples=").append(String.join(" | ", contract.counterExamples())).append('\n');
            out.append("---\n");
        }
        return out.toString();
    }

    static boolean extractable(FeatureDefinition definition) {
        if (definition.sensitivity() == Sensitivity.SENSITIVE_REF) return false;
        if (!definition.allowedSources().contains(EvidenceSource.MODEL_INFERENCE)) return false;
        return !sections(definition).isEmpty();
    }

    private static List<String> sections(FeatureDefinition definition) {
        return definition.sections().stream()
                .filter(section -> section != Section.MEMORY)
                .filter(section -> definition.allows(section, EvidenceSource.MODEL_INFERENCE))
                .map(Enum::name)
                .sorted()
                .toList();
    }

    private static List<String> evidenceKinds(FeatureDefinition definition) {
        if (definition.sections().contains(Section.BEHAVIOR) && !definition.sections().contains(Section.SELF)) {
            return List.of("scenario");
        }
        return List.of("statement");
    }

    private record Blurb(String definition, List<String> examples, List<String> counters) {
        static Blurb fallback(FeatureDefinition definition) {
            return new Blurb(definition.displayName(), List.of(), List.of());
        }
    }

    private static Map<String, Blurb> blurbs() {
        Map<String, Blurb> map = new java.util.HashMap<>();
        map.put("self.pace", blurb(
                "这个人自己相处或做事的节奏。slow 偏慢，steady 平稳，fast 偏快。",
                List.of("刚认识时我会先安静一阵", "我做事喜欢留出余量"),
                List.of("我喜欢节奏慢的人")));
        map.put("self.communication", blurb(
                "这个人自己说话的多少和方式。quiet 话少，direct 直说，frequent 话多。",
                List.of("熟了以后我一句一句慢慢说", "我习惯把想法直接讲出来"),
                List.of("我希望对方话少一点")));
        map.put("self.social.initiative", blurb(
                "这个人自己会不会先迈出社交的一步。low 偏被动，steady 看场合，high 常常先开口。",
                List.of("聚会里我通常等别人先找我", "认识新朋友时我会先打招呼"),
                List.of("我希望另一半先约我")));
        map.put("self.exploration", blurb(
                "这个人自己愿不愿意尝试没去过的地方或没做过的事。",
                List.of("周末我更想去没去过的小店", "我很少改已经熟悉的路线"),
                List.of("我希望对方爱尝试新事物")));
        map.put("self.schedule", blurb(
                "这个人自己的作息。early 偏早，flexible 不固定，late 偏晚。",
                List.of("我晚上十点前就想休息", "我常常后半夜还醒着"),
                List.of("我希望对方跟我一样早睡")));
        map.put("partner.social.initiative", blurb(
                "这个人希望伴侣在交往里谁先行动。high 希望对方更主动，low 希望对方少主动，steady 希望适中。",
                List.of("我更舒服的方式是对方先发出邀请", "我不太想被对方频繁来找"),
                List.of("我自己见面时会先打招呼")));
        map.put("partner.communication", blurb(
                "这个人希望伴侣怎样沟通。quiet 希望话少，direct 希望直说，frequent 希望经常聊。",
                List.of("我希望对方有事就明说", "我喜欢对方隔一阵就分享日常"),
                List.of("我自己说话很少")));
        map.put("partner.pace", blurb(
                "这个人希望伴侣的相处节奏。slow 希望慢一点，steady 希望平稳，fast 希望利落。",
                List.of("我希望另一半做事利落一点", "我希望关系走得慢一些"),
                List.of("我自己做事偏慢")));
        map.put("partner.support", blurb(
                "这个人希望从伴侣那里得到怎样的陪伴。space 要空间，steady 要稳定回应，close 要靠近。",
                List.of("我难过时希望对方就在旁边", "我需要对方给我单独待着的时间"),
                List.of("我自己不太会安慰人")));
        map.put("partner.schedule", blurb(
                "这个人希望伴侣的作息靠近哪一种。early 偏早，flexible 随意，late 偏晚。",
                List.of("我希望对方也能早一点休息", "我希望对方不介意我晚睡"),
                List.of("我自己习惯早起")));
        map.put("behavior.scenario.signal", blurb(
                "只有材料里已经给出情景的批准含义时才能使用。取值必须等于那个批准含义。",
                List.of("材料给出 meaning 为 steady 时，取值写 steady"),
                List.of("只看到选项字母 A")));
        return Map.copyOf(map);
    }

    private static Blurb blurb(String definition, List<String> examples, List<String> counters) {
        return new Blurb(definition, examples, counters);
    }
}
