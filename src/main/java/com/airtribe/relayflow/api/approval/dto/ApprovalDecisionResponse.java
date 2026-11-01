package com.airtribe.relayflow.api.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApprovalDecisionResponse(
		@JsonProperty("approval_id") String approvalId,
		@JsonProperty("run_id") String runId,
		String status) {
}
