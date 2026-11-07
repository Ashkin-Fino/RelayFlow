package com.airtribe.relayflow.common.exception;

public class ApprovalNotFoundException extends RuntimeException {
	public ApprovalNotFoundException(String approvalId) {
		super("Approval not found: " + approvalId);
	}
}
