package com.airtribe.relayflow.worker.exception;

/**
 * Describes a node execution failure and whether retrying the same logical
 * node execution may succeed.
 */
public class NodeExecutionException extends RuntimeException {
    private final boolean retryable;
    private final Integer httpStatus;

    public NodeExecutionException(String message, boolean retryable) {
        this(message, retryable, null, null);
    }

    public NodeExecutionException(String message, boolean retryable, Integer httpStatus) {
        this(message, retryable, httpStatus, null);
    }

    public NodeExecutionException(String message, boolean retryable, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
