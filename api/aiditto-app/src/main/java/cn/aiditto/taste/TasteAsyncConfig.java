package cn.aiditto.taste;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TasteAsyncConfig {

    @Bean
    TasteExecutor tasteExecutor(@Value("${aiditto.taste.async-enabled:true}") boolean asyncEnabled) {
        return new TasteExecutor(asyncEnabled);
    }
}
