package cn.aiditto.auth;

import cn.aiditto.account.User;
import cn.aiditto.account.UserMapper;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opaque bearer tokens (技术方案 §6.2): 32 random bytes, base64url; the database keeps SHA-256(token). Lookups are
 * cached for 60 s in-process; every revoke evicts, so logout takes effect on the next request.
 */
@Service
public class SessionService {
    static final Duration TTL = Duration.ofDays(30);
    private static final Duration CACHE_FOR = Duration.ofSeconds(60);
    private static final Duration TOUCH_EVERY = Duration.ofHours(1);

    public record Issued(String token, Instant expiresAt) {}

    private record Cached(AuthUser user, Instant expiresAt, Instant cachedAt) {}

    private final AuthSessionMapper sessions;
    private final UserMapper users;
    private final Crypto crypto;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public SessionService(AuthSessionMapper sessions, UserMapper users, Crypto crypto, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.crypto = crypto;
        this.clock = clock;
    }

    public Issued issue(long userId, String userAgent, String ip) {
        Instant now = Times.millis(clock.instant());
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(crypto.randomBytes(32));
        AuthSession s = new AuthSession();
        s.tokenHash = tokenHash(token);
        s.userId = userId;
        s.createdAt = now;
        s.lastSeenAt = now;
        s.expiresAt = now.plus(TTL);
        s.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        s.ip = ip;
        sessions.insert(s);
        return new Issued(token, s.expiresAt);
    }

    public Optional<AuthUser> resolve(String token) {
        if (token == null || token.isBlank() || token.length() > 128) return Optional.empty();
        byte[] hash = tokenHash(token);
        String key = HexFormat.of().formatHex(hash);
        Instant now = clock.instant();
        Cached c = cache.get(key);
        if (c != null && c.cachedAt.plus(CACHE_FOR).isAfter(now) && c.expiresAt.isAfter(now)) return Optional.of(c.user);
        cache.remove(key);

        AuthSession s = sessions.selectOne(new QueryWrapper<AuthSession>().eq("token_hash", hash));
        if (s == null || s.revokedAt != null || !s.expiresAt.isAfter(now)) return Optional.empty();
        User u = users.selectById(s.userId);
        if (u == null || User.DELETING.equals(u.status) || "purged".equals(u.status) || "banned".equals(u.status)) return Optional.empty();

        Instant expires = s.expiresAt;
        if (s.lastSeenAt.plus(TOUCH_EVERY).isBefore(now)) {
            Instant t = Times.millis(now);
            expires = t.plus(TTL);
            sessions.update(null, new UpdateWrapper<AuthSession>().eq("id", s.id).set("last_seen_at", t).set("expires_at", expires));
        }
        AuthUser au = new AuthUser(u.id, s.id, "admin".equals(u.role));
        cache.put(key, new Cached(au, expires, now));
        return Optional.of(au);
    }

    public void revoke(long sessionId) {
        sessions.update(null, new UpdateWrapper<AuthSession>().eq("id", sessionId).isNull("revoked_at").set("revoked_at", Times.millis(clock.instant())));
        cache.values().removeIf(c -> c.user.sessionId() == sessionId);
    }

    public void revokeAll(long userId) {
        sessions.update(null, new UpdateWrapper<AuthSession>().eq("user_id", userId).isNull("revoked_at").set("revoked_at", Times.millis(clock.instant())));
        cache.values().removeIf(c -> c.user.userId() == userId);
    }

    static byte[] tokenHash(String token) {
        return Crypto.sha256(token.getBytes(StandardCharsets.US_ASCII));
    }
}
