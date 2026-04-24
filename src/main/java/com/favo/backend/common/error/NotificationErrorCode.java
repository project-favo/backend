package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Push (FCM) and in-app notification flows (range 14000–14999).
 */
public enum NotificationErrorCode implements ErrorCode {
    /**
     * No row exists for the device token; cannot deliver until client registers a token.
     */
    FCM_TOKEN_NOT_REGISTERED(
            HttpStatus.BAD_REQUEST, 14001, "No FCM device token is registered for this user or device.",
            ErrorSeverity.WARN, false, true),
    FCM_DELIVERY_FAILED(
            HttpStatus.BAD_GATEWAY, 14002, "FCM or Firebase Cloud Messaging delivery failed for this request.",
            ErrorSeverity.WARN, true, true),
    NOTIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND, 14003, "The in-app notification was not found or is no longer available.",
            ErrorSeverity.WARN, false, true),
    /**
     * Idempotent or duplicate mark-as-read: resource already in read state.
     */
    NOTIFICATION_ALREADY_READ(
            HttpStatus.CONFLICT, 14004, "The notification is already marked as read.",
            ErrorSeverity.INFO, false, true),
    NOTIFICATION_PREFERENCE_KEY_INVALID(
            HttpStatus.BAD_REQUEST, 14005, "The notification preference key is not recognized for this app version.",
            ErrorSeverity.WARN, false, true),
    FCM_DEVICE_ID_INVALID(
            HttpStatus.BAD_REQUEST, 14006, "The device or installation identifier for push registration is not valid.",
            ErrorSeverity.WARN, false, true),
    NOTIFICATION_PREFERENCES_UPDATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, 14007, "Server could not persist notification preferences.",
            ErrorSeverity.ERROR, true, true),
    /**
     * The stored FCM token is no longer valid and must be replaced by the client.
     */
    FCM_TOKEN_EXPIRED(
            HttpStatus.GONE, 14008, "The registered FCM token is no longer valid and must be refreshed.",
            ErrorSeverity.WARN, false, true),
    IN_APP_NOTIFICATION_LIST_QUERY_INVALID(
            HttpStatus.BAD_REQUEST, 14009, "Query parameters for listing notifications are out of range or inconsistent.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    NotificationErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
