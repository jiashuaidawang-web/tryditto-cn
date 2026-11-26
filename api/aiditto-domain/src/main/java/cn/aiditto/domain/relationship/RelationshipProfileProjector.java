package cn.aiditto.domain.relationship;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Answers to a relationship snapshot. Direct facts only.
 * Missing answers stay absent. 不限 is unbounded. 不愿透露 is withheld.
 * This does not call a model and does not read or write {@code ProfileProjector}.
 */
public final class RelationshipProfileProjector {
    /** Question ids the projector reads. Anything else, including free-text branches, stays out of the snapshot. */
    public static final Set<String> STORED_QUESTIONS = Set.of(
            "hobbies", "cookingStyle", "musicSound", "travelStyle", "readingGenres",
            "gymTrainingStyle", "gamingGenres", "moviesContent", "artMediums",
            "relationshipLooking", "matchingStyle", "isStudent", "schoolYear",
            "educationLevel", "firstDateBudget", "datingHelp", "expectedAgeRange",
            "heightRange", "educationImportance", "expectedGender", "occupation", "firstMessage");

    private static final Set<String> HOBBIES = Set.of(
            "cooking", "music", "travel", "reading", "gym", "gaming", "movies", "art");

    private RelationshipProfileProjector() {}

    public static RelationshipProfileSnapshot project(long userId, Map<String, Object> answers, FeatureRegistry registry) {
        return project(userId, answers, null, registry);
    }

    public static RelationshipProfileSnapshot project(long userId, Map<String, Object> answers,
                                                      QuestionCatalog catalog, FeatureRegistry registry) {
        List<ProfileEvidence> evidence = new ArrayList<>();
        if (answers != null) {
            hobbies(userId, answers, evidence, registry);
            branches(userId, answers, evidence, registry);
            enums(userId, answers, evidence, registry);
            texts(userId, answers, evidence, registry);
            preferences(userId, answers, catalog, evidence, registry);
        }
        return RelationshipProfile.of(userId, evidence, registry).snapshot(registry);
    }

    private static void hobbies(long userId, Map<String, Object> answers, List<ProfileEvidence> evidence, FeatureRegistry registry) {
        List<String> known = new ArrayList<>();
        for (String value : strings(answers.get("hobbies"))) {
            if (HOBBIES.contains(value)) known.add(value);
        }
        if (!known.isEmpty()) {
            evidence.add(ProfileEvidence.answer(userId, "lifestyle.hobbies", Section.SELF,
                    new FeatureValue.EnumMany(known), "hobbies", registry));
        }
    }

    private static void branches(long userId, Map<String, Object> answers, List<ProfileEvidence> evidence, FeatureRegistry registry) {
        Set<String> picked = Set.copyOf(strings(answers.get("hobbies")));
        branch(userId, picked, "cooking", answers.get("cookingStyle"), "lifestyle.cooking", evidence, registry);
        branch(userId, picked, "music", answers.get("musicSound"), "lifestyle.music", evidence, registry);
        branch(userId, picked, "travel", answers.get("travelStyle"), "lifestyle.travel", evidence, registry);
        branch(userId, picked, "reading", answers.get("readingGenres"), "lifestyle.reading", evidence, registry);
        branch(userId, picked, "gym", answers.get("gymTrainingStyle"), "lifestyle.gym", evidence, registry);
        branch(userId, picked, "gaming", answers.get("gamingGenres"), "lifestyle.gaming", evidence, registry);
        branch(userId, picked, "movies", answers.get("moviesContent"), "lifestyle.movies", evidence, registry);
        branch(userId, picked, "art", answers.get("artMediums"), "lifestyle.art", evidence, registry);
    }

