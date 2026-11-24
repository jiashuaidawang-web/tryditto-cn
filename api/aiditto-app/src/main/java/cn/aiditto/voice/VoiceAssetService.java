package cn.aiditto.voice;

import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把用户的「语气资产」（人格推断 + 本人原话样本）读成一段**受控、脱敏**的分身提示词补充。
 *
 * 治本最后一公里（创始人 2026-11-18）：分身 Agent 不再只靠问卷脑补的 persona_text 说话，
 * 而是叠加 voice_profile 的语气风格 + voice_sample 的本人原话——让分身用接近本人的语气回答/提问。
 *
 * 零回归：没有语气资产的用户返回空串，分身行为与以前完全一致。
 * 合规：原话加密存储，读出后过 scrub（邮箱/手机/密钥/链接），再截长度；只取本人自愿输入的样本。
 */
@Service
public class VoiceAssetService {
    private static final int MAX_TOTAL = 320;   // 补充串总长上限，控制 token 成本
    private static final int MAX_SAMPLES = 3;   // 最多带 3 条原话

    private final JdbcTemplate jdbc;
    private final Crypto crypto;

    public VoiceAssetService(JdbcTemplate jdbc, Crypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    /** 给某用户生成语气补充串；无资产返回空串。 */
    public String voiceHint(long userId) {
        StringBuilder out = new StringBuilder();
        try {
            String style = activeVoiceStyle(userId);
            if (!style.isBlank()) out.append("说话风格：").append(scrub(style));
            List<String> samples = recentSamples(userId);
            if (!samples.isEmpty()) {
                if (out.length() > 0) out.append("。");
                out.append("本人常这样表达：").append(String.join("；", samples));
            }
        } catch (RuntimeException e) {
            return "";  // 任何异常都不影响精排主流程
        }
        String s = out.toString();
        return s.length() > MAX_TOTAL ? s.substring(0, MAX_TOTAL) : s;
    }

    private String activeVoiceStyle(long userId) {
        Object raw;
        try {
            raw = jdbc.queryForObject("""
                    SELECT traits_json FROM voice_profile
                    WHERE user_id=? AND is_active ORDER BY version DESC LIMIT 1
                    """, Object.class, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return "";
        }
        if (raw == null) return "";
        try {
            String json = String.valueOf(raw);
            Object v = Json.readMap(json).get("voice_style");
            return v == null ? "" : String.valueOf(v);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private List<String> recentSamples(long userId) {
        return jdbc.query("""
                SELECT text_enc FROM voice_sample
                WHERE user_id=? AND source IN ('scenario_custom','voice_seed')
                ORDER BY created_at DESC LIMIT ?
                """, (rs, n) -> {
                    byte[] enc = rs.getBytes("text_enc");
                    if (enc == null) return null;
                    String text = scrub(crypto.decrypt(enc));
                    if (text.isBlank()) return null;
                    // 单条也截长度，防止一条超长原话撑爆提示词
                    return text.length() > 80 ? text.substring(0, 80) : text;
                }, userId, MAX_SAMPLES).stream().filter(s -> s != null).toList();
    }

    /** 与 AgentRerankService.scrub 同口径的脱敏，防止本人原话里出现 PII。 */
    static String scrub(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\w.+-]+@[\\w.-]+", "[邮箱]")
                .replaceAll("1[3-9]\\d{9}", "[手机号]")
                .replaceAll("sk-[A-Za-z0-9]{16,}", "[密钥]")
                .replaceAll("https?://\\S+", "[链接]")
                .replaceAll("\\s+", " ").strip();
    }
}
