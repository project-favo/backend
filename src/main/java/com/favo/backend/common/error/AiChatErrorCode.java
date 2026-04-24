package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * OpenAI chat (general and product-scoped) integration (range 16000–16999).
 */
public enum AiChatErrorCode implements ErrorCode {
    /**
     * Network, DNS, or connection failure before a successful HTTP response from OpenAI.
     */
    OPENAI_API_UNREACHABLE(
            HttpStatus.BAD_GATEWAY, 16001, "The OpenAI API could not be reached.",
            ErrorSeverity.WARN, true, true),
    OPENAI_RATE_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, 16002, "OpenAI rate limit was exceeded; retry with backoff.",
            ErrorSeverity.WARN, true, true),
    /**
     * Combined system + user history exceeds the model context length.
     */
    OPENAI_CONTEXT_WINDOW_OVERFLOW(
            HttpStatus.PAYLOAD_TOO_LARGE, 16003, "The prompt and history are too long for the model context window.",
            ErrorSeverity.WARN, false, true),
    /**
     * Heuristic or classifier detected likely prompt-injection; request was rejected.
     */
    CHAT_PROMPT_INJECTION_SUSPECTED(
            HttpStatus.BAD_REQUEST, 16004, "The request was rejected for safety (possible prompt abuse).",
            ErrorSeverity.WARN, false, true),
    /**
     * Product id supplied for a product chat does not map to a visible product in this tenant.
     */
    AI_PRODUCT_CONTEXT_NOT_FOUND(
            HttpStatus.NOT_FOUND, 16005, "The product context for AI chat was not found.",
            ErrorSeverity.WARN, false, true),
    /**
     * Could not map OpenAI tool/stream response into a stable application DTO.
     */
    AI_RESPONSE_PARSE_FAILED(
            HttpStatus.BAD_GATEWAY, 16006, "The chat response from OpenAI was not in the expected format.",
            ErrorSeverity.ERROR, true, true),
    /**
     * Server-side configuration or key issue (surface generic message; details server-only log).
     */
    OPENAI_CONFIGURATION_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR, 16007, "OpenAI integration is not configured for this environment.",
            ErrorSeverity.FATAL, false, false),
    CHAT_CONTENT_POLICY_REFUSAL(
            HttpStatus.BAD_REQUEST, 16008, "The model refused to answer due to content policy (OpenAI or app policy).",
            ErrorSeverity.WARN, false, true),
    /**
     * Requested model name is not available or is deprecated in this deployment.
     */
    OPENAI_MODEL_NOT_AVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, 16009, "The selected chat model is not available. Try again later.",
            ErrorSeverity.WARN, true, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    AiChatErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
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
