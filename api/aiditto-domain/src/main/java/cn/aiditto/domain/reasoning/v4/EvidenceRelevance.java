package cn.aiditto.domain.reasoning.v4;

/** Whether a citation actually belongs to the preference and target feature of this claim. */
public enum EvidenceRelevance {
    RELEVANT,
    UNRELATED,
    INCOMPLETE,
    ABSENT,
    MISSING_AS_FACT
}
