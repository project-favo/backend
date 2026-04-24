package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * HuggingFace toxicity, admin moderation, and appeals (range 19000–19999).
 */
public enum ModerationErrorCode implements ErrorCode {
    /**
     * toxic-bert score above the configured reject threshold.
     */
    TOXICITY_SCORE_EXCEEDS_THRESHOLD(
            HttpStatus.BAD_REQUEST, 19001, "Content was blocked because toxicity score exceeded the policy limit.",
            ErrorSeverity.WARN, false, true),
    /**
     * HuggingFace endpoint down, 5xx, or empty body.
     */
    MODERATION_SERVICE_UNREACHABLE(
            HttpStatus.BAD_GATEWAY, 19002, "The automated moderation service is temporarily unavailable.",
            ErrorSeverity.WARN, true, true),
    /**
     * Final state already set; duplicate moderation action.
     */
    CONTENT_ALREADY_MODERATED(
            HttpStatus.CONFLICT, 19003, "This content has already been moderated.",
            ErrorSeverity.WARN, false, true),
    /**
     * Admin cannot take action on content they authored (policy / audit).
     */
    MODERATION_ADMIN_ACTION_ON_OWN_CONTENT(
            HttpStatus.FORBIDDEN, 19004, "Moderators may not act on their own published content.",
            ErrorSeverity.WARN, false, true),
    APPEAL_ALREADY_SUBMITTED(
            HttpStatus.CONFLICT, 19005, "An appeal for this decision has already been submitted.",
            ErrorSeverity.WARN, false, true),
    /**
     * Classifier returned malformed output or scores that could not be parsed.
     */
    TOXICITY_CLASSIFIER_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY, 19006, "The moderation classifier returned an invalid response.",
            ErrorSeverity.ERROR, true, true),
    /**
     * Backpressure: human review queue full.
     */
    MODERATION_REVIEW_QUEUE_OVERLOADED(
            HttpStatus.SERVICE_UNAVAILABLE, 19007, "The manual moderation queue is full; try again later.",
            ErrorSeverity.WARN, true, true),
    /** Message type not supported by the pipeline (e.g. binary file). */
    MODERATION_CONTENT_TYPE_UNSUPPORTED(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, 19008, "This content type cannot be processed by moderation.",
            ErrorSeverity.WARN, false, true),
    /**
     * Composite risk score from multiple signals rejected the content.
     */
    MODERATION_COMPOSITE_SCORE_REJECTED(
            HttpStatus.BAD_REQUEST, 19009, "Content was rejected by automated moderation policies.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    ModerationErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
