package cn.aiditto.it;

import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.MockLlmModel;
import cn.aiditto.domain.copy.BannedWords;
import cn.aiditto.match.MatchCard;
import cn.aiditto.match.SyntheticLoader;
import cn.aiditto.match.WeekRunner;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 7 端到端闸门（真 PG16）：开启 AI 文案后跑完整周批，验证
 *  - match_copy.source 真的出现 'ai'，并落 prompt_version；
 *  - AI 文案经 CopyValidator，禁用词 / 无效引用为 0；
 *  - 模型输出不可用时全量回落 TemplateCopy，周批仍 published（绝不空发周三）；
 *  - week.stats 同时含 SOLVE 与 COPY 的统计（merge 不覆盖）。
 *
 * 默认跳过；AIDITTO_STEP7_IT=1 开启。用 MockLlmModel 的按调用点脚本注入
 * 「合法模型输出」，不依赖外部 LLM。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_STEP7_IT", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiCopyIT extends ApiIT {

    /** 通用合法输出：cites 只用 location（恒为双方共有），不含引号/禁用词/联系方式，任何 pair 都能过校验。 */
    private static final String VALID_AI_JSON = """
            {"low":{"headline":"去见见{{partner}}吧",
                    "blurb":["ta 也在期待这次见面。","第一次见面不用准备太多，一杯饮品的时间刚刚好。"],
                    "openers":["问问 ta 这周最开心的一件事。","问问 ta 周末一般怎么过。"],
                    "reasons":[{"text":"你们想约会的地方离得不远，见一面很方便。","basis":"你们选的约会范围有交集","cites":["location"]},
                               {"text":"约在你们中间的地方，谁都不用跑太远。","basis":"你们各自能接受的路程","cites":["location"]}]},
             "high":{"headline":"{{partner}}也在期待这次见面",
                     "blurb":["ta 也在期待这次见面。","一杯饮品的时间，刚好够认识一个人。"],
                     "openers":["问问 ta 这周最开心的一件事。","问问 ta 周末一般怎么过。"],
                     "reasons":[{"text":"你们想约会的地方离得不远，见一面很方便。","basis":"你们选的约会范围有交集","cites":["location"]},
                                {"text":"约在你们中间的地方，谁都不用跑太远。","basis":"你们各自能接受的路程","cites":["location"]}]},
             "activity":"一杯饮品，约 45 分钟","extra":""}
            """;

    @DynamicPropertySource
    static void copyOn(DynamicPropertyRegistry r) {
        r.add("aiditto.match.copy.enabled", () -> "true");
        r.add("aiditto.match.copy.max-attempts", () -> "2");
        r.add("aiditto.llm.mode", () -> "mock");
        // 精排与本步无关，保持关闭以缩短 IT
        r.add("aiditto.match.agent.enabled", () -> "false");
    }

    @Autowired WeekRunner weeks;
    @Autowired JdbcTemplate jdbc;
    @Autowired SyntheticLoader loader;
    @Autowired MatchCard matchCard;
    @Autowired @Qualifier("primaryLlmModel") LlmModel primary;

    private MockLlmModel mock() {
        assertThat(primary).isInstanceOf(MockLlmModel.class);
        return (MockLlmModel) primary;
    }

    @Test
    @Order(1)
    void aiSourceIsWrittenAndEveryLinePassesContentSafety() {
        mock().clearScripts().respond("match_copy", VALID_AI_JSON);
        loader.load();
        Map<String, Object> out = weeks.runManual("bj", Instant.now(), true);
        System.out.println("[STEP7-IT] summary=" + out);

        Long weekId = jdbc.queryForObject(
                "SELECT MAX(id) FROM week WHERE pool='bj' AND trigger_kind='manual'", Long.class);
        assertThat(weekId).isNotNull();
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_copy c JOIN "match" m ON m.id=c.match_id WHERE m.week_id=?
                """, Long.class, weekId);
        Long ai = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_copy c JOIN "match" m ON m.id=c.match_id
                WHERE m.week_id=? AND c.source='ai'
                """, Long.class, weekId);
        System.out.println("[STEP7-IT] week=" + weekId + " copies=" + total + " ai=" + ai);
        assertThat(total).isGreaterThan(0);
        assertThat(ai).isEqualTo(total);

        Long versioned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_copy c JOIN "match" m ON m.id=c.match_id
                WHERE m.week_id=? AND c.source='ai' AND c.prompt_version='match-copy-v1'
                """, Long.class, weekId);
        assertThat(versioned).isEqualTo(ai);

        // 禁用词 / 联系方式：把整周所有文案拼起来逐条扫
        List<String> texts = jdbc.queryForList("""
                SELECT c.headline || ' ' || c.blurb::text || ' ' || c.reasons::text || ' ' || c.openers::text
                FROM match_copy c JOIN "match" m ON m.id=c.match_id WHERE m.week_id=?
                """, String.class, weekId);
        for (String t : texts) assertThat(BannedWords.find(t)).isEmpty();

        // 无效引用 = 0：所有 cites 必须落在双方共有字段里（location 恒有）
        Long badCites = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT jsonb_array_elements_text(r->'cites') AS cite
                  FROM match_copy c JOIN "match" m ON m.id=c.match_id,
                       jsonb_array_elements(c.reasons) r
                  WHERE m.week_id=?
                ) x WHERE cite <> 'location'
                """, Long.class, weekId);
        assertThat(badCites == null ? 0L : badCites).isZero();

        String stats = jdbc.queryForObject("SELECT stats::text FROM week WHERE id=?", String.class, weekId);
        System.out.println("[STEP7-IT] stats=" + stats);
        assertThat(stats).contains("\"pairs\"", "copyTemplateRate", "\"copyAi\"");
        assertThat(stats).doesNotContain("\"copyTemplateRate\": 1.0");

        // AI 标识端到端：match_copy.source='ai' 必须透到 MatchCard.reasons[].ai=true，
        // 前端据此显示「AI 生成」提示（本项目是 Agent 产品，如实展示而非隐藏）。
        Long aiUserId = jdbc.queryForObject("""
                SELECT c.viewer_id FROM match_copy c JOIN "match" m ON m.id=c.match_id
                WHERE m.week_id=? AND c.source='ai' ORDER BY c.viewer_id LIMIT 1
                """, Long.class, weekId);
        assertThat(aiUserId).isNotNull();
        Map<String, Object> card = matchCard.current(aiUserId, Instant.now());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reasons = (List<Map<String, Object>>) card.get("reasons");
        assertThat(reasons).isNotEmpty();
        assertThat(reasons).allSatisfy(r -> assertThat(r.get("ai")).isEqualTo(true));
        System.out.println("[STEP7-IT] reason.ai=true 透传成功 user=" + aiUserId + " reasons=" + reasons.size());
    }

    @Test
    @Order(2)
    void unusableModelOutputFallsBackToTemplateAndStillPublishes() {
        mock().clearScripts(); // 默认返回 {"mock":true,...} → 解析失败 → 全量模板兜底
        // 上一周批已 publish：match_side 的 open 状态与 pastPairs 会让同周再跑配不出新对。
        // 本用例只验「文案降级」，先清掉上一周批的派生态（无外键、不动用户与画像）。
        for (String table : List.of("match_side", "match_copy", "date_plan", "feedback",
                "recall_candidate", "\"match\"", "week_participant", "week")) {
            jdbc.update("DELETE FROM " + table);
        }
        loader.load();
        Map<String, Object> out = weeks.runManual("bj", Instant.now(), true);
        System.out.println("[STEP7-IT-DEGRADED] summary=" + out);

        Long weekId = jdbc.queryForObject(
                "SELECT MAX(id) FROM week WHERE pool='bj' AND trigger_kind='manual'", Long.class);
        Long template = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_copy c JOIN "match" m ON m.id=c.match_id
                WHERE m.week_id=? AND c.source='template'
                """, Long.class, weekId);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_copy c JOIN "match" m ON m.id=c.match_id WHERE m.week_id=?
                """, Long.class, weekId);
        System.out.println("[STEP7-IT-DEGRADED] week=" + weekId + " copies=" + total + " template=" + template);
        assertThat(total).isGreaterThan(0);
        assertThat(template).isEqualTo(total);
        String state = jdbc.queryForObject("SELECT state FROM week WHERE id=?", String.class, weekId);
        assertThat(state).isEqualTo("published");
        String stats = jdbc.queryForObject("SELECT stats::text FROM week WHERE id=?", String.class, weekId);
        System.out.println("[STEP7-IT-DEGRADED] stats=" + stats);
        assertThat(stats).contains("\"copyTemplateRate\": 1.0");
    }
}
