package com.airtribe.relayflow.worker.dto;

import tools.jackson.databind.JsonNode;

public record NodeExecutionResult(JsonNode output, String nextNodeId, long delaySeconds) {
	public static NodeExecutionResult next(JsonNode output, String nextNodeId) {
		return new NodeExecutionResult(output, nextNodeId, 0);
	}
}