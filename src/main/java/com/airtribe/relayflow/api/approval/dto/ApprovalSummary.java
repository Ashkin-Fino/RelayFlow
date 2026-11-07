package com.airtribe.relayflow.api.approval.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApprovalSummary(
		String id,
		@JsonProperty("run_id") String runId,
		@JsonProperty("node_id") String nodeId,
		String message,
		String status,
		@JsonProperty("created_at") Instant createdAt) {
}
