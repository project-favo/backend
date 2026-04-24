package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Direct messaging, conversations, and WebSocket/STOMP delivery (range 15000–15999).
 */
public enum MessageErrorCode implements ErrorCode {
    CONVERSATION_NOT_FOUND(
            HttpStatus.NOT_FOUND, 15001, "The conversation was not found or the user is not a participant.",
            ErrorSeverity.WARN, false, true),
    MESSAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND, 15002, "The message was not found in this conversation.",
            ErrorSeverity.WARN, false, true),
    /**
     * Sender is not a member of the conversation (cross-tenant or left conversation).
     */
    MESSAGE_SENDER_NOT_PARTICIPANT(
            HttpStatus.FORBIDDEN, 15003, "The sender is not a participant in this conversation.",
            ErrorSeverity.WARN, false, true),
    /**
     * STOMP or WebSocket session is closed or not yet ready for the requested operation.
     */
    WS_SESSION_NOT_ACTIVE(
            HttpStatus.SERVICE_UNAVAILABLE, 15004, "No active WebSocket session is available for this user.",
            ErrorSeverity.WARN, true, true),
    MESSAGE_CONTENT_TOO_LONG(
            HttpStatus.PAYLOAD_TOO_LARGE, 15005, "The message body exceeds the maximum size.",
            ErrorSeverity.WARN, false, true),
    CONVERSATION_ACCESS_DENIED(
            HttpStatus.FORBIDDEN, 15006, "The user may not access this conversation.",
            ErrorSeverity.WARN, false, true),
    MESSAGE_DELIVERY_PERSISTENCE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, 15007, "The message could not be saved after delivery.",
            ErrorSeverity.ERROR, true, true),
    /** Non-existent or expired draft in edit/retry flows. */
    MESSAGE_DRAFT_NOT_FOUND(
            HttpStatus.NOT_FOUND, 15008, "The requested message draft was not found.",
            ErrorSeverity.WARN, false, true),
    /**
     * One participant has blocked the other; new messages are not allowed.
     */
    MESSAGE_RECIPIENT_BLOCKED(
            HttpStatus.FORBIDDEN, 15009, "This recipient is blocked; messaging is not allowed.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    MessageErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
