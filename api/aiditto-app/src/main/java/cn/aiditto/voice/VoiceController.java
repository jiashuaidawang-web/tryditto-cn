package cn.aiditto.voice;

import cn.aiditto.auth.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 自适应情景采集 API（治本工程：攒「本人说话方式」资产，让分身像主人）。
 *
 * 流程：POST /start → 拿第一题（含 A/B/C 选项，A=系统预测你最可能选的）
 *      → POST /answer 逐题作答（可只选、可自定义输入）
 *      → 系统据实生成下一题，或收敛/你主动 stop 时推断人格 + 语气样本入库。
 * 前端拿到的选项**不含预测排序**，防止被刷；预测命中率只作为内部校准信号。
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    private final VoiceSessionService sessions;

    public VoiceController(VoiceSessionService sessions) {
        this.sessions = sessions;
    }

    /** 开始（或续上）一次采集，返回当前题。 */
    @PostMapping("/start")
    Map<String, Object> start(@AuthenticationPrincipal AuthUser me) {
        return sessions.start(me.userId());
    }

    /** 作答一题。chosenKey 与 customText 二选一（都给时以自定义为准）。 */
    public record AnswerBody(String chosenKey, String customText) {}

    @PostMapping("/answer/{questionId}")
    Map<String, Object> answer(@AuthenticationPrincipal AuthUser me, @PathVariable long questionId,
                               @RequestBody AnswerBody body) {
        return sessions.answer(me.userId(), questionId,
                body == null ? null : body.chosenKey(),
                body == null ? null : body.customText());
    }

    /** 主动停止：基于已有作答推断，不必答完。 */
    @PostMapping("/stop/{sessionId}")
    Map<String, Object> stop(@AuthenticationPrincipal AuthUser me, @PathVariable long sessionId) {
        return sessions.stop(me.userId(), sessionId);
    }

    /** 看当前生效的人格推断（资产）。 */
    @GetMapping("/profile")
    Map<String, Object> profile(@AuthenticationPrincipal AuthUser me) {
        return sessions.latestPublicProfile(me.userId());
    }
}