    private static void branch(long userId, Set<String> picked, String hobby, Object raw, String feature,
                               List<ProfileEvidence> evidence, FeatureRegistry registry) {
        if (!picked.contains(hobby)) return;
        List<String> values = new ArrayList<>();
        FeatureDefinition definition = registry.require(feature);
        for (String value : strings(raw)) {
            if (definition.allowedValues().contains(value)) values.add(value);
        }
        if (values.isEmpty()) return;
        String source = switch (feature) {
            case "lifestyle.cooking" -> "cookingStyle";
            case "lifestyle.music" -> "musicSound";
            case "lifestyle.travel" -> "travelStyle";
            case "lifestyle.reading" -> "readingGenres";
            case "lifestyle.gym" -> "gymTrainingStyle";
            case "lifestyle.gaming" -> "gamingGenres";
            case "lifestyle.movies" -> "moviesContent";
            case "lifestyle.art" -> "artMediums";
            default -> feature;
        };
        evidence.add(ProfileEvidence.answer(userId, feature, Section.SELF, new FeatureValue.EnumMany(values), source, registry));
    }

    private static void enums(long userId, Map<String, Object> answers, List<ProfileEvidence> evidence, FeatureRegistry registry) {
        List<String> goals = filter(registry, "relationship.goal", strings(answers.get("relationshipLooking")));
        if (!goals.isEmpty()) {
            evidence.add(ProfileEvidence.answer(userId, "relationship.goal", Section.SELF,
                    new FeatureValue.EnumMany(goals), "relationshipLooking", registry));
        }
        String pace = one(answers.get("matchingStyle"));
        if (registry.require("matching.pace").allowedValues().contains(pace)) {
            evidence.add(ProfileEvidence.answer(userId, "matching.pace", Section.SELF, new FeatureValue.EnumOne(pace), "matchingStyle", registry));
        }
        if ("yes".equals(answers.get("isStudent")) || "no".equals(answers.get("isStudent"))) {
            evidence.add(ProfileEvidence.answer(userId, "life.student", Section.SELF,
                    new FeatureValue.Bool("yes".equals(answers.get("isStudent"))), "isStudent", registry));
        }
        String year = one(answers.get("schoolYear"));
        if (registry.require("education.year").allowedValues().contains(year)) {
            evidence.add(ProfileEvidence.answer(userId, "education.year", Section.SELF, new FeatureValue.EnumOne(year), "schoolYear", registry));
        }
        String education = one(answers.get("educationLevel"));
        if ("preferNot".equals(education)) {
            evidence.add(ProfileEvidence.answer(userId, "education.level", Section.SELF, new FeatureValue.Withheld(), "educationLevel", registry));
        } else if (registry.require("education.level").allowedValues().contains(education)) {
            evidence.add(ProfileEvidence.answer(userId, "education.level", Section.SELF, new FeatureValue.EnumOne(education), "educationLevel", registry));
        }
        String budget = one(answers.get("firstDateBudget"));
        if ("noPref".equals(budget)) {
            evidence.add(ProfileEvidence.answer(userId, "spending.first_date", Section.SELF, new FeatureValue.Unbounded(), "firstDateBudget", registry));
        } else if (registry.require("spending.first_date").allowedValues().contains(budget)) {
            evidence.add(ProfileEvidence.answer(userId, "spending.first_date", Section.SELF, new FeatureValue.EnumOne(budget), "firstDateBudget", registry));
        }
        List<String> help = filter(registry, "support.help_wanted", strings(answers.get("datingHelp")));
        if (!help.isEmpty()) {
            evidence.add(ProfileEvidence.answer(userId, "support.help_wanted", Section.SELF,
                    new FeatureValue.EnumMany(help), "datingHelp", registry));
        }
    }

    private static void texts(long userId, Map<String, Object> answers, List<ProfileEvidence> evidence, FeatureRegistry registry) {
        if (present(answers.get("occupation"))) {
            evidence.add(ProfileEvidence.answer(userId, "occupation.text", Section.SELF,
                    new FeatureValue.ControlledRef("onboarding_answer.occupation"), "occupation", registry));
        }
        if (present(answers.get("firstMessage"))) {
            evidence.add(ProfileEvidence.build(userId, "opening.message", Section.BEHAVIOR,
                    new FeatureValue.ControlledRef("onboarding_answer.first_message"),
                    EvidenceSource.DIRECT_ANSWER, "firstMessage", EvidenceIds.CATALOG, 1.0, EvidenceStatus.CONFIRMED,
                    null, null, null, RelationshipTypes.BehaviorKind.SELF_REPORTED, null, registry));
        }
    }

