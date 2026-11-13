package cn.aiditto.domain.common;

/** Stable error codes (技术方案 §6.7). The frontend branches on {@link #name()}. */
public enum ErrorCode {
    BAD_REQUEST(400, "请求格式不对"),
    INVALID_PHONE(400, "手机号格式不对"),
    INVALID_CODE_FORMAT(400, "验证码格式不对"),
    INVALID_EMAIL(400, "邮箱格式不对"),
    UNAUTHENTICATED(401, "请先登录"),
    AUTH_CODE_WRONG(401, "验证码错误"),
    FORBIDDEN(403, "没有权限"),
    ONBOARDING_REQUIRED(403, "请先完成入驻问卷"),
    PHOTO_ISSUE(403, "照片需要更换"),
    ACCOUNT_INACTIVE(403, "账号已停用或正在注销"),
    NOT_FOUND(404, "找不到"),
    MATCH_NOT_FOUND(404, "找不到这场匹配"),
    REASON_INDEX(404, "没有这条理由"),
    FIELD_LOCKED(409, "这一项不能再修改"),
    PHOTO_LIMIT(409, "最多 6 张照片"),
    PHOTO_MIN(409, "至少保留 2 张照片"),
    BLOCK_LIMIT(409, "屏蔽名单最多 20 条"),
    EMAIL_NOT_VERIFIED(409, "邮箱还没验证"),
    MATCH_STATE(409, "现在不能做这个操作"),
    CHECKIN_WINDOW(409, "现在还不能签到"),
    FEEDBACK_EXISTS(409, "反馈已经提交过了"),
    RUN_IN_PROGRESS(409, "这一池还有没跑完的批次"),
    UPLOAD_STATE(409, "上传地址已失效"),
    PHOTO_TOO_LARGE(413, "照片太大"),
    PHOTO_TYPE(415, "不支持的图片格式"),
    VALIDATION(422, "填写的内容不对"),
    ONBOARDING_INCOMPLETE(422, "入驻问卷还没填完"),
    UNDER_AGE(422, "年满 18 周岁才能加入"),
    BLOCK_NEEDS_CONTACT(422, "至少填一个邮箱或手机号"),
    SLOTS_EMPTY(422, "至少选一个时段"),
    SLOT_UNKNOWN(422, "没有这个时段"),
    REASON_UNKNOWN(422, "不认识这个拒绝理由"),
    CATEGORY_UNKNOWN(422, "不认识这个举报类型"),
    SMS_TOO_FREQUENT(429, "发送太频繁"),
    EMAIL_TOO_FREQUENT(429, "发送太频繁"),
    AUTH_CODE_LOCKED(429, "错误次数太多，请稍后再试"),
    RATE_LIMITED(429, "请求太频繁"),
    INTERNAL(500, "服务器开小差了"),
    SMS_UNAVAILABLE(503, "短信服务未开通");

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }
}
