package com.airtribe.relayflow.api.run.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunSummary(
		@JsonProperty("run_id") String runId,
		@JsonProperty("workflow_id") String workflowId,
		String status,
		@JsonProperty("trigger_type") String triggerType,
		@JsonProperty("current_node_id") String currentNodeId,
		@JsonProperty("steps_executed") int stepsExecuted,
		@JsonProperty("ai_tokens_used") long aiTokensUsed,
		@JsonProperty("cancel_requested") boolean cancelRequested,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("started_at") Instant startedAt,
		@JsonProperty("finished_at") Instant finishedAt) {
}
