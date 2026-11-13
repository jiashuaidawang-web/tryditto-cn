package cn.aiditto.infra.web;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/** Every error leaves as a ProblemDetail carrying {@code code}; Spring's own MVC errors get a code by status. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> domain(DomainException e) {
        return Problems.entity(e.code(), e.getMessage(), e.fields(), e.retryAfter());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        log.error("unhandled error, requestId={}", requestId, e);
        return Problems.entity(ErrorCode.INTERNAL, "请求 id：" + requestId, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).distinct().toList();
        return Problems.entity(ErrorCode.VALIDATION, null, fields, null);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        return Problems.entity(ErrorCode.VALIDATION, null, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> res = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (res != null && res.getBody() instanceof ProblemDetail pd && pd.getProperties() == null) {
            pd.setProperty("code", codeFor(statusCode.value()).name());
        }
        return res;
    }

    private static ErrorCode codeFor(int status) {
        return switch (status) {
            case 400, 405, 406, 415 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 413 -> ErrorCode.PHOTO_TOO_LARGE;
            case 422 -> ErrorCode.VALIDATION;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status >= 500 ? ErrorCode.INTERNAL : ErrorCode.BAD_REQUEST;
        };
    }
}
