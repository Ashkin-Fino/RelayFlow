package com.airtribe.relayflow.worker.dto;

import tools.jackson.databind.JsonNode;

public record NodeExecutionResult(JsonNode output, String nextNodeId, long delaySeconds,
		Integer tokensPrompt, Integer tokensCompletion) {
	public NodeExecutionResult {
		if ((tokensPrompt != null && tokensPrompt < 0)
				|| (tokensCompletion != null && tokensCompletion < 0)) {
			throw new IllegalArgumentException("AI token usage cannot be negative");
		}
	}

	public NodeExecutionResult(JsonNode output, String nextNodeId, long delaySeconds) {
		this(output, nextNodeId, delaySeconds, null, null);
	}

	public static NodeExecutionResult next(JsonNode output, String nextNodeId) {
		return new NodeExecutionResult(output, nextNodeId, 0);
	}

	public static NodeExecutionResult next(JsonNode output, String nextNodeId,
			int tokensPrompt, int tokensCompletion) {
		return new NodeExecutionResult(output, nextNodeId, 0, tokensPrompt, tokensCompletion);
	}
}