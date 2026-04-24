package com.favo.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard JSON envelope for all API error responses processed by {@link GlobalExceptionHandler}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String errorCode,
        int internalCode,
        String message,
        boolean retryable,
        String timestamp,
        String traceId
) {
}
