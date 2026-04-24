package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Friend feed, activity feed, and ranking (range 20000–20999).
 */
public enum FeedErrorCode implements ErrorCode {
    /**
     * No follow graph or allowed content to build a personalized stream.
     */
    FEED_NOT_AVAILABLE_NO_FOLLOWS(
            HttpStatus.NOT_FOUND, 20001, "No friend feed is available because the user follows no one yet.",
            ErrorSeverity.INFO, false, true),
    /** Requested page index or offset is past the last page. */
    FEED_PAGE_OUT_OF_RANGE(
            HttpStatus.BAD_REQUEST, 20002, "The requested feed page is beyond the available range.",
            ErrorSeverity.WARN, false, true),
    /**
     * Unknown activity type in an event stream (version skew or corrupt row).
     */
    ACTIVITY_EVENT_TYPE_UNKNOWN(
            HttpStatus.INTERNAL_SERVER_ERROR, 20003, "An activity event referenced an unknown type.",
            ErrorSeverity.ERROR, false, false),
    /** Opaque cursor could not be decoded or is expired. */
    FEED_CURSOR_INVALID(
            HttpStatus.BAD_REQUEST, 20004, "The feed pagination cursor is invalid or expired.",
            ErrorSeverity.WARN, false, true),
    /**
     * Ranking / scoring service returned no score (degraded mode not available).
     */
    FEED_RANKING_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, 20005, "Feed ranking is temporarily unavailable.",
            ErrorSeverity.WARN, true, true),
    /** Personalization profile not yet built (cold start). */
    FEED_PERSONALIZATION_NOT_READY(
            HttpStatus.SERVICE_UNAVAILABLE, 20006, "The personalized feed is not ready yet; try again shortly.",
            ErrorSeverity.WARN, true, true),
    /**
     * Redis or local feed cache miss with no safe fallback.
     */
    FEED_CACHE_MISS(
            HttpStatus.SERVICE_UNAVAILABLE, 20007, "The feed could not be loaded from cache; please retry.",
            ErrorSeverity.WARN, true, true),
    /** Requested window (time or size) inconsistent with server limits. */
    FEED_WINDOW_PARAMETER_INVALID(
            HttpStatus.BAD_REQUEST, 20008, "Feed time or size window parameters are invalid.",
            ErrorSeverity.WARN, false, true),
    /**
     * Composite index for friend feed failed (e.g. DB error in native query).
     */
    FEED_QUERY_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, 20009, "The feed could not be loaded due to a server error.",
            ErrorSeverity.ERROR, true, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    FeedErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
