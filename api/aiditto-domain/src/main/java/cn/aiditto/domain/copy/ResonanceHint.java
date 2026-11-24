package cn.aiditto.domain.copy;

import java.util.ArrayList;
import java.util.List;

/**
 * 从裁判的深层共鸣信号（sim_report）提炼出的**受控**输入，用于「为什么是你们俩」的深层理由。
 *
 * 设计红线（C 端 + 合规 §8.1）：
 *  - 只含固定安全词表里的定性标签，绝不携带裁判 reason_text / 模拟对话原文 / 任何自由文本；
 *  - 只有裁判 confidence 足够、且非降级时才给出标签，避免把模型幻觉包装成"共鸣"；
 *  - 标签是双方共享的观察（不是某方隐私），可作为 CopyValidator 的合法 cites。
 *
 * 这样理由能写出「你们的聊天节奏很合拍」这类跨表面判断，而不是只有「你们都喜欢旅行」。
 */
public record ResonanceHint(List<String> traits, boolean reliable) {

    public static final String CITE = "resonance";

    private static final double MIN_CONFIDENCE = 0.6;
    private static final int MIN_DIM = 75;

    public ResonanceHint {
        traits = traits == null ? List.of() : List.copyOf(traits);
    }

    public static ResonanceHint none() {
        return new ResonanceHint(List.of(), false);
    }

    public boolean present() {
        return reliable && !traits.isEmpty();
    }

    /**
     * 从裁判维度分数提炼受控标签。只读取结构化数字，不读任何模型自由文本。
     *
     * @param dims       {"values":..,"chemistry":..,"vibe":..,"feasible":..}
     * @param confidence 裁判置信度 0-1
     * @param degraded   是否降级（降级不出标签）
     */
    public static ResonanceHint from(java.util.Map<String, Integer> dims, double confidence, boolean degraded) {
        if (degraded || dims == null || confidence < MIN_CONFIDENCE) return none();
        List<String> out = new ArrayList<>();
        if (ge(dims, "chemistry")) out.add("聊得来");
        if (ge(dims, "vibe")) out.add("节奏合拍");
        if (ge(dims, "values")) out.add("想法相近");
        return new ResonanceHint(out, !out.isEmpty());
    }

    private static boolean ge(java.util.Map<String, Integer> dims, String key) {
        Integer v = dims.get(key);
        return v != null && v >= MIN_DIM;
    }
}
