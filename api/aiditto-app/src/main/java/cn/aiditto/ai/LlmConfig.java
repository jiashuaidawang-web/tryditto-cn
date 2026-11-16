package cn.aiditto.ai;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.LongSupplier;

/** 网关装配。mode=mock 时主备都是确定性实现，本机无密钥也能跑全链路与故障注入。 */
@Configuration
public class LlmConfig {

    @Bean("primaryLlmModel")
    LlmModel primaryLlmModel(LlmProperties p) {
        return p.live()
                ? new OpenAiCompatModel(p.primaryBaseUrl(), p.primaryApiKey(), p.primaryModel(),
                        p.timeoutMs(), p.primaryExtraBodyMap())
                : new MockLlmModel("mock-primary");
    }

    @Bean("fallbackLlmModel")
    LlmModel fallbackLlmModel(LlmProperties p) {
        return p.live() && p.fallbackAvailable()
                ? new OpenAiCompatModel(p.fallbackBaseUrl(), p.fallbackApiKey(), p.fallbackModel(),
                        p.timeoutMs(), p.fallbackExtraBodyMap())
                : new MockLlmModel("mock-fallback");
    }

    @Bean
    LlmCallLogger llmCallLogger(JdbcTemplate jdbc) {
        return new JdbcLlmCallLogger(jdbc);
    }

    @Bean
    LlmGateway llmGateway(@Qualifier("primaryLlmModel") LlmModel primary,
                          @Qualifier("fallbackLlmModel") LlmModel fallback,
                          LlmCallLogger logger,
                          LlmProperties props,
                          IdentifierGenerator ids) {
        LongSupplier nextId = () -> ((Number) ids.nextId(new Object())).longValue();
        return new LlmGateway(primary, fallback, logger, props,
                s -> s != null && !s.isBlank(), nextId, null);
    }
}
