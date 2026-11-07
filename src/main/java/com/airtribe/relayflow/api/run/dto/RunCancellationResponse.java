package com.airtribe.relayflow.api.run.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunCancellationResponse(
		@JsonProperty("run_id") String runId,
		String status,
		@JsonProperty("cancel_requested") boolean cancelRequested) {
}
