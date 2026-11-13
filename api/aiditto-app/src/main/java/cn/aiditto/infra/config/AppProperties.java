package cn.aiditto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("aiditto")
public record AppProperties(
        boolean demoMode,
        String demoLoginCode,
        List<String> adminPhones,
        String inviteBase,
        Crypto crypto,
        Photos photos) {

    public AppProperties {
        adminPhones = adminPhones == null ? List.of() : adminPhones.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        inviteBase = inviteBase == null || inviteBase.isBlank() ? "jianyimian.cn" : inviteBase;
    }

    public record Crypto(String dataKey, String hashPepper, boolean allowDevKeys) {}

    public record Photos(String root, boolean accelRedirect, long maxBytes) {}
}
