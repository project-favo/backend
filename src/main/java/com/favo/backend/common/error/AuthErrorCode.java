package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Firebase- and session-related authentication and authorization errors (range 10000–10999).
 */
public enum AuthErrorCode implements ErrorCode {
    /**
     * Firebase ID token could not be verified (including signature, issuer, and audience).
     */
    AUTH_TOKEN_FIREBASE_INVALID(
            HttpStatus.UNAUTHORIZED, 10001, "Firebase ID token is invalid or could not be verified.",
            ErrorSeverity.ERROR, true, true),
    /**
     * Token {@code iat} / expiry indicates the credential is no longer within its validity window.
     */
    AUTH_TOKEN_FIREBASE_EXPIRED(
            HttpStatus.UNAUTHORIZED, 10002, "Firebase ID token has expired.",
            ErrorSeverity.WARN, true, true),
    /**
     * Token was valid but is no longer accepted (e.g. user disabled, refresh required).
     */
    AUTH_TOKEN_FIREBASE_REVOKED(
            HttpStatus.UNAUTHORIZED, 10003, "Firebase ID token has been revoked or is no longer valid.",
            ErrorSeverity.WARN, true, true),
    AUTH_HEADER_MISSING(
            HttpStatus.UNAUTHORIZED, 10004, "Authorization header is required but was not sent.",
            ErrorSeverity.WARN, false, true),
    AUTH_HEADER_MALFORMED(
            HttpStatus.BAD_REQUEST, 10005, "Authorization header is not a valid Bearer token.",
            ErrorSeverity.WARN, false, true),
    /**
     * Token verified, but the corresponding local account row does not exist.
     */
    AUTH_USER_NOT_FOUND(
            HttpStatus.NOT_FOUND, 10006, "No local user account exists for the verified credentials.",
            ErrorSeverity.WARN, false, true),
    AUTH_ACCOUNT_BANNED(
            HttpStatus.FORBIDDEN, 10007, "This account is banned and may not use the service.",
            ErrorSeverity.ERROR, false, true),
    AUTH_ACCOUNT_SUSPENDED(
            HttpStatus.FORBIDDEN, 10008, "This account is suspended temporarily.",
            ErrorSeverity.WARN, true, true),
    AUTH_FORBIDDEN_ROLE(
            HttpStatus.FORBIDDEN, 10009, "The authenticated user does not have the required role for this action.",
            ErrorSeverity.WARN, false, true),
    AUTH_PERMISSION_DENIED(
            HttpStatus.FORBIDDEN, 10010, "The authenticated user lacks permission to perform this operation.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    AuthErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
