package cn.aiditto.onboarding;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/** Row of {@code onboarding_answer}: exactly one of {@code valueJson} / {@code valueEnc} is set. */
@TableName("onboarding_answer")
public class Answer {
    public Long userId;
    public String questionId;
    public String valueJson;
    public byte[] valueEnc;
    public Instant updatedAt;
}
