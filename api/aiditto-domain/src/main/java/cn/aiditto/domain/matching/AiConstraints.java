package cn.aiditto.domain.matching;

/**
 * Structured constraints extracted from "雷区" (技术方案 §7.2 rule 10, closed enum). Only pass a non-null
 * instance when {@code profile.ai_status = ok}. Null bounds mean "no constraint".
 */
public record AiConstraints(Integer minHeight, Integer maxHeight, Integer minAge, Integer maxAge,
                            boolean studentsOnly, boolean nonStudentsOnly) {
    public static final AiConstraints NONE = new AiConstraints(null, null, null, null, false, false);

    /** Whether a partner with these attributes satisfies the constraints. */
    public boolean accepts(int partnerHeight, int partnerAge, boolean partnerStudent) {
        if (minHeight != null && partnerHeight < minHeight) return false;
        if (maxHeight != null && partnerHeight > maxHeight) return false;
        if (minAge != null && partnerAge < minAge) return false;
        if (maxAge != null && partnerAge > maxAge) return false;
        if (studentsOnly && !partnerStudent) return false;
        return !(nonStudentsOnly && partnerStudent);
    }
}
