package com.airtribe.relayflow.common.exception;

public class WebhookUnauthorizedException extends RuntimeException {
	public WebhookUnauthorizedException() {
		super("Missing or invalid webhook secret");
	}
}