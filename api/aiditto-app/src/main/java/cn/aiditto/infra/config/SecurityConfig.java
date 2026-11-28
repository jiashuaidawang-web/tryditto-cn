package cn.aiditto.infra.config;

import cn.aiditto.auth.BearerTokenFilter;
import cn.aiditto.auth.SessionService;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.web.Problems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Stateless bearer auth (技术方案 §9 鉴权). No cookies, so no CSRF. {@code /api/admin/**} needs ADMIN; everything
 * else under {@code /api} needs a session except login and the public lookups below.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, SessionService sessions) throws Exception {
        http.csrf(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new BearerTokenFilter(sessions), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/sms/send", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/places/search").permitAll()
                        // Signed URLs for <img src>; the controller checks the signature or the bearer session.
                        .requestMatchers(HttpMethod.GET, "/api/photos/*/raw").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/match-lab/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> Problems.write(res, ErrorCode.UNAUTHENTICATED, null))
                        .accessDeniedHandler((req, res, ex) -> Problems.write(res, ErrorCode.FORBIDDEN, null)));
        return http.build();
    }

    /** Keeps Boot from generating a default user with a logged password. */
    @Bean
    UserDetailsService noUsers() {
        return new InMemoryUserDetailsManager();
    }
}
