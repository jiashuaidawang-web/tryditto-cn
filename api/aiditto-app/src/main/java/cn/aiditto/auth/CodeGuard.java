package cn.aiditto.auth;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.config.AppProperties;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Shared code bookkeeping for SMS and email (技术方案 §6.3): send limits, demo-code check, 5 wrong tries → 15 min lock.
 * Demo mode never sends anything; the accepted code is DEMO_LOGIN_CODE.
 */
@Component
public class CodeGuard {
    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration LOCK_WINDOW = Duration.ofMinutes(15);
    static final int MAX_WRONG = 5;
    static final int MAX_PER_DAY = 10;
    static final int MAX_PER_IP_MINUTE = 5;

    private final AuthCodeMapper codes;
    private final Crypto crypto;
    private final AppProperties props;
    private final Clock clock;

    public CodeGuard(AuthCodeMapper codes, Crypto crypto, AppProperties props, Clock clock) {
        this.codes = codes;
        this.crypto = crypto;
        this.props = props;
        this.clock = clock;
        if (props.demoMode() && (props.demoLoginCode() == null || !props.demoLoginCode().matches("^\\d{6}$"))) {
            throw new IllegalStateException("DEMO_LOGIN_CODE must be 6 digits when DEMO_MODE=true");
        }
    }

    /** Records a "sent" code after the rate limits pass. {@code tooFrequent} is the channel's 429 code. */
    public void recordSend(String channel, byte[] target, String ip, ErrorCode tooFrequent) {
        requireDemo();
        Instant now = Times.millis(clock.instant());
        AuthCode last = codes.selectOne(new QueryWrapper<AuthCode>().eq("channel", channel).eq("target_hash", target)
                .orderByDesc("sent_at").last("LIMIT 1"));
        if (last != null && last.sentAt.plus(COOLDOWN).isAfter(now)) {
            throw DomainException.tooFrequent(tooFrequent, Duration.between(now, last.sentAt.plus(COOLDOWN)).toSeconds() + 1);
        }
        List<AuthCode> today = codes.selectList(new QueryWrapper<AuthCode>().eq("channel", channel).eq("target_hash", target)
                .gt("sent_at", now.minus(Duration.ofDays(1))).orderByAsc("sent_at"));
        if (today.size() >= MAX_PER_DAY) {
            throw DomainException.tooFrequent(tooFrequent, Duration.between(now, today.get(0).sentAt.plus(Duration.ofDays(1))).toSeconds() + 1);
        }
        if (ip != null && codes.selectCount(new QueryWrapper<AuthCode>().eq("ip", ip).gt("sent_at", now.minusSeconds(60))) >= MAX_PER_IP_MINUTE) {
            throw DomainException.tooFrequent(tooFrequent, 60);
        }
        insert(channel, target, ip, now);
    }

    /** True when {@code code} is right. Wrong codes count towards the lock; the 5th wrong one answers 429. */
    public boolean check(String channel, byte[] target, String code, String ip) {
        requireDemo();
        Instant now = Times.millis(clock.instant());
        ensureNotLocked(channel, target, now);
        if (constantTimeEquals(code, props.demoLoginCode())) {
            codes.update(null, new UpdateWrapper<AuthCode>().eq("channel", channel).eq("target_hash", target)
                    .set("attempts", 0).setSql("expires_at = LEAST(expires_at, {0})", now));
            return true;
        }
        AuthCode latest = codes.selectOne(new QueryWrapper<AuthCode>().eq("channel", channel).eq("target_hash", target)
                .orderByDesc("sent_at").last("LIMIT 1"));
        if (latest == null) latest = insert(channel, target, ip, now);
        codes.update(null, new UpdateWrapper<AuthCode>().eq("id", latest.id).setSql("attempts = attempts + 1").set("last_attempt_at", now));
        ensureNotLocked(channel, target, now);
        return false;
    }

    private void ensureNotLocked(String channel, byte[] target, Instant now) {
        List<AuthCode> recent = codes.selectList(new QueryWrapper<AuthCode>().eq("channel", channel).eq("target_hash", target)
                .gt("last_attempt_at", now.minus(LOCK_WINDOW)));
        int wrong = recent.stream().mapToInt(c -> c.attempts).sum();
        if (wrong >= MAX_WRONG) {
            Instant last = recent.stream().map(c -> c.lastAttemptAt).max(Instant::compareTo).orElse(now);
            throw DomainException.tooFrequent(ErrorCode.AUTH_CODE_LOCKED, Duration.between(now, last.plus(LOCK_WINDOW)).toSeconds() + 1);
        }
    }

    private AuthCode insert(String channel, byte[] target, String ip, Instant now) {
        AuthCode c = new AuthCode();
        c.channel = channel;
        c.targetHash = target;
        c.codeHash = crypto.hash("code:" + props.demoLoginCode());
        c.sentAt = now;
        c.expiresAt = now.plus(CODE_TTL);
        c.attempts = 0;
        c.ip = ip;
        codes.insert(c);
        return c;
    }

    private void requireDemo() {
        if (!props.demoMode()) throw new DomainException(ErrorCode.SMS_UNAVAILABLE, "真实短信和邮件尚未接入");
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
