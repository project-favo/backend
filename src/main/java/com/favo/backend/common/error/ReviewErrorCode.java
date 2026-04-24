package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Reviews, flags, and vote interactions (range 12000–12999).
 */
public enum ReviewErrorCode implements ErrorCode {
    REVIEW_NOT_FOUND(
            HttpStatus.NOT_FOUND, 12001, "The requested review was not found.",
            ErrorSeverity.WARN, false, true),
    /**
     * Business rule: at most one published review per user per product.
     */
    REVIEW_DUPLICATE_FOR_PRODUCT(
            HttpStatus.CONFLICT, 12002, "A review for this product already exists from this user.",
            ErrorSeverity.WARN, false, true),
    REVIEW_CONTENT_EMPTY(
            HttpStatus.BAD_REQUEST, 12003, "Review text must not be empty.",
            ErrorSeverity.WARN, false, true),
    REVIEW_CONTENT_TOO_SHORT(
            HttpStatus.BAD_REQUEST, 12004, "Review text is shorter than the minimum allowed length.",
            ErrorSeverity.WARN, false, true),
    /**
     * HuggingFace toxic-bert or equivalent classifier marked content as unsafe.
     */
    REVIEW_TOXIC_HUGGINGFACE(
            HttpStatus.BAD_REQUEST, 12005, "Review was rejected by automated toxicity checks.",
            ErrorSeverity.WARN, false, true),
    REVIEW_DELETE_NOT_OWNER(
            HttpStatus.FORBIDDEN, 12006, "Only the review author may delete this review.",
            ErrorSeverity.WARN, false, true),
    /**
     * User already submitted a report for this review (anti-spam / deduplication).
     */
    REVIEW_REPORT_ALREADY_SUBMITTED(
            HttpStatus.CONFLICT, 12007, "A report for this review from this user already exists.",
            ErrorSeverity.WARN, false, true),
    REVIEW_TEXT_TOO_LONG(
            HttpStatus.BAD_REQUEST, 12008, "Review text exceeds the maximum length.",
            ErrorSeverity.WARN, false, true),
    /**
     * Review is locked in the current state (e.g. locked after moderation) and may not be edited.
     */
    REVIEW_NOT_EDITABLE(
            HttpStatus.CONFLICT, 12009, "This review may not be edited in its current state.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    ReviewErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
