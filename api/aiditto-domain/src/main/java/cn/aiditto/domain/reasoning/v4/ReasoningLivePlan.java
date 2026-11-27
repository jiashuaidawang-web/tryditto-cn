package cn.aiditto.domain.reasoning.v4;

import java.util.List;
import java.util.Map;

/**
 * Real-model runs need an experiment key and a named model. Absence is NOT_RUN.
 * This class does not read a secret file and does not call a model.
 */
public final class ReasoningLivePlan {
    public static final List<String> MODELS = List.of("LongCat-2.5", "deepseek-flash", "hy4-flash", "qwen4-max");
    public static final int FULL_DIRECTION_CALLS = 224;
    public static final int SMOKE_RELATIONS = 10;
    public static final int CHALLENGE_RELATIONS = 36;
    public static final int MODEL_CALL_CAP = 320;
    public static final int MODEL_TOKEN_CAP = 1_500_000;
    /** Budget gate fixed before any live result. It is not the quality gate. */
    public static final double SMOKE_ENTRY_LEGAL_RATE = 0.80;

    private ReasoningLivePlan() {}

    public static String status(Map<String, String> environment) {
        String key = environment == null ? null : environment.get("M03_EXPERIMENT_KEY");
        String model = environment == null ? null : environment.get("M03_MODEL");
        if (key == null || key.isBlank() || model == null || model.isBlank()) return "NOT_RUN";
        if (!MODELS.contains(model)) return "NOT_RUN";
        return "CONFIGURED";
    }
}
