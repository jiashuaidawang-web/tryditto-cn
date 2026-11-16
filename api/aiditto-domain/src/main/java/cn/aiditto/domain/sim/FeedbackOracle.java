package cn.aiditto.domain.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 反馈 Oracle：按隐藏真值模拟真人反馈（仿真方案 §4.1 / §4.2）。
 *
 * 防自证循环：确定性函数 + 噪声，**不用系统的 LLM**；文本用同义改写模板与噪声，
 * 不共享裁判/画像模型的措辞。带口是心非、矛盾、极短、情绪化四类脏数据标记。
 */
public final class FeedbackOracle {

    private FeedbackOracle() {}

    public static Feedback of(double trueScore, HiddenTruth viewer, HiddenTruth partner, long seed) {
        Random rnd = new Random(seed ^ viewer.userId() * 31L ^ partner.userId());
        double noise = rnd.nextGaussian() * 0.03;
        double p = sigmoid((trueScore - 60.0) / 10.0);
        boolean again = rnd.nextDouble() < p;
        int rating = clampInt((int) Math.round(trueScore / 20.0 + rnd.nextGaussian() * 0.6), 1, 5);

        // reason_tag 指向「用户自己最看重 / 最不看重的维度」：这才是可学的个人审美信号。
        // 实验记录：曾按 §4.1 字面改成「这对真值贡献最大/最小维度」，12 周期实测
        // taste_recovery 0.907→0.775、pred-true ρ 0.814→0.671、固定池净收益 −0.135，
        // 明显更差，已回滚。该对照实现保留在 TrueScore.topContributionDim/worstContributionDim
        // 作证据链，不参与默认回放。
        // 口是心非组（§3.1）：嘴上强调身高/学历，真实反馈却全在夸幽默感与节奏感，
        // 差评则落在被高估的显性条件上——这正是「学到真实审美」要验证的样本。
        String dim;
        if (viewer.twoFaced()) {
            dim = again ? (rnd.nextBoolean() ? "humor" : "expressiveness")
                    : (rnd.nextBoolean() ? "height_sensitivity" : "look_weight");
        } else {
            dim = again ? topWeightDim(viewer.weights()) : bottomWeightDim(viewer.weights());
        }
        String reasonTag = (again ? "good:" : "bad:") + dim;

        int variant = Math.floorMod(rnd.nextInt(), 5); // 20% 无信息量回复
        boolean terse = variant == 0;
        boolean ambivalent = !terse && variant == 1;
        boolean emotional = !terse && !ambivalent && variant == 2 && !again;
        List<String> flags = new ArrayList<>();
        if (terse) flags.add("terse");
        if (ambivalent) flags.add("ambivalent");
        if (emotional) flags.add("emotional");

        String text = terse ? terseText(rnd)
                : ambivalent ? "人挺好的，但就是没啥感觉，说不上来哪不对"
                : emotional ? "这次真的有点失望，聊不到一块去"
                : again ? goodText(dim, rnd) : badText(dim, rnd);
        return new Feedback(again, rating, reasonTag, text, noise, List.copyOf(flags));
    }

    public static String topWeightDim(cn.aiditto.domain.taste.TasteWeights w) {
        String best = cn.aiditto.domain.taste.TasteWeights.DIMS.get(0);
        for (String d : cn.aiditto.domain.taste.TasteWeights.DIMS) {
            if (w.get(d) > w.get(best)) best = d;
        }
        return best;
    }

    public static String bottomWeightDim(cn.aiditto.domain.taste.TasteWeights w) {
        String worst = cn.aiditto.domain.taste.TasteWeights.DIMS.get(0);
        for (String d : cn.aiditto.domain.taste.TasteWeights.DIMS) {
            if (w.get(d) < w.get(worst)) worst = d;
        }
        return worst;
    }

    private static String goodText(String dim, Random rnd) {
        String core = switch (dim) {
            case "humor" -> "聊起来特别轻松，笑点很合";
            case "adventure" -> "都爱尝试没做过的事，节奏很搭";
            case "expressiveness" -> "一个爱说一个爱听，居然一点不冷场";
            case "warmth" -> "说话很舒服，没有压迫感";
            case "schedule" -> "作息挺合得来，约时间不费劲";
            case "calm" -> "相处很安静，不累";
            default -> "整体聊得挺舒服的";
        };
        return rnd.nextBoolean() ? core + "。" : core + "，还想再约一次。";
    }

    private static String badText(String dim, Random rnd) {
        String core = switch (dim) {
            case "humor" -> "条件都对，但就是没话题";
            case "schedule" -> "作息差太多，估计很难约上";
            case "calm" -> "节奏不太一样，有点累";
            case "expressiveness" -> "聊不太起来，有点冷场";
            default -> "条件都符合，可惜没什么化学反应";
        };
        return rnd.nextBoolean() ? core + "。" : core + "，应该不会再约了。";
    }

    private static String terseText(Random rnd) {
        return rnd.nextBoolean() ? "还行吧" : "一般";
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
