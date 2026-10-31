package com.airtribe.relayflow.common.exception;

public class StaleQueueLeaseException extends RuntimeException {
	public StaleQueueLeaseException(long jobId) {
		super("Queue job " + jobId + " is no longer owned by this worker lease");
	}
}