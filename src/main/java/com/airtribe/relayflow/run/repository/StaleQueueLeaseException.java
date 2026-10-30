package com.airtribe.relayflow.run.repository;

public class StaleQueueLeaseException extends RuntimeException {
	public StaleQueueLeaseException(long jobId) {
		super("Queue job " + jobId + " is no longer owned by this worker lease");
	}
}