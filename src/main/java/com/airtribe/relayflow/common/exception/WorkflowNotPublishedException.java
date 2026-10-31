package com.airtribe.relayflow.common.exception;

public class WorkflowNotPublishedException extends RuntimeException {
	public WorkflowNotPublishedException(String workflowId) {
		super("Workflow '" + workflowId + "' is not published");
	}
}