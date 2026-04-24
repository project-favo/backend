package com.favo.backend.common.error;

/**
 * Relative importance of a failure for operations and log routing. Does not
 * determine HTTP status; see {@link ErrorCode#getHttpStatus()}.
 */
public enum ErrorSeverity {

    /**
     * Process or request cannot continue; requires immediate human attention
     * (data corruption, security breach, invariant violation in production).
     */
    FATAL,
    /**
     * Operation failed; user or system action required, often visible to clients.
     */
    ERROR,
    /**
     * Unexpected but non-blocking condition; may succeed on retry or alternate path.
     */
    WARN,
    /**
     * Informational (expected branch or diagnostic); not an operational incident.
     */
    INFO
}
