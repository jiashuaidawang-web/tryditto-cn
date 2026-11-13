package cn.aiditto.domain.questionnaire;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What `POST /api/onboarding/complete` requires (技术方案 §6.4) and which email the code page verifies (flow.ts). */
public final class OnboardingRules {

    public static final int MIN_PHOTOS = 2;
    public static final List<Integer> DEFAULT_AGE_RANGE = List.of(18, 24);

    private OnboardingRules() {}

    public static boolean isStudent(Map<String, Object> answers) {
        return "yes".equals(answers.get("isStudent"));
    }

    /** Student who took "not a student? continue without a school email" — flow.ts switches them to the professional tail. */
    public static boolean escapedSchoolEmail(Map<String, Object> answers) {
        return isStudent(answers) && Boolean.TRUE.equals(answers.get(QuestionCatalog.NO_SCHOOL_EMAIL));
    }

    /** flow.ts emailToVerify: the school email unless the user escaped or is not a student. */
    public static String emailToVerify(Map<String, Object> answers) {
        boolean school = isStudent(answers) && !escapedSchoolEmail(answers);
        Object v = answers.get(school ? "schoolEmail" : "personalEmail");
        return v instanceof String s ? s.strip() : "";
    }

    /** Question ids still missing for matching. Returns an empty list when onboarding may complete. */
    public static List<String> missing(Map<String, Object> answers, int photoCount, boolean emailVerified) {
        List<String> out = new ArrayList<>();
        for (String id : List.of("isStudent", "name", "gender", "expectedGender", "birthday", "hobbies", "location",
                "relationshipLooking", "matchingStyle")) {
            if (isBlank(answers.get(id))) out.add(id);
        }
        if (isStudent(answers) && !escapedSchoolEmail(answers) && isBlank(answers.get("schoolYear"))) out.add("schoolYear");
        if (photoCount < MIN_PHOTOS) out.add("photos");
        if (emailToVerify(answers).isEmpty()) {
            out.add(isStudent(answers) && !escapedSchoolEmail(answers) ? "schoolEmail" : "personalEmail");
        } else if (!emailVerified) {
            out.add("emailVerify");
        }
        return out;
    }

    private static boolean isBlank(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof List<?> l) return l.isEmpty();
        return false;
    }
}
