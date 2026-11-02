package com.airtribe.relayflow.api.run.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

public record RunTraceResponse(
		@JsonProperty("run_id") String runId,
		@JsonProperty("workflow_id") String workflowId,
		String status,
		@JsonProperty("trigger_type") String triggerType,
		@JsonProperty("current_node_id") String currentNodeId,
		@JsonProperty("steps_executed") int stepsExecuted,
		@JsonProperty("ai_tokens_used") long aiTokensUsed,
		@JsonProperty("cancel_requested") boolean cancelRequested,
		JsonNode error,
		@JsonProperty("started_at") Instant startedAt,
		@JsonProperty("finished_at") Instant finishedAt,
		List<StepTraceResponse> steps) {
}
