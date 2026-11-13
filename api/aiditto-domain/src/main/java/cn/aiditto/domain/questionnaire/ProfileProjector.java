package cn.aiditto.domain.questionnaire;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers → the structured profile the matcher reads (技术方案 §4.4 {@code profile}). Pure; no IO. */
public final class ProfileProjector {

    public record ProfileSnapshot(
            String name,
            String gender,
            List<String> expectedGenders,
            LocalDate birthday,
            int ageMin,
            int ageMax,
            Integer heightCm,
            boolean heightAny,
            Integer heightMin,
            Integer heightMax,
            boolean isStudent,
            String schoolYear,
            String schoolDomain,
            String occupation,
            String educationLevel,
            String educationImportance,
            String budget,
            String matchingStyle,
            List<String> relationshipLooking,
            List<String> hobbies,
            Map<String, Object> hobbyDetails,
            String placeId,
            String travelMode,
            Integer travelMinutes,
            String firstMessage) {}

    private ProfileProjector() {}

    public static ProfileSnapshot project(Map<String, Object> a, QuestionCatalog catalog) {
        List<Integer> ages = intList(a.get("expectedAgeRange"));
        if (ages.size() != 2) ages = OnboardingRules.DEFAULT_AGE_RANGE;
        Map<?, ?> height = a.get("height") instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> range = a.get("heightRange") instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> loc = a.get("location") instanceof Map<?, ?> m ? m : Map.of();
        List<String> hobbies = strList(a.get("hobbies"));

        Map<String, Object> details = new LinkedHashMap<>();
        for (String hobby : hobbies) {
            for (String qid : catalog.branchesOf(hobby)) {
                if (a.get(qid) != null) details.put(qid, a.get(qid));
            }
        }

        boolean student = OnboardingRules.isStudent(a);
        String schoolDomain = null;
        if (student && !OnboardingRules.escapedSchoolEmail(a) && a.get("schoolEmail") instanceof String se && se.contains("@")) {
            schoolDomain = se.substring(se.indexOf('@') + 1).toLowerCase();
        }

        return new ProfileSnapshot(
                str(a.get("name")),
                str(a.get("gender")),
                strList(a.get("expectedGender")),
                AnswerValidator.parseDate(a.get("birthday")),
                ages.get(0),
                ages.get(1),
                AnswerValidator.intOf(height.get("cm")),
                Boolean.TRUE.equals(range.get("any")),
                AnswerValidator.intOf(range.get("min")),
                AnswerValidator.intOf(range.get("max")),
                student,
                str(a.get("schoolYear")),
                schoolDomain,
                str(a.get("occupation")),
                str(a.get("educationLevel")),
                str(a.get("educationImportance")),
                str(a.get("firstDateBudget")),
                str(a.get("matchingStyle")),
                strList(a.get("relationshipLooking")),
                hobbies,
                details,
                str(loc.get("placeId")),
                str(loc.get("mode")),
                AnswerValidator.intOf(loc.get("minutes")),
                str(a.get("firstMessage")));
    }

    private static String str(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static List<String> strList(Object o) {
        if (!(o instanceof List<?> l)) return List.of();
        return l.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static List<Integer> intList(Object o) {
        if (!(o instanceof List<?> l)) return List.of();
        return l.stream().map(AnswerValidator::intOf).filter(java.util.Objects::nonNull).toList();
    }
}
