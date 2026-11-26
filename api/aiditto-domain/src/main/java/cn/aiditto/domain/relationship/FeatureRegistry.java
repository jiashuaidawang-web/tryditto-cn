package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.Sensitivity;
import cn.aiditto.domain.relationship.RelationshipTypes.ValueType;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Version {@code features-1}. Codes come from questions.json or from a named future slot that the
 * projector leaves empty. Nothing here is an MBTI axis, a rank, or a score of how good a person is.
 */
public final class FeatureRegistry {
    public static final String VERSION = "features-1";

    private final Map<String, FeatureDefinition> byCode;

    private FeatureRegistry(Map<String, FeatureDefinition> byCode) {
        this.byCode = Map.copyOf(byCode);
    }

    public static FeatureRegistry v1() {
        Map<String, FeatureDefinition> m = new TreeMap<>();
        add(m, selfEnumSet("relationship.goal", "关系目标",
                "lifePartner", "serious", "casual", "friends", "notSure"));
        add(m, selfEnumSet("lifestyle.hobbies", "兴趣",
                "cooking", "music", "travel", "reading", "gym", "gaming", "movies", "art"));
        add(m, selfEnumSet("lifestyle.cooking", "做饭方式", "italian", "asian", "mexican", "comfort", "baking", "grilling", "healthy", "brunch"));
        add(m, selfEnumSet("lifestyle.music", "常听的音乐", "pop", "hiphop", "indie", "electronic", "rnb", "rock", "country", "jazz", "classical", "kpop", "latin"));
        add(m, selfEnumSet("lifestyle.travel", "旅行方式", "backpacking", "luxury", "foodie", "adventure", "beach", "city", "solo", "road"));
        add(m, selfEnumSet("lifestyle.reading", "阅读类型", "litfic", "fantasy", "scifi", "mystery", "romance", "memoir", "selfhelp", "poetry", "classics", "smut"));
        add(m, selfEnumSet("lifestyle.gym", "运动方式", "lifting", "running", "yoga", "pilates", "crossfit", "boxing", "dance", "cycling", "climbing"));
        add(m, selfEnumSet("lifestyle.gaming", "游戏类型", "fps", "rpg", "sports", "moba", "strategy", "indie", "cozy", "fighting", "mmo"));
        add(m, selfEnumSet("lifestyle.movies", "影像偏好", "indie", "horror", "romcom", "action", "scifi", "documentary", "a24", "foreign", "anime", "reality"));
        add(m, selfEnumSet("lifestyle.art", "创作媒介", "drawing", "painting", "digital", "pottery", "sculpture", "collage", "crafts", "mixed"));
        add(m, selfEnum("matching.pace", "筛选节奏", "faster", "steady", "fewer", "theOne"));
        add(m, bool("life.student", "是否在读"));
        add(m, selfEnum("education.year", "年级", "freshman", "sophomore", "junior", "senior", "master", "phd", "other"));
        add(m, selfEnum("education.level", "学历", "highSchool", "college", "graduate"));
        add(m, selfEnum("spending.first_date", "第一次见面的花费口径", "under20", "20to50", "50to100", "100plus"));
        add(m, text("occupation.text", "自述近况"));
        add(m, text("lifestyle.stated_interest", "自述兴趣原文"));
        add(m, selfEnumSet("support.help_wanted", "希望被帮忙的事",
                "approaching", "convo", "intoMe", "confident", "whatIWant", "theDate", "justDate"));
        add(m, text("opening.message", "自述开场白"));
        add(m, def("behavior.scenario.signal", "情景选择中的行为倾向", ValueType.ENUM,
                Set.of("low", "steady", "high"), null, null, Set.of(Section.BEHAVIOR),
                Set.of(EvidenceSource.MODEL_INFERENCE, EvidenceSource.SELF_REPORTED), Sensitivity.ORDINARY));
        add(m, preferenceRange("partner.age", "希望对方的年龄段", 18, 80));
        add(m, preferenceRange("partner.height", "希望对方的身高范围", 120, 230));
        add(m, preference("partner.education", "对对方学历的要求", ValueType.ENUM, Set.of("atLeastMine"), null, null));
        add(m, sensitiveRef("partner.gender", "希望对方的性别"));
        add(m, futureEnum("self.social.initiative", "自述社交主动性", Section.SELF, "low", "steady", "high"));
        add(m, futureEnum("partner.social.initiative", "希望对方的社交主动性", Section.PREFERENCE, "low", "steady", "high"));
        add(m, futureEnum("self.communication", "自述沟通习惯", Section.SELF, "quiet", "direct", "frequent"));
        add(m, futureEnum("partner.communication", "希望对方的沟通习惯", Section.PREFERENCE, "quiet", "direct", "frequent"));
        add(m, futureEnum("self.exploration", "自述探索倾向", Section.SELF, "low", "steady", "high"));
        add(m, futureEnum("self.pace", "自述相处节奏", Section.SELF, "slow", "steady", "fast"));
        add(m, futureEnum("partner.pace", "希望对方的相处节奏", Section.PREFERENCE, "slow", "steady", "fast"));
        add(m, futureEnum("partner.support", "希望得到的情感支持", Section.PREFERENCE, "space", "steady", "close"));
        add(m, futureEnum("self.schedule", "自述作息", Section.SELF, "early", "flexible", "late"));
        add(m, futureEnum("partner.schedule", "希望对方的作息", Section.PREFERENCE, "early", "flexible", "late"));
        return new FeatureRegistry(m);
    }

