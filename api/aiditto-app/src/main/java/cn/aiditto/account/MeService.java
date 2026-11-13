package cn.aiditto.account;

import cn.aiditto.domain.questionnaire.OnboardingRules;
import cn.aiditto.infra.config.AppProperties;
import cn.aiditto.infra.crypto.Pii;
import cn.aiditto.onboarding.AnswerStore;
import org.springframework.stereotype.Service;

import java.util.Map;

/** {@code Me} of web/src/api/types.ts plus the optional fields of 技术方案 F12. Contact details are masked. */
@Service
public class MeService {
    public record MeView(String id, String name, boolean onboarded, boolean isStudent, String email, String phone,
                         String inviteUrl, String status, String photoIssue, boolean isAdmin, boolean demoMode) {}

    private final AccountService accounts;
    private final AnswerStore answers;
    private final AppProperties props;

    public MeService(AccountService accounts, AnswerStore answers, AppProperties props) {
        this.accounts = accounts;
        this.answers = answers;
        this.props = props;
    }

    public MeView me(long userId) {
        User u = accounts.require(userId);
        Map<String, Object> a = answers.load(userId);
        String email = OnboardingRules.emailToVerify(a);
        String phone = accounts.phoneOf(u);
        return new MeView(
                String.valueOf(u.id),
                a.get("name") instanceof String s ? s : "",
                u.onboarded(),
                OnboardingRules.isStudent(a),
                email.isEmpty() ? "" : Pii.maskEmail(email),
                phone == null ? "" : Pii.maskPhone(phone),
                accounts.inviteUrl(u),
                u.status,
                u.photoIssue,
                "admin".equals(u.role),
                props.demoMode());
    }
}
