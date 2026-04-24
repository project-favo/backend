package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * User profile, follow, and account preferences (range 11000–11999).
 */
public enum UserErrorCode implements ErrorCode {
    /**
     * Proposed display name is already used by another account (compared in a case-insensitive way for duplicates).
     */
    USERNAME_ALREADY_TAKEN(
            HttpStatus.CONFLICT, 11001, "That username is already taken.",
            ErrorSeverity.WARN, false, true),
    USERNAME_FORMAT_INVALID(
            HttpStatus.BAD_REQUEST, 11002, "That username does not match the required pattern.",
            ErrorSeverity.WARN, false, true),
    USER_PROFILE_NOT_FOUND(
            HttpStatus.NOT_FOUND, 11003, "The requested user profile was not found.",
            ErrorSeverity.WARN, false, true),
    /** Self-follow in follow/unfollow is not allowed. */
    USER_FOLLOW_SELF_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST, 11004, "A user may not follow themselves.",
            ErrorSeverity.WARN, false, true),
    AVATAR_MEDIA_TYPE_UNSUPPORTED(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, 11005, "The avatar image type is not allowed.",
            ErrorSeverity.WARN, false, true),
    AVATAR_FILE_SIZE_EXCEEDED(
            HttpStatus.PAYLOAD_TOO_LARGE, 11006, "The avatar file exceeds the maximum allowed size.",
            ErrorSeverity.WARN, true, true),
    USER_FOLLOW_DUPLICATE(
            HttpStatus.CONFLICT, 11007, "The follow relationship already exists.",
            ErrorSeverity.WARN, false, true),
    /**
     * Target user is blocked, deleted, or otherwise unavailable for the operation.
     */
    USER_NOT_AVAILABLE(
            HttpStatus.NOT_FOUND, 11008, "The target user could not be found for this action.",
            ErrorSeverity.WARN, false, true),
    USER_UNFOLLOW_NOT_FOLLOWING(
            HttpStatus.BAD_REQUEST, 11009, "Cannot unfollow because this follow relationship does not exist.",
            ErrorSeverity.WARN, false, true),
    /**
     * Bean validation (e.g. @Valid) or request body binding failed for a user profile request.
     */
    USER_FIELD_VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST, 11010, "Request validation failed for one or more user fields.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    UserErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
