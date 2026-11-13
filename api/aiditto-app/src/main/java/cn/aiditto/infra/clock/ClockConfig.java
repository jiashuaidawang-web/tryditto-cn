package cn.aiditto.infra.clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Configuration
public class ClockConfig {
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Business dates (birthdays, weeks) are Shanghai calendar dates. */
    public static LocalDate shanghaiDate(Instant now) {
        return now.atZone(SHANGHAI).toLocalDate();
    }
}
