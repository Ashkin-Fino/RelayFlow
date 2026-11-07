package com.airtribe.relayflow.api.workflow.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

public record WorkflowDetailResponse(
		String id,
		String name,
		String description,
		String status,
		JsonNode definition,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("updated_at") Instant updatedAt) {
}
