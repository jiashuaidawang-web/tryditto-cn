package cn.aiditto.domain.sim;

import cn.aiditto.domain.taste.TasteWeights;

import java.util.Set;

/**
 * 合成用户的隐藏真值。**系统全程不可见**：不得进入任何问卷答案、persona/taste 文本、
 * 提示词、日志或向量库；只有 sim 包与 Oracle 可读（仿真方案 §4.3 防自证循环）。
 *
 * @param weights 10 维真实审美权重
 * @param dealbreakers 真实红线（结构化标签，不是原文）
 * @param attributes 自身属性标签，供 Oracle 判定「对方红线是否被命中」
 * @param twoFaced 嘴上条件与真值故意冲突的样本（≥20%）
 */
public record HiddenTruth(long userId, TasteWeights weights, Set<String> dealbreakers,
                          Set<String> attributes, boolean twoFaced, String longTailTag) {

    public HiddenTruth {
        dealbreakers = dealbreakers == null ? Set.of() : Set.copyOf(dealbreakers);
        attributes = attributes == null ? Set.of() : Set.copyOf(attributes);
    }
}
