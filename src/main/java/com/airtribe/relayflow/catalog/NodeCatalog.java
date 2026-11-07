package com.airtribe.relayflow.catalog;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NodeCatalog {
	private final JsonNode definition;

	public NodeCatalog(ObjectMapper objectMapper) {
		try (var input = new ClassPathResource("data/node_catalog.json").getInputStream()) {
			this.definition = objectMapper.readTree(input);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load data/node_catalog.json", exception);
		}

		if (!definition.path("nodes").isArray() || !definition.path("triggers").isArray()) {
			throw new IllegalStateException("Node catalog must define nodes and triggers arrays");
		}
	}

	public JsonNode definition() {
		return definition.deepCopy();
	}
}