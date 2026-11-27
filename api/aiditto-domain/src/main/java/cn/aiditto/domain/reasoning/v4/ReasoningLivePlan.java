package cn.aiditto.domain.reasoning.v4;

import java.util.List;
import java.util.Map;

/**
 * Real-model runs need an experiment key and a named model. Absence is NOT_RUN.
 * This class does not read a secret file and does not call a model.
 */
public final class ReasoningLivePlan {
    public static final List<String> MODELS = List.of("LongCat-2.5", "deepseek-flash", "hy4-flash", "qwen4-max");

    private ReasoningLivePlan() {}

    public static String status(Map<String, String> environment) {
        String key = environment == null ? null : environment.get("M03_EXPERIMENT_KEY");
        String model = environment == null ? null : environment.get("M03_MODEL");
        if (key == null || key.isBlank() || model == null || model.isBlank()) return "NOT_RUN";
        if (!MODELS.contains(model)) return "NOT_RUN";
        return "CONFIGURED";
    }
}