    private static boolean present(Object raw) {
        if (raw instanceof String text) return !text.isBlank();
        return Boolean.TRUE.equals(raw) || raw instanceof List<?> list && !list.isEmpty();
    }

    private static void preferences(long userId, Map<String, Object> answers, QuestionCatalog catalog,
                                    List<ProfileEvidence> evidence, FeatureRegistry registry) {
        List<Integer> ages = ints(answers.get("expectedAgeRange"));
        if (ages.size() == 2) {
            evidence.add(preference(userId, "partner.age", new FeatureValue.Span(ages.get(0), ages.get(1)),
                    Operator.BETWEEN, Importance.SUPER, ConstraintType.DOCUMENTED_EXISTING_RULE, "expectedAgeRange", registry));
        }
        if (answers.get("heightRange") instanceof Map<?, ?> height) {
            if (Boolean.TRUE.equals(height.get("any"))) {
                evidence.add(preference(userId, "partner.height", new FeatureValue.Unbounded(),
                        Operator.ANY, Importance.NONE, ConstraintType.UNBOUNDED, "heightRange", registry));
            } else if (number(height.get("min")) != null && number(height.get("max")) != null) {
                evidence.add(preference(userId, "partner.height", new FeatureValue.Span(number(height.get("min")), number(height.get("max"))),
                        Operator.BETWEEN, Importance.SUPER, ConstraintType.DOCUMENTED_EXISTING_RULE, "heightRange", registry));
            }
        }
        String education = one(answers.get("educationImportance"));
        if ("none".equals(education)) {
            evidence.add(preference(userId, "partner.education", new FeatureValue.Unbounded(),
                    Operator.ANY, Importance.NONE, ConstraintType.UNBOUNDED, "educationImportance", registry));
        } else if ("kinda".equals(education)) {
            evidence.add(preference(userId, "partner.education", new FeatureValue.EnumOne("atLeastMine"),
                    Operator.AT_LEAST, Importance.KINDA, ConstraintType.SOFT, "educationImportance", registry));
        } else if ("super".equals(education)) {
            evidence.add(preference(userId, "partner.education", new FeatureValue.EnumOne("atLeastMine"),
                    Operator.AT_LEAST, Importance.SUPER, ConstraintType.DOCUMENTED_EXISTING_RULE, "educationImportance", registry));
        }
        List<String> genders = strings(answers.get("expectedGender"));
        if (!genders.isEmpty()) {
            if (catalog != null && !catalog.isSensitive("expectedGender")) {
                throw new IllegalStateException("expectedGender left the sensitive set");
            }
            if (genders.contains("everyone")) {
                evidence.add(preference(userId, "partner.gender", new FeatureValue.Unbounded(),
                        Operator.ANY, Importance.NONE, ConstraintType.UNBOUNDED, "expectedGender", registry));
            } else {
                evidence.add(preference(userId, "partner.gender", new FeatureValue.ControlledRef("profile.expected_genders_enc"),
                        Operator.CONTROLLED_REF, Importance.SUPER, ConstraintType.DOCUMENTED_EXISTING_RULE, "expectedGender", registry));
            }
        }
    }

    private static ProfileEvidence preference(long userId, String feature, FeatureValue claim, Operator operator,
                                              Importance importance, ConstraintType constraint, String source,
                                              FeatureRegistry registry) {
        return ProfileEvidence.build(userId, feature, Section.PREFERENCE, claim, EvidenceSource.DIRECT_ANSWER,
                source, EvidenceIds.CATALOG, 1.0, EvidenceStatus.CONFIRMED, operator, importance, constraint, null, null, registry);
    }

    private static List<String> filter(FeatureRegistry registry, String feature, List<String> values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (registry.require(feature).allowedValues().contains(value)) out.add(value);
        }
        return out;
    }

    private static List<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) out.add(text);
        }
        return out;
    }

    private static String one(Object raw) {
        return raw instanceof String text ? text : "";
    }

    private static List<Integer> ints(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Integer> out = new ArrayList<>();
        for (Object item : list) {
            Double value = number(item);
            if (value == null || value != Math.rint(value)) return List.of();
            out.add(value.intValue());
        }
        return out;
    }

    private static Double number(Object raw) {
        if (raw instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue();
        return null;
    }
}
