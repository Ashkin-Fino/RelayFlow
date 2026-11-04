package com.airtribe.relayflow.common.exception;

public class WorkflowStateConflictException extends RuntimeException {
	public WorkflowStateConflictException(String message) {
		super(message);
	}
}
