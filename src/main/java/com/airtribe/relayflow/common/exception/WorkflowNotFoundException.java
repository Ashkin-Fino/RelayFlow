package com.airtribe.relayflow.common.exception;

public class WorkflowNotFoundException extends RuntimeException {
	public WorkflowNotFoundException(String workflowId) {
		super("Workflow '" + workflowId + "' was not found");
	}
}