package com.airtribe.relayflow.execution.repository;

public class StaleQueueLeaseException extends RuntimeException {
	public StaleQueueLeaseException(long jobId) {
		super("Queue job " + jobId + " is no longer owned by this worker lease");
	}
}