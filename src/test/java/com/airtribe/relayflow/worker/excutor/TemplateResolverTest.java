package com.airtribe.relayflow.worker.excutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TemplateResolverTest {
	private final ObjectMapper mapper = new ObjectMapper();
	private final TemplateResolver resolver = new TemplateResolver(mapper);

	@Test
	void resolvesNestedObjectAndArrayPathsAndPreservesWholeValueTypes() {
		JsonNode trigger = mapper.readTree("""
				{"orders":[{"amount":42}]}
				""");
		JsonNode template = mapper.readTree("""
				{"amount":"{{trigger.body.orders.0.amount}}",
				 "data":"{{nodes.classify.output.category}}",
				 "text":"Order {{trigger.body.orders.0.amount}}"}
				""");
		JsonNode output = mapper.readTree("{\"category\":\"question\"}");

		JsonNode resolved = resolver.resolve(template, trigger, Map.of("classify", output));

		assertEquals(42, resolved.path("amount").asInt());
		assertEquals("question", resolved.path("data").asString());
		assertEquals("Order 42", resolved.path("text").asString());
	}

	@Test
	void failsClearlyForMissingNestedArrayPath() {
		JsonNode template = mapper.readTree("\"{{trigger.body.orders.4.amount}}\"");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(template, mapper.readTree("{\"orders\":[]}"), Map.of()));

		assertEquals("Unresolved template path '{{trigger.body.orders.4.amount}}'", error.getMessage());
	}
}
