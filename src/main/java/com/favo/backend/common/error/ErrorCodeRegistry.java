package com.favo.backend.common.error;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central index of every {@link ErrorCode} constant. Call {@link #initialize()} once at application
 * startup (e.g. from {@link GlobalExceptionHandler}) so duplicate {@link ErrorCode#getInternalCode()}
 * values fail fast across domains.
 */
public final class ErrorCodeRegistry {

    private static final Map<Integer, ErrorCode> BY_INTERNAL_CODE = new HashMap<>();

    private static volatile boolean initialized;

    private ErrorCodeRegistry() {
    }

    /**
     * Registers all known {@link ErrorCode} enum values and validates global uniqueness of
     * {@link ErrorCode#getInternalCode()}.
     *
     * @throws IllegalStateException if any internal code appears on more than one constant
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        BY_INTERNAL_CODE.clear();
        addEnum(AuthErrorCode.class);
        addEnum(UserErrorCode.class);
        addEnum(ReviewErrorCode.class);
        addEnum(ProductErrorCode.class);
        addEnum(NotificationErrorCode.class);
        addEnum(MessageErrorCode.class);
        addEnum(AiChatErrorCode.class);
        addEnum(MediaErrorCode.class);
        addEnum(EmailErrorCode.class);
        addEnum(ModerationErrorCode.class);
        addEnum(FeedErrorCode.class);
        addEnum(SystemErrorCode.class);
        initialized = true;
    }

    private static <E extends Enum<E> & ErrorCode> void addEnum(Class<E> type) {
        for (E e : type.getEnumConstants()) {
            ErrorCode code = e;
            ErrorCode previous = BY_INTERNAL_CODE.put(code.getInternalCode(), code);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate internal error code: " + code.getInternalCode()
                                + " used by both " + previous.getErrorCode() + " and " + code.getErrorCode());
            }
        }
    }

    /**
     * @return Lookup by internal numeric code (O(1)), if {@link #initialize()} has run
     */
    public static Optional<ErrorCode> findByInternalCode(int internalCode) {
        return Optional.ofNullable(BY_INTERNAL_CODE.get(internalCode));
    }

    /**
     * @return Unmodifiable view of all registered codes after initialization
     */
    public static Map<Integer, ErrorCode> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(BY_INTERNAL_CODE));
    }
}
