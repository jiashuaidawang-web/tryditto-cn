package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

/** Importance here is how strongly the user said the preference matters. It is not the education answer. */
public final class PreferenceWeights {
    private PreferenceWeights() {}

    public static int of(Importance importance) {
        if (importance == null) throw new IllegalArgumentException("importance");
        return switch (importance) {
            case SUPER -> CompatibilityFormula.SUPER;
            case KINDA -> CompatibilityFormula.KINDA;
            case NONE -> CompatibilityFormula.NONE;
        };
    }
}
