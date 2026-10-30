package com.airtribe.relayflow.workflow.exceptions;

public class WorkflowNotFoundException extends RuntimeException {
	public WorkflowNotFoundException(String workflowId) {
		super("Workflow '" + workflowId + "' was not found");
	}
}