package cn.aiditto.onboarding;

import cn.aiditto.account.AccountService;
import cn.aiditto.account.User;
import cn.aiditto.account.UserMapper;
import cn.aiditto.auth.EmailCodeService;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.questionnaire.AnswerValidator;
import cn.aiditto.domain.questionnaire.OnboardingRules;
import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.questionnaire.QuestionSpec;
import cn.aiditto.domain.questionnaire.QuestionType;
import cn.aiditto.infra.clock.ClockConfig;
import cn.aiditto.infra.web.Times;
import cn.aiditto.photo.PhotoService;
import cn.aiditto.place.PlaceService;
import cn.aiditto.profile.ProfileProjection;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Onboarding write-through (技术方案 §6.4) and profile edits (§6.5): every answer is validated like validate.ts and
 * stored at once. After completion each edit re-projects the {@code profile} row.
 */
@Service
public class OnboardingService {
    public record Progress(Map<String, Object> answers, String furthestStepId, boolean completed) {}

    private final AnswerStore store;
    private final QuestionCatalog catalog;
    private final AccountService accounts;
    private final UserMapper users;
    private final EmailCodeService emails;
    private final PhotoService photos;
    private final PlaceService places;
    private final ProfileProjection projection;
    private final Clock clock;

    public OnboardingService(AnswerStore store, QuestionCatalog catalog, AccountService accounts, UserMapper users,
                             EmailCodeService emails, PhotoService photos, PlaceService places,
                             ProfileProjection projection, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.accounts = accounts;
        this.users = users;
        this.emails = emails;
        this.photos = photos;
        this.places = places;
        this.projection = projection;
        this.clock = clock;
    }

    /** {@code value == null} removes the answer (stale hobby branches, cleared optional fields). */
    @Transactional
    public void writeAnswer(long userId, String questionId, Object value) {
        User u = accounts.require(userId);
        Map<String, Object> answers = store.load(userId);

        if (catalog.isFlag(questionId)) {
            if (value == null) store.delete(userId, List.of(questionId));
            else if (value instanceof Boolean) store.put(userId, questionId, value);
            else throw DomainException.invalid(questionId, "应为 true 或 false");
            afterWrite(u, userId);
            return;
        }

        QuestionSpec q = catalog.find(questionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "没有这道题"));
        if (q.type() == QuestionType.CODE) {
            if (value == null) return;
            String email = OnboardingRules.emailToVerify(answers);
            if (email.isEmpty() || !emails.isVerified(userId, email)) throw new DomainException(ErrorCode.EMAIL_NOT_VERIFIED, "邮箱还没有验证");
            return;
        }

        if (value == null) {
            if (u.onboarded()) {
                Map<String, Object> after = new LinkedHashMap<>(answers);
                after.remove(questionId);
                if (requiredFields(userId, after).contains(questionId)) throw DomainException.invalid(questionId, "这一项不能为空");
            }
            store.delete(userId, List.of(questionId));
            if ("hobbies".equals(questionId)) dropStaleBranches(userId, List.of(), answers);
            afterWrite(u, userId);
            return;
        }

        Object normalized = AnswerValidator.validate(q, value, context());
        if ("birthday".equals(questionId) && u.onboarded() && !Objects.equals(normalized, answers.get("birthday"))) {
            throw new DomainException(ErrorCode.FIELD_LOCKED, "生日在入驻完成后不能修改", List.of("birthday"), null);
        }
        store.put(userId, questionId, normalized);
        if ("hobbies".equals(questionId)) {
            @SuppressWarnings("unchecked")
            List<String> picked = (List<String>) normalized;
            dropStaleBranches(userId, picked, answers);
        }
        afterWrite(u, userId);
    }

    @Transactional
    public void setFurthestStep(long userId, String stepId) {
        if (stepId == null) return;
        if (catalog.find(stepId).isEmpty()) throw DomainException.invalid("furthestStepId", "没有这一步");
        users.update(null, new UpdateWrapper<User>().eq("id", userId).set("furthest_step_id", stepId)
                .set("updated_at", Times.millis(clock.instant())));
    }

    public Progress progress(long userId) {
        User u = accounts.require(userId);
        Map<String, Object> answers = store.load(userId);
        long count = photos.countActive(userId, "profile");
        if (count > 0) answers.put("photos", count);
        return new Progress(answers, u.furthestStepId, u.onboarded());
    }

    @Transactional
    public void complete(long userId) {
        User u = accounts.require(userId);
        Map<String, Object> answers = store.load(userId);
        List<String> missing = requiredFields(userId, answers);
        if (!missing.isEmpty()) {
            throw new DomainException(ErrorCode.ONBOARDING_INCOMPLETE, "还有题没有答完", missing, null);
        }
        Instant now = Times.millis(clock.instant());
        UpdateWrapper<User> up = new UpdateWrapper<User>().eq("id", userId).set("updated_at", now);
        if (!u.onboarded()) up.set("onboarding_completed_at", now);
        if (User.ONBOARDING.equals(u.status)) up.set("status", User.ACTIVE);
        users.update(null, up);
        projection.write(userId, answers, photos.countActive(userId, "profile"));
    }

    private List<String> requiredFields(long userId, Map<String, Object> answers) {
        String email = OnboardingRules.emailToVerify(answers);
        boolean verified = !email.isEmpty() && emails.isVerified(userId, email);
        return OnboardingRules.missing(answers, (int) photos.countActive(userId, "profile"), verified);
    }

    private void dropStaleBranches(long userId, List<String> picked, Map<String, Object> before) {
        List<String> stale = catalog.staleBranchKeys(picked, new ArrayList<>(before.keySet()));
        store.delete(userId, stale);
    }

    private void afterWrite(User u, long userId) {
        if (u.onboarded()) projection.write(userId, store.load(userId), photos.countActive(userId, "profile"));
    }

    private AnswerValidator.Context context() {
        return new AnswerValidator.Context(ClockConfig.shanghaiDate(clock.instant()), places::isArea);
    }
}
