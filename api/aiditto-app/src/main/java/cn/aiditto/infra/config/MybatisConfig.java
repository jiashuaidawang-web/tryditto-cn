package cn.aiditto.infra.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisConfig {

    /** Single api instance: fixed snowflake ids skip MyBatis-Plus's host/MAC probe, which costs seconds at startup. */
    @Bean
    IdentifierGenerator identifierGenerator() {
        return new DefaultIdentifierGenerator(1, 1);
    }
}
