package com.airtribe.relayflow.run.exceptions;

public class WebhookUnauthorizedException extends RuntimeException {
	public WebhookUnauthorizedException() {
		super("Missing or invalid webhook secret");
	}
}