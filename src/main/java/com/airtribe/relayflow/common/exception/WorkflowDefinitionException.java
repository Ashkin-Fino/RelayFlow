package com.airtribe.relayflow.common.exception;

public class WorkflowDefinitionException extends RuntimeException {
	private final String code;

	public WorkflowDefinitionException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}