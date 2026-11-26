package cn.aiditto.domain.relationship;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * What an ordinary relationship snapshot is allowed to contain.
 * Free text is not on this list: a regex cannot prove that an open sentence has no
 * phone number, id number, student id, address, or private URL. Those answers stay
 * in their original store and the snapshot keeps a fixed reference, or nothing.
 */
public final class OrdinarySnapshot {
    public static final String PROFILE_SCOPE = "profile-self";
    public static final String MATCHING_SCOPE = "matching";
    public static final String MODEL_EXPORT_SCOPE = "model-export";

    public static final Set<String> CONTROLLED_REFS = Set.of(
            "profile.expected_genders_enc",
            "onboarding_answer.occupation",
            "onboarding_answer.first_message");

    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z][A-Za-z0-9._:-]{0,64}$");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{6,}");

    private OrdinarySnapshot() {}

    public static boolean safeToken(String value) {
        return value != null && TOKEN.matcher(value).matches() && !LONG_NUMBER.matcher(value).find();
    }

    public static boolean allowedRef(String ref) {
        return CONTROLLED_REFS.contains(ref);
    }
}
