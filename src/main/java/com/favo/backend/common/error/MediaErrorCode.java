package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Upload, storage, and serving of images and other media (range 17000–17999).
 */
public enum MediaErrorCode implements ErrorCode {
    MEDIA_TYPE_UNSUPPORTED(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, 17001, "The uploaded media type is not allowed.",
            ErrorSeverity.WARN, false, true),
    MEDIA_FILE_SIZE_EXCEEDED(
            HttpStatus.PAYLOAD_TOO_LARGE, 17002, "The uploaded file exceeds the maximum size.",
            ErrorSeverity.WARN, false, true),
    /**
     * Object storage or file system write returned an error (permissions, I/O, quota).
     */
    MEDIA_STORAGE_WRITE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, 17003, "The media file could not be written to storage.",
            ErrorSeverity.ERROR, true, true),
    MEDIA_NOT_FOUND(
            HttpStatus.NOT_FOUND, 17004, "The requested media object was not found.",
            ErrorSeverity.WARN, false, true),
    /**
     * Optional antivirus / malware step reported an infection (details server-only log).
     */
    MEDIA_MALWARE_SCAN_FAILED(
            HttpStatus.UNPROCESSABLE_ENTITY, 17005, "The file failed a security check and was not stored.",
            ErrorSeverity.ERROR, false, true),
    /**
     * Image width/height or pixel count out of product limits.
     */
    IMAGE_DIMENSIONS_EXCEEDED(
            HttpStatus.BAD_REQUEST, 17006, "The image resolution exceeds the allowed maximum.",
            ErrorSeverity.WARN, false, true),
    /** Scan service timed out; treat as unsafe to promote to storage. */
    MEDIA_MALWARE_SCAN_TIMED_OUT(
            HttpStatus.GATEWAY_TIMEOUT, 17007, "The file security check timed out; upload was not completed.",
            ErrorSeverity.WARN, true, true),
    /**
     * Read path failed when streaming or pre-processing stored media.
     */
    MEDIA_STORAGE_READ_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, 17008, "The media file could not be read from storage.",
            ErrorSeverity.ERROR, true, true),
    /** Rare failure when storing encrypted-at-rest blobs. */
    MEDIA_ENCRYPTION_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, 17009, "The media file could not be processed for encryption or decryption.",
            ErrorSeverity.FATAL, true, false);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    MediaErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
