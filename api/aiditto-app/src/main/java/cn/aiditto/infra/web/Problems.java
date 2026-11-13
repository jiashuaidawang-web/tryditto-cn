package cn.aiditto.infra.web;

import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.json.Json;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RFC 9457 bodies with our stable {@code code} extension (技术方案 §6.1). */
public final class Problems {
    private Problems() {}

    public static ProblemDetail detail(ErrorCode code, String detail, List<String> fields, Long retryAfter) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.status()), detail == null ? code.title() : detail);
        pd.setTitle(code.title());
        pd.setProperty("code", code.name());
        if (fields != null && !fields.isEmpty()) pd.setProperty("fields", fields);
        if (retryAfter != null) pd.setProperty("retryAfter", retryAfter);
        return pd;
    }

    public static ResponseEntity<Object> entity(ErrorCode code, String detail, List<String> fields, Long retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        return ResponseEntity.status(code.status()).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail(code, detail, fields, retryAfter));
    }

    /** For filters that run before Spring MVC (security entry point). */
    public static void write(HttpServletResponse res, ErrorCode code, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", code.title());
        body.put("status", code.status());
        body.put("detail", detail == null ? code.title() : detail);
        body.put("code", code.name());
        res.setStatus(code.status());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(Json.write(body));
    }
}
