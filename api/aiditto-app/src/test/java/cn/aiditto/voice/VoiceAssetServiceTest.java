package cn.aiditto.voice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 语气资产注入的脱敏闸门：本人原话进分身提示词前必须去掉 PII。 */
class VoiceAssetServiceTest {

    @Test
    void scrubRemovesPii() {
        String raw = "你可以打我 13800138000 或邮箱 a.b@x.com，看 https://evil.cn 这个链接，sk-" + "A".repeat(20);
        String s = VoiceAssetService.scrub(raw);
        assertThat(s).contains("[手机号]").contains("[邮箱]").contains("[链接]").contains("[密钥]");
        assertThat(s).doesNotContain("13800138000").doesNotContain("a.b@x.com")
                .doesNotContain("https://evil.cn");
    }

    @Test
    void scrubNormalizesWhitespace() {
        assertThat(VoiceAssetService.scrub("  我  这个人\n比较直接  ")).isEqualTo("我 这个人 比较直接");
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(VoiceAssetService.scrub(null)).isEmpty();
        assertThat(VoiceAssetService.scrub("")).isEmpty();
    }

    @Test
    void simulatePromptDemandsVoiceReference() {
        assertThat(cn.aiditto.match.AiJudge.simulateSystem())
                .contains("【语气参考】")
                .contains("语气参考说话");
    }
}
