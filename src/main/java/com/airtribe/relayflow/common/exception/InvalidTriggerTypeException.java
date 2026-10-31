package com.airtribe.relayflow.common.exception;

public class InvalidTriggerTypeException extends RuntimeException {
	public InvalidTriggerTypeException(String expectedType) {
		super("Workflow cannot be started with this trigger; expected '" + expectedType + "'");
	}
}