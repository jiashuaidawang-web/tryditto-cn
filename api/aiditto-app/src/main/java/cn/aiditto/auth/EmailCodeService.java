package cn.aiditto.auth;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.questionnaire.AnswerValidator;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.crypto.Pii;
import cn.aiditto.infra.web.Times;
import cn.aiditto.match.OutboundGuard;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import java.time.Clock;

/** Demo email verification (技术方案 §6.5): nothing is sent; the code is DEMO_LOGIN_CODE. Codes never reach answers (F8). */
@Service
public class EmailCodeService {
    private final CodeGuard guard;
    private final EmailVerificationMapper verifications;
    private final Crypto crypto;
    private final Clock clock;
    private final OutboundGuard outbound;

    public EmailCodeService(CodeGuard guard, EmailVerificationMapper verifications, Crypto crypto, Clock clock,
                            OutboundGuard outbound) {
        this.guard = guard;
        this.verifications = verifications;
        this.crypto = crypto;
        this.clock = clock;
        this.outbound = outbound;
    }

    public String send(String rawEmail, String ip) {
        String email = requireEmail(rawEmail);
        if (!outbound.mayEmail(email)) return Pii.maskEmail(email);
        guard.recordSend(AuthCode.EMAIL, crypto.hash(email), ip, ErrorCode.EMAIL_TOO_FREQUENT);
        return Pii.maskEmail(email);
    }

    /** Not transactional: a wrong code must stay counted even when this call ends in AUTH_CODE_LOCKED. */
    public boolean verify(long userId, String rawEmail, String code, String ip) {
        String email = requireEmail(rawEmail);
        if (code == null || !code.matches("^\\d{6}$")) return false;
        byte[] hash = crypto.hash(email);
        if (!guard.check(AuthCode.EMAIL, hash, code, ip)) return false;
        String kind = AnswerValidator.isSchoolEmail(email) ? "school" : "personal";
        EmailVerification row = verifications.selectOne(new QueryWrapper<EmailVerification>().eq("user_id", userId).eq("kind", kind));
        boolean fresh = row == null;
        if (fresh) {
            row = new EmailVerification();
            row.userId = userId;
            row.kind = kind;
        }
        row.emailEnc = crypto.encrypt(email);
        row.emailHash = hash;
        row.domain = email.substring(email.indexOf('@') + 1);
        row.verifiedAt = Times.millis(clock.instant());
        if (fresh) verifications.insert(row);
        else verifications.updateById(row);
        return true;
    }

    public boolean isVerified(long userId, String rawEmail) {
        String email = Pii.normalizeEmail(rawEmail);
        if (email.isEmpty()) return false;
        return verifications.selectCount(new QueryWrapper<EmailVerification>().eq("user_id", userId)
                .eq("email_hash", crypto.hash(email)).isNotNull("verified_at")) > 0;
    }

    public void deleteAll(long userId) {
        verifications.delete(new QueryWrapper<EmailVerification>().eq("user_id", userId));
    }

    private static String requireEmail(String raw) {
        String email = Pii.normalizeEmail(raw);
        if (!AnswerValidator.isEmail(email)) throw new DomainException(ErrorCode.INVALID_EMAIL);
        return email;
    }
}
