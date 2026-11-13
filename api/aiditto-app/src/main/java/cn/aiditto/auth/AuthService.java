package cn.aiditto.auth;

import cn.aiditto.account.AccountService;
import cn.aiditto.account.MeService;
import cn.aiditto.account.User;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.crypto.Pii;
import cn.aiditto.infra.web.Times;
import org.springframework.stereotype.Service;

/** Phone login with the demo code (技术方案 §6.3). */
@Service
public class AuthService {
    private final CodeGuard guard;
    private final AccountService accounts;
    private final SessionService sessions;
    private final MeService me;

    public AuthService(CodeGuard guard, AccountService accounts, SessionService sessions, MeService me) {
        this.guard = guard;
        this.accounts = accounts;
        this.sessions = sessions;
        this.me = me;
    }

    public long sendSms(String rawPhone, String ip) {
        String phone = requirePhone(rawPhone);
        guard.recordSend(AuthCode.SMS, accounts.phoneHash(phone), ip, ErrorCode.SMS_TOO_FREQUENT);
        return CodeGuard.COOLDOWN.toSeconds();
    }

    public record LoginResult(String token, String expiresAt, MeService.MeView me) {}

    public LoginResult login(String rawPhone, String code, String ref, String userAgent, String ip) {
        String phone = requirePhone(rawPhone);
        if (code == null || !code.matches("^\\d{6}$")) throw new DomainException(ErrorCode.INVALID_CODE_FORMAT);
        if (!guard.check(AuthCode.SMS, accounts.phoneHash(phone), code, ip)) throw new DomainException(ErrorCode.AUTH_CODE_WRONG, "验证码不对，再试一次");
        User u = accounts.loginUser(phone, ref);
        SessionService.Issued s = sessions.issue(u.id, userAgent, ip);
        return new LoginResult(s.token(), Times.iso(s.expiresAt()), me.me(u.id));
    }

    public void logout(AuthUser user) {
        sessions.revoke(user.sessionId());
    }

    private static String requirePhone(String raw) {
        String phone = Pii.normalizePhone(raw);
        if (!Pii.isCnMobile(phone)) throw new DomainException(ErrorCode.INVALID_PHONE);
        return phone;
    }
}
