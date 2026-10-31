package com.airtribe.relayflow.common.exception;

public class WorkflowConflictException extends RuntimeException {
	public WorkflowConflictException(String workflowId) {
		super("Workflow '" + workflowId + "' already exists");
	}
}