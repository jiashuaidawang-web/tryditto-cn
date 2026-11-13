package cn.aiditto.infra.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address. With {@code server.forward-headers-strategy=native} Tomcat already replaces
 * {@code getRemoteAddr()} with the X-Forwarded-For value nginx sets, and only trusts internal proxies.
 */
public final class ClientIp {
    private ClientIp() {}

    public static String of(HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        return ip == null ? null : ip.substring(0, Math.min(45, ip.length()));
    }
}
