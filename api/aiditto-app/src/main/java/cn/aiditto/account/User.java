package cn.aiditto.account;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/** Row of {@code user} (技术方案 §4.3). Status values follow §5.5. */
@TableName("\"user\"")
public class User {
    public static final String ONBOARDING = "onboarding";
    public static final String ACTIVE = "active";
    public static final String DEACTIVATED = "deactivated";
    public static final String DELETING = "deleting";

    @TableId(type = IdType.ASSIGN_ID)
    public Long id;
    public byte[] phoneEnc;
    public byte[] phoneHash;
    public String role;
    public String status;
    public String lang;
    public Boolean isSynthetic;
    /** real / demo / synthetic. Real users are never paired with the other two. */
    public String cohort;
    public String handle;
    public String inviteCode;
    public Long referredBy;
    public Instant onboardingCompletedAt;
    public String furthestStepId;
    public String photoIssue;
    public Instant suspendedUntil;
    public Integer noShowCount;
    public Integer lateCancelCount;
    public Integer noResponseStreak;
    public Integer disputeCount;
    public Integer emptyWeekStreak;
    public Instant deactivatedAt;
    public Instant deleteRequestedAt;
    public Instant purgeAt;
    public Instant lastLoginAt;
    public Instant createdAt;
    public Instant updatedAt;

    public boolean onboarded() {
        return onboardingCompletedAt != null;
    }
}
