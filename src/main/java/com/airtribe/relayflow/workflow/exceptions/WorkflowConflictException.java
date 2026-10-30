package com.airtribe.relayflow.workflow.exceptions;

public class WorkflowConflictException extends RuntimeException {
	public WorkflowConflictException(String workflowId) {
		super("Workflow '" + workflowId + "' already exists");
	}
}