    public String version() { return VERSION; }

    public FeatureDefinition require(String code) {
        FeatureDefinition found = byCode.get(code);
        if (found == null) throw new IllegalArgumentException("unknown feature " + code);
        return found;
    }

    public Collection<FeatureDefinition> all() { return byCode.values(); }

    public void checkValue(FeatureDefinition definition, FeatureValue value) {
        if (value instanceof FeatureValue.Unbounded || value instanceof FeatureValue.Withheld) return;
        if (value instanceof FeatureValue.ControlledRef) {
            if (definition.sensitivity() != Sensitivity.SENSITIVE_REF) throw new IllegalArgumentException("controlled ref on an ordinary feature");
            return;
        }
        if (definition.sensitivity() == Sensitivity.SENSITIVE_REF) {
            throw new IllegalArgumentException("sensitive value was copied");
        }
        switch (definition.valueType()) {
            case BOOLEAN -> {
                if (!(value instanceof FeatureValue.Bool)) throw new IllegalArgumentException("expected boolean");
            }
            case ENUM -> {
                if (!(value instanceof FeatureValue.EnumOne one) || !definition.allowedValues().contains(one.value())) {
                    throw new IllegalArgumentException("enum outside the registry");
                }
            }
            case ENUM_SET -> {
                if (!(value instanceof FeatureValue.EnumMany many) || !definition.allowedValues().containsAll(many.values())) {
                    throw new IllegalArgumentException("enum set outside the registry");
                }
            }
            case NUMBER -> {
                if (!(value instanceof FeatureValue.Num n)) throw new IllegalArgumentException("expected number");
                inRange(definition, n.value());
            }
            case RANGE -> {
                if (!(value instanceof FeatureValue.Span span)) throw new IllegalArgumentException("expected range");
                inRange(definition, span.min());
                inRange(definition, span.max());
            }
            case TEXT -> {
                if (!(value instanceof FeatureValue.Text text)) throw new IllegalArgumentException("expected text");
                if (text.value().length() > 60) throw new IllegalArgumentException("text is too long");
                if (SensitiveText.containsContact(text.value())) throw new IllegalArgumentException("text contains contact data");
            }
        }
    }

    private static void inRange(FeatureDefinition definition, double value) {
        if (definition.min() != null && value < definition.min()) throw new IllegalArgumentException("below minimum");
        if (definition.max() != null && value > definition.max()) throw new IllegalArgumentException("above maximum");
    }

    private static void add(Map<String, FeatureDefinition> m, FeatureDefinition definition) {
        m.put(definition.featureCode(), definition);
    }

