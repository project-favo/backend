package com.favo.backend.common.error;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Platform-wide {@link RuntimeException} that always carries a registered {@link ErrorCode}
 * and optional structured context for logging (never for sensitive data).
 */
public final class FavoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final Map<String, Object> context;

    public FavoException(ErrorCode errorCode) {
        this(Objects.requireNonNull(errorCode, "errorCode"), Map.of(), null);
    }

    public FavoException(ErrorCode errorCode, Map<String, Object> context) {
        this(errorCode, context, null);
    }

    public FavoException(ErrorCode errorCode, Throwable cause) {
        this(Objects.requireNonNull(errorCode, "errorCode"), Map.of(), cause);
    }

    public FavoException(ErrorCode errorCode, Map<String, Object> context, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode").getMessage(), cause);
        this.errorCode = errorCode;
        this.context = context == null || context.isEmpty() ? Map.of() : Map.copyOf(context);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * @return Unmodifiable key/value pairs for support and logs (field names, ids); never PII in production.
     */
    public Map<String, Object> getContext() {
        return Collections.unmodifiableMap(context);
    }
}
