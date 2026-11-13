package cn.aiditto.domain.common;

import java.util.List;

/** A business rule said no. The web layer maps it to a ProblemDetail with {@code code}, {@code fields}, {@code retryAfter}. */
public class DomainException extends RuntimeException {
    private final ErrorCode code;
    private final List<String> fields;
    private final Long retryAfter;

    public DomainException(ErrorCode code, String detail, List<String> fields, Long retryAfter) {
        super(detail == null ? code.title() : detail);
        this.code = code;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
        this.retryAfter = retryAfter;
    }

    public DomainException(ErrorCode code, String detail) {
        this(code, detail, null, null);
    }

    public DomainException(ErrorCode code) {
        this(code, null, null, null);
    }

    public static DomainException invalid(String field, String detail) {
        return new DomainException(ErrorCode.VALIDATION, detail, List.of(field), null);
    }

    public static DomainException tooFrequent(ErrorCode code, long retryAfterSeconds) {
        return new DomainException(code, null, null, Math.max(1, retryAfterSeconds));
    }

    public ErrorCode code() {
        return code;
    }

    public List<String> fields() {
        return fields;
    }

    public Long retryAfter() {
        return retryAfter;
    }
}
