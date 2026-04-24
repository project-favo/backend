package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Central contract for every Favo API error. Implementations are domain-scoped
 * enums; each constant maps to one stable {@link #getInternalCode()} in its
 * reserved numeric range and one {@link #getErrorCode()} string used in JSON
 * and logs.
 */
public sealed interface ErrorCode permits AuthErrorCode, UserErrorCode, ReviewErrorCode, ProductErrorCode,
        NotificationErrorCode, MessageErrorCode, AiChatErrorCode, MediaErrorCode, EmailErrorCode, ModerationErrorCode,
        FeedErrorCode, SystemErrorCode {

    /**
     * @return UPPER_SNAKE_CASE identifier, unique across the platform (e.g. {@code AUTH_TOKEN_FIREBASE_INVALID}).
     */
    String getErrorCode();

    /**
     * @return HTTP status sent to the client for this error. Always use the enum, never a raw int.
     */
    HttpStatus getHttpStatus();

    /**
     * @return Five-digit, domain-scoped stable identifier for support and log correlation. Unique across all domains.
     */
    int getInternalCode();

    /**
     * @return English, developer-oriented description. May be redacted in API responses when {@link #isExposedToClient()} is false.
     */
    String getMessage();

    /**
     * @return Severity for logging and alerting policy.
     */
    ErrorSeverity getSeverity();

    /**
     * @return True when clients should back off and retry the same idempotent request.
     */
    boolean isRetryable();

    /**
     * @return When false, {@link #getMessage()} must not appear in the public JSON body; use a generic client message instead.
     */
    boolean isExposedToClient();

    /**
     * @return The developer {@link #getMessage()} when {@link #isExposedToClient()} is true; otherwise a non-localized
     *         generic English phrase for API consumers.
     */
    default String getClientMessage() {
        return isExposedToClient() ? getMessage() : "An error occurred";
    }
}
