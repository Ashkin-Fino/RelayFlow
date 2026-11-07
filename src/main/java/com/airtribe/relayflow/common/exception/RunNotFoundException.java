package com.airtribe.relayflow.common.exception;

public class RunNotFoundException extends RuntimeException {
	public RunNotFoundException(String runId) {
		super("Run '" + runId + "' was not found");
	}
}