package com.favo.backend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.MDC;
import jakarta.annotation.PostConstruct;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps every throw site that uses {@link FavoException} and common framework exceptions to
 * {@link ApiErrorResponse}. Initializes {@link ErrorCodeRegistry} on startup.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    private static final String TRACE_MDC = "traceId";
    private static final String TRACE_B3 = "X-B3-TraceId";
    private static final String TRACE_ATTR = "traceId";

    @PostConstruct
    public void init() {
        ErrorCodeRegistry.initialize();
    }

    @ExceptionHandler(FavoException.class)
    public ResponseEntity<ApiErrorResponse> handleFavo(FavoException ex) {
        logFavo(ex);
        return responseFrom(ex.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        if (log.isDebugEnabled()) {
            String fields = ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            log.debug("MethodArgumentNotValidException: {}", fields);
        }
        return responseFrom(UserErrorCode.USER_FIELD_VALIDATION_FAILED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex) {
        if (log.isDebugEnabled()) {
            log.debug("ConstraintViolationException: {}", ex.getMessage());
        }
        return responseFrom(UserErrorCode.USER_FIELD_VALIDATION_FAILED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        if (log.isDebugEnabled()) {
            log.debug("HttpMessageNotReadableException: {}", ex.getMessage());
        }
        return responseFrom(SystemErrorCode.API_REQUEST_BODY_MALFORMED);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex) {
        if (log.isDebugEnabled()) {
            log.debug("MissingServletRequestParameterException: {}", ex.getMessage());
        }
        return responseFrom(UserErrorCode.USER_FIELD_VALIDATION_FAILED);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        if (log.isDebugEnabled()) {
            log.debug("MethodArgumentTypeMismatchException: {}", ex.getMessage());
        }
        return responseFrom(UserErrorCode.USER_FIELD_VALIDATION_FAILED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        log.warn("DataIntegrityViolationException: {}", ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage());
        return responseFrom(UserErrorCode.USER_FIELD_VALIDATION_FAILED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        if (log.isDebugEnabled()) {
            log.debug("MaxUploadSizeExceededException: {}", ex.getMessage());
        }
        return responseFrom(MediaErrorCode.MEDIA_FILE_SIZE_EXCEEDED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(
            HttpMediaTypeNotSupportedException ex) {
        if (log.isDebugEnabled()) {
            log.debug("HttpMediaTypeNotSupportedException: {}", ex.getMessage());
        }
        return responseFrom(MediaErrorCode.MEDIA_TYPE_UNSUPPORTED);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(
            HttpRequestMethodNotSupportedException ex) {
        if (log.isDebugEnabled()) {
            log.debug("HttpRequestMethodNotSupportedException: {}", ex.getMessage());
        }
        return responseFrom(SystemErrorCode.HTTP_METHOD_OR_PATH_NOT_ALLOWED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} | request={} {}", ex.getMessage(), request.getMethod(), request.getRequestURI());
        return responseFrom(AuthErrorCode.AUTH_PERMISSION_DENIED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {} | request={} {}", ex.getMessage(), request.getMethod(), request.getRequestURI());
        return responseFrom(AuthErrorCode.AUTH_TOKEN_FIREBASE_INVALID);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        return responseFrom(SystemErrorCode.UNEXPECTED_INTERNAL_ERROR);
    }

    private void logFavo(FavoException ex) {
        ErrorCode code = ex.getErrorCode();
        String ctx = ex.getContext().isEmpty() ? "{}" : ex.getContext().toString();
        switch (code.getSeverity()) {
            case FATAL, ERROR -> log.error("FavoException: {} | context={}", code.getErrorCode(), ctx, ex);
            case WARN -> log.warn("FavoException: {} | context={} | message={}", code.getErrorCode(), ctx, ex.getMessage());
            case INFO -> {
                if (log.isDebugEnabled()) {
                    log.debug("FavoException: {} | context={}", code.getErrorCode(), ctx);
                }
            }
        }
    }

    private String resolveTraceId() {
        String t = MDC.get(TRACE_MDC);
        if (t != null && !t.isBlank()) {
            return t;
        }
        t = MDC.get(TRACE_B3);
        if (t != null && !t.isBlank()) {
            return t;
        }
        var ra = RequestContextHolder.getRequestAttributes();
        if (ra instanceof ServletRequestAttributes s) {
            Object a = s.getRequest().getAttribute(TRACE_ATTR);
            if (a != null) {
                return a.toString();
            }
        }
        return UUID.randomUUID().toString();
    }

    private ResponseEntity<ApiErrorResponse> responseFrom(ErrorCode code) {
        int status = code.getHttpStatus().value();
        String bodyMessage = code.getClientMessage();
        String ts = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        var body = new ApiErrorResponse(
                status,
                code.getErrorCode(),
                code.getInternalCode(),
                bodyMessage,
                code.isRetryable(),
                ts,
                resolveTraceId());
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }
}
