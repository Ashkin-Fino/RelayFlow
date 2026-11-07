package com.airtribe.relayflow.common.exception;

/**
 * Describes a node execution failure and whether retrying the same logical
 * node execution may succeed.
 */
public class NodeExecutionException extends RuntimeException {
    private final boolean retryable;
    private final Integer httpStatus;
    private final Integer tokensPrompt;
    private final Integer tokensCompletion;

    public NodeExecutionException(String message, boolean retryable) {
        this(message, retryable, null, null);
    }

    public NodeExecutionException(String message, boolean retryable, Integer httpStatus) {
        this(message, retryable, httpStatus, null);
    }

    public NodeExecutionException(String message, boolean retryable, Integer httpStatus, Throwable cause) {
        this(message, retryable, httpStatus, cause, null, null);
    }

    public NodeExecutionException(String message, boolean retryable, Integer httpStatus, Throwable cause,
            Integer tokensPrompt, Integer tokensCompletion) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
        this.tokensPrompt = tokensPrompt;
        this.tokensCompletion = tokensCompletion;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Integer getTokensPrompt() {
        return tokensPrompt;
    }

    public Integer getTokensCompletion() {
        return tokensCompletion;
    }
}
