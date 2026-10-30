package com.airtribe.relayflow.run.exceptions;

public class InvalidTriggerTypeException extends RuntimeException {
	public InvalidTriggerTypeException(String expectedType) {
		super("Workflow cannot be started with this trigger; expected '" + expectedType + "'");
	}
}