package com.airtribe.relayflow.run.exceptions;

public class WorkflowNotPublishedException extends RuntimeException {
	public WorkflowNotPublishedException(String workflowId) {
		super("Workflow '" + workflowId + "' is not published");
	}
}