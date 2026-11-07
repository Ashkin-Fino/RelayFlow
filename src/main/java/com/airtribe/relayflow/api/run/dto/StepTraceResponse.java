package com.airtribe.relayflow.api.run.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

public record StepTraceResponse(
		@JsonProperty("node_id") String nodeId,
		String type,
		String status,
		int sequence,
		int attempt,
		JsonNode input,
		JsonNode output,
		JsonNode error,
		@JsonProperty("started_at") Instant startedAt,
		@JsonProperty("finished_at") Instant finishedAt,
		@JsonProperty("duration_ms") Long durationMs,
		@JsonProperty("tokens_prompt") Integer tokensPrompt,
		@JsonProperty("tokens_completion") Integer tokensCompletion,
		@JsonProperty("idempotency_key") String idempotencyKey,
		@JsonProperty("approval_id") String approvalId,
		@JsonProperty("approval_status") String approvalStatus,
		@JsonProperty("approval_decided_by") String approvalDecidedBy,
		@JsonProperty("approval_decided_at") Instant approvalDecidedAt,
		@JsonProperty("approval_reason") String approvalReason) {
}