    private static FeatureDefinition selfEnumSet(String code, String name, String... allowed) {
        return def(code, name, ValueType.ENUM_SET, Set.of(allowed), null, null, Set.of(Section.SELF),
                Set.of(EvidenceSource.DIRECT_ANSWER, EvidenceSource.SELF_REPORTED), Sensitivity.ORDINARY);
    }

    private static FeatureDefinition selfEnum(String code, String name, String... allowed) {
        return def(code, name, ValueType.ENUM, Set.of(allowed), null, null, Set.of(Section.SELF),
                Set.of(EvidenceSource.DIRECT_ANSWER, EvidenceSource.SELF_REPORTED), Sensitivity.ORDINARY);
    }

    private static FeatureDefinition bool(String code, String name) {
        return def(code, name, ValueType.BOOLEAN, Set.of(), null, null, Set.of(Section.SELF),
                Set.of(EvidenceSource.DIRECT_ANSWER), Sensitivity.ORDINARY);
    }

    private static FeatureDefinition text(String code, String name) {
        if ("occupation.text".equals(code) || code.startsWith("opening.")) {
            Set<Section> sections = code.startsWith("opening.") ? Set.of(Section.BEHAVIOR) : Set.of(Section.SELF);
            return def(code, name, ValueType.TEXT, Set.of(), null, null, sections,
                    Set.of(EvidenceSource.DIRECT_ANSWER), Sensitivity.SENSITIVE_REF);
        }
        Set<Section> sections = Set.of(Section.SELF);
        return def(code, name, ValueType.TEXT, Set.of(), null, null, sections,
                Set.of(EvidenceSource.DIRECT_ANSWER, EvidenceSource.SELF_REPORTED), Sensitivity.ORDINARY);
    }

    private static FeatureDefinition preferenceRange(String code, String name, double min, double max) {
        return def(code, name, ValueType.RANGE, Set.of(), min, max, Set.of(Section.PREFERENCE),
                Set.of(EvidenceSource.DIRECT_ANSWER), Sensitivity.ORDINARY);
    }

    private static FeatureDefinition preference(String code, String name, ValueType type, Set<String> allowed, Double min, Double max) {
        return def(code, name, type, allowed, min, max, Set.of(Section.PREFERENCE),
                Set.of(EvidenceSource.DIRECT_ANSWER), Sensitivity.ORDINARY);
    }

    private static FeatureDefinition sensitiveRef(String code, String name) {
        return def(code, name, ValueType.TEXT, Set.of(), null, null, Set.of(Section.PREFERENCE),
                Set.of(EvidenceSource.DIRECT_ANSWER), Sensitivity.SENSITIVE_REF);
    }

    /**
     * A slot for a later direct answer, a provisional model note, or a real observation.
     * The projector does not fill these. Model output cannot use the memory section.
     */
    private static FeatureDefinition futureEnum(String code, String name, Section section, String... allowed) {
        if (section == Section.PREFERENCE) {
            return def(code, name, ValueType.ENUM, Set.of(allowed), null, null, Set.of(Section.PREFERENCE),
                    Set.of(EvidenceSource.SELF_REPORTED, EvidenceSource.DIRECT_ANSWER, EvidenceSource.MODEL_INFERENCE),
                    Sensitivity.ORDINARY);
        }
        return def(code, name, ValueType.ENUM, Set.of(allowed), null, null,
                Set.of(Section.SELF, Section.MEMORY),
                Set.of(EvidenceSource.SELF_REPORTED, EvidenceSource.MODEL_INFERENCE, EvidenceSource.FEEDBACK_OBSERVATION),
                Sensitivity.ORDINARY);
    }

    private static FeatureDefinition def(String code, String name, ValueType type, Set<String> allowed, Double min, Double max,
                                         Set<Section> sections, Set<EvidenceSource> sources, Sensitivity sensitivity) {
        return new FeatureDefinition(code, name, type, allowed, min, max, sections, sources, sensitivity, 1);
    }
}
