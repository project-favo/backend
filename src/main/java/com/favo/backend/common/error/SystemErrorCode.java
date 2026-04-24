package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Infrastructure, configuration, and uncategorized failures (range 99000–99999).
 */
public enum SystemErrorCode implements ErrorCode {
    DATABASE_CONNECTION_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE, 99001, "The application could not connect to the database.",
            ErrorSeverity.FATAL, true, false),
    SYSTEM_DB_TRANSACTION_ROLLBACK(
            HttpStatus.INTERNAL_SERVER_ERROR, 99002, "The database transaction was rolled back.",
            ErrorSeverity.ERROR, true, false),
    UNEXPECTED_INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, 99003, "An unexpected internal error occurred.",
            ErrorSeverity.ERROR, true, false),
    /**
     * Generic upstream timeout when no more specific ErrorCode applies.
     */
    THIRD_PARTY_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT, 99004, "A downstream service did not respond in time.",
            ErrorSeverity.WARN, true, true),
    FEATURE_FLAG_DISABLED(
            HttpStatus.NOT_FOUND, 99005, "This feature is disabled in the current deployment.",
            ErrorSeverity.INFO, false, true),
    SYSTEM_CONFIGURATION_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR, 99006, "The server configuration is invalid or incomplete.",
            ErrorSeverity.FATAL, false, false),
    /**
     * Developer wiring error; not a client concern.
     */
    BEAN_INITIALIZATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, 99007, "A required server component failed to start.",
            ErrorSeverity.FATAL, false, false),
    /**
     * Global API rate limit for the client or IP was exceeded.
     */
    RATE_LIMIT_GLOBAL_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, 99008, "Too many requests; please slow down and retry later.",
            ErrorSeverity.WARN, true, true),
    /** Planned or emergency read-only mode. */
    MAINTENANCE_MODE(
            HttpStatus.SERVICE_UNAVAILABLE, 99009, "The service is temporarily under maintenance.",
            ErrorSeverity.INFO, true, true),
    /**
     * HTTP verb or path not supported on this resource (catch-all when not better specified).
     */
    HTTP_METHOD_OR_PATH_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED, 99010, "The HTTP method or path is not supported for this resource.",
            ErrorSeverity.WARN, false, true),
    API_REQUEST_BODY_MALFORMED(
            HttpStatus.BAD_REQUEST, 99011, "The request body could not be read as valid JSON or expected format.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    SystemErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
            boolean retryable, boolean exposedToClient) {
        this.httpStatus = httpStatus;
        this.internalCode = internalCode;
        this.message = message;
        this.severity = severity;
        this.retryable = retryable;
        this.exposedToClient = exposedToClient;
    }

    @Override
    public String getErrorCode() {
        return name();
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public int getInternalCode() {
        return internalCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ErrorSeverity getSeverity() {
        return severity;
    }

    @Override
    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public boolean isExposedToClient() {
        return exposedToClient;
    }
}
