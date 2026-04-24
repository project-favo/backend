package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Resend (and related) transactional email delivery (range 18000–18999).
 */
public enum EmailErrorCode implements ErrorCode {
    /**
     * Non-2xx from Resend HTTP API or transport failure after final send attempt.
     */
    RESEND_DELIVERY_FAILED(
            HttpStatus.BAD_GATEWAY, 18001, "The email could not be delivered via the Resend API.",
            ErrorSeverity.WARN, true, true),
    EMAIL_ADDRESS_FORMAT_INVALID(
            HttpStatus.BAD_REQUEST, 18002, "The email address is not in a valid format.",
            ErrorSeverity.WARN, false, true),
    /** No row for the secret or opaque id the client presented. */
    VERIFICATION_TOKEN_NOT_FOUND(
            HttpStatus.NOT_FOUND, 18003, "The email verification token was not found.",
            ErrorSeverity.WARN, false, true),
    VERIFICATION_TOKEN_EXPIRED(
            HttpStatus.GONE, 18004, "The email verification token has expired.",
            ErrorSeverity.WARN, false, true),
    VERIFICATION_TOKEN_ALREADY_USED(
            HttpStatus.CONFLICT, 18005, "The email verification token has already been used.",
            ErrorSeverity.WARN, false, true),
    /**
     * Too many sends to the same mailbox in a sliding window (anti-abuse).
     */
    EMAIL_RATE_LIMIT_PER_ADDRESS(
            HttpStatus.TOO_MANY_REQUESTS, 18006, "Too many emails have been sent to this address recently.",
            ErrorSeverity.WARN, true, true),
    /**
     * Template engine could not resolve a required placeholder for this email type.
     */
    EMAIL_TEMPLATE_VARIABLE_MISSING(
            HttpStatus.INTERNAL_SERVER_ERROR, 18007, "An email template variable was missing on the server.",
            ErrorSeverity.ERROR, true, false),
    /**
     * Provider reported hard bounce or suppressed recipient.
     */
    EMAIL_RECIPIENT_SUPPRESSED(
            HttpStatus.BAD_REQUEST, 18008, "This email address could not receive mail (bounced or suppressed).",
            ErrorSeverity.WARN, false, true),
    /**
     * Disposable or blocked domain policy (if enabled); no message content is implied to the client.
     */
    EMAIL_DOMAIN_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST, 18009, "This email domain is not allowed for registration or delivery.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    EmailErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
