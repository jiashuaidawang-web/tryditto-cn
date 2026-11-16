package cn.aiditto.match;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.CopyValidator;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.ProfileFacts;
import cn.aiditto.domain.copy.VenueOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 7：AI 文案子图（技术方案 §8.2 / §8.5、Prompt 模板 4）。
 *
 * 双层容错，绝不把 LLM 故障变成学生可见的错误：
 *  - 网关层：主备切换 / 重试 / 熔断 / empty-content 翻倍（Step 3 已有）
 *  - 子图层：非法 JSON、缺字段、禁用词、编造引用、无效 cites、长度越界
 *            → 带着具体问题重试（默认 2 次）
 *  - 兜底层：全部失败返回 empty，由 {@link MatchCopyWriter} 落 TemplateCopy
 *
 * 绝不抛异常：周批 COPY 阶段任何一次文案失败都不能中断周三交付。
 */
@Service
public class AiCopyService {

    private static final Logger log = LoggerFactory.getLogger(AiCopyService.class);

    private final LlmGateway llm;
    private final MatchCopyProperties props;
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger invalidOutputs = new AtomicInteger();
    private final AtomicInteger degradedCalls = new AtomicInteger();
    private final AtomicInteger exhausted = new AtomicInteger();

    public AiCopyService(LlmGateway llm, MatchCopyProperties props) {
        this.llm = llm;
        this.props = props;
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(props.enabled());
    }

    public String promptVersion() {
        return props.promptVersion();
    }

    /** 成功返回 AI 文案；任何失败返回 empty（调用方走模板兜底）。 */
    public Optional<MatchCopy> generate(ProfileFacts low, ProfileFacts high,
                                        CopyContext ctx, List<VenueOption> venues) {
        if (!enabled() || venues == null || venues.isEmpty()) return Optional.empty();
        VenueOption venue = venues.get(0);
        String baseUser = AiCopyGenerator.user(low, high, ctx, venues);
        StringBuilder hint = new StringBuilder();
        try {
            for (int i = 0; i < props.maxAttempts(); i++) {
                LlmRequest req = new LlmRequest("match_copy", props.promptVersion(),
                        AiCopyGenerator.system(), baseUser + hint,
                        props.maxTokens(), props.temperature(), true, true);
                attempts.incrementAndGet();
                LlmResult r = llm.call(req);
                if (!r.ok()) {
                    degradedCalls.incrementAndGet();
                    // 网关已耗尽主备（或返回空内容），再打同一批请求没有意义
                    break;
                }
                AiCopyGenerator.Result parsed = AiCopyGenerator.parse(r.content(), ctx, venue.id(),
                        venue.defaultActivity(), venue.defaultExtra());
                if (parsed.ok()) {
                    accepted.incrementAndGet();
                    return Optional.of(parsed.copy());
                }
                invalidOutputs.incrementAndGet();
                // Issue.toString() 只有 path + detail；重试提示必须把规则名也带上，
                // 否则模型只知道"某个词违规"，不知道是禁用词还是编造引用，第二次仍会犯同类错。
                String summary = issuesForPrompt(parsed.issues());
                log.debug("ai copy rejected attempt={} issues={}", i + 1, clip(summary, 300));
                hint.append("\n\n上次输出被程序判为作废（第 ").append(i + 1)
                        .append(" 次），必须逐条修正：\n").append(clip(summary, 1200))
                        .append("\ncites 只能从【共同项清单】里选；引号内容必须逐字来自【可用词表】；")
                        .append("不要输出地点、venueId、评分、性别、年龄字段。");
            }
        } catch (RuntimeException e) {
            log.warn("ai copy failed -> template fallback: {}", e.getClass().getSimpleName());
            degradedCalls.incrementAndGet();
        }
        exhausted.incrementAndGet();
        return Optional.empty();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("copyAttempts", attempts.get());
        m.put("copyAccepted", accepted.get());
        m.put("copyInvalidOutputs", invalidOutputs.get());
        m.put("copyDegradedCalls", degradedCalls.get());
        m.put("copyExhausted", exhausted.get());
        return m;
    }

    public void reset() {
        attempts.set(0);
        accepted.set(0);
        invalidOutputs.set(0);
        degradedCalls.set(0);
        exhausted.set(0);
    }

    private static String issuesForPrompt(List<CopyValidator.Issue> issues) {
        return issues.stream()
                .map(i -> i.path() + " [" + i.rule() + "]: " + i.detail())
                .reduce((a, b) -> a + "；" + b)
                .orElse("输出不符合结构要求");
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}
