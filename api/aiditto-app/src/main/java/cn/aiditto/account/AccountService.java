package cn.aiditto.account;

import cn.aiditto.auth.SessionService;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.config.AppProperties;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** User rows and the account lifecycle of 技术方案 §5.5 that M1 needs: create, deactivate, reactivate, delete. */
@Service
public class AccountService {
    static final Duration PURGE_AFTER = Duration.ofDays(30);
    private static final String INVITE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final UserMapper users;
    private final SessionService sessions;
    private final Crypto crypto;
    private final AppProperties props;
    private final Clock clock;

    public AccountService(UserMapper users, SessionService sessions, Crypto crypto, AppProperties props, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.crypto = crypto;
        this.props = props;
        this.clock = clock;
    }

    public User require(long userId) {
        User u = users.selectById(userId);
        if (u == null) throw new DomainException(ErrorCode.UNAUTHENTICATED);
        return u;
    }

    public byte[] phoneHash(String phone) {
        return crypto.hash(phone);
    }

    public String phoneOf(User u) {
        return u.phoneEnc == null ? null : crypto.decrypt(u.phoneEnc);
    }

    /** Finds or creates the user behind a phone, syncs the admin role, and brings a deactivated account back. */
    @Transactional
    public User loginUser(String phone, String ref) {
        Instant now = Times.millis(clock.instant());
        byte[] hash = phoneHash(phone);
        User u = users.selectOne(new QueryWrapper<User>().eq("phone_hash", hash));
        if (u == null) u = create(phone, hash, ref, now);
        if (User.DELETING.equals(u.status) || "purged".equals(u.status) || "banned".equals(u.status)) {
            throw new DomainException(ErrorCode.ACCOUNT_INACTIVE, User.DELETING.equals(u.status) ? "账号正在注销中" : "账号不可用");
        }
        String role = props.adminPhones().contains(phone) ? "admin" : "user";
        UpdateWrapper<User> up = new UpdateWrapper<User>().eq("id", u.id).set("last_login_at", now).set("role", role).set("updated_at", now);
        if (User.DEACTIVATED.equals(u.status)) {
            u.status = u.onboarded() ? User.ACTIVE : User.ONBOARDING;
            up.set("status", u.status).set("deactivated_at", null);
        }
        users.update(null, up);
        u.role = role;
        return u;
    }

    private User create(String phone, byte[] hash, String ref, Instant now) {
        User u = new User();
        u.phoneEnc = crypto.encrypt(phone);
        u.phoneHash = hash;
        u.role = "user";
        u.status = User.ONBOARDING;
        u.lang = "zh";
        u.isSynthetic = false;
        u.noShowCount = 0;
        u.emptyWeekStreak = 0;
        u.createdAt = now;
        u.updatedAt = now;
        if (ref != null && !ref.isBlank()) {
            User referrer = users.selectOne(new QueryWrapper<User>().eq("invite_code", ref.strip()));
            if (referrer != null) u.referredBy = referrer.id;
        }
        for (int attempt = 0; ; attempt++) {
            u.inviteCode = inviteCode();
            try {
                users.insert(u);
                return u;
            } catch (DuplicateKeyException e) {
                User raced = users.selectOne(new QueryWrapper<User>().eq("phone_hash", hash));
                if (raced != null) return raced;
                if (attempt >= 3) throw e;
            }
        }
    }

    private String inviteCode() {
        byte[] r = crypto.randomBytes(7);
        StringBuilder sb = new StringBuilder("u_");
        for (byte b : r) sb.append(INVITE_ALPHABET.charAt((b & 0xff) % INVITE_ALPHABET.length()));
        return sb.toString();
    }

    public String inviteUrl(User u) {
        return props.inviteBase() + "/j/home-referral/" + u.inviteCode;
    }

    @Transactional
    public void deactivate(long userId) {
        Instant now = Times.millis(clock.instant());
        users.update(null, new UpdateWrapper<User>().eq("id", userId).in("status", User.ACTIVE, User.ONBOARDING, "paused")
                .set("status", User.DEACTIVATED).set("deactivated_at", now).set("updated_at", now));
        sessions.revokeAll(userId);
    }

    @Transactional
    public void reactivate(long userId) {
        User u = require(userId);
        if (!User.DEACTIVATED.equals(u.status)) return;
        Instant now = Times.millis(clock.instant());
        users.update(null, new UpdateWrapper<User>().eq("id", userId).eq("status", User.DEACTIVATED)
                .set("status", u.onboarded() ? User.ACTIVE : User.ONBOARDING).set("deactivated_at", null).set("updated_at", now));
    }

    /** Closes the account now; PurgeJob (M4) deletes personal data at {@code purge_at}. */
    @Transactional
    public void requestDelete(long userId) {
        Instant now = Times.millis(clock.instant());
        users.update(null, new UpdateWrapper<User>().eq("id", userId)
                .set("status", User.DELETING).set("delete_requested_at", now).set("purge_at", now.plus(PURGE_AFTER)).set("updated_at", now));
        sessions.revokeAll(userId);
    }
}
