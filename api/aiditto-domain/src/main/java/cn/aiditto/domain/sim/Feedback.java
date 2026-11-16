package cn.aiditto.domain.sim;

import java.util.List;

/**
 * 一次模拟真人反馈（FeedbackOracle 的输出）。
 *
 * @param reasonTag 结构化原因标签（{@code good:<dim>} / {@code bad:<dim>}），不含隐藏真值数值
 * @param freeText 口语化反馈文本，带同义改写与噪声
 * @param flags ambivalent / emotional / terse
 */
public record Feedback(boolean meetAgain, int rating, String reasonTag, String freeText,
                       double noise, List<String> flags) {
    public Feedback {
        flags = flags == null ? List.of() : List.copyOf(flags);
    }
}
