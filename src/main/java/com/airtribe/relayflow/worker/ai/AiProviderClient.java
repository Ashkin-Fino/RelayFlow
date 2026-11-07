package com.airtribe.relayflow.worker.ai;

import tools.jackson.databind.JsonNode;

public interface AiProviderClient {
	Completion complete(String prompt, JsonNode outputSchema);

	record Completion(String content, int promptTokens, int completionTokens) {
		public Completion {
			if (content == null || promptTokens < 0 || completionTokens < 0) {
				throw new IllegalArgumentException("AI completion content and token usage must be valid");
			}
		}
	}
}
