package com.airtribe.relayflow.run.exceptions;

public class RunNotFoundException extends RuntimeException {
	public RunNotFoundException(String runId) {
		super("Run '" + runId + "' was not found");
	}
}