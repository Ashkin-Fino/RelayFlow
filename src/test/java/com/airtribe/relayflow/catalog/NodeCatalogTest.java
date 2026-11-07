package com.airtribe.relayflow.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NodeCatalogTest {
	@Test
	void loadsPackagedCatalog() {
		var catalog = new NodeCatalog(new ObjectMapper()).definition();

		assertEquals(7, catalog.path("nodes").size());
		assertEquals(3, catalog.path("triggers").size());
		assertTrue(containsNodeType(catalog, "approval"), "Catalog should include the approval node");
		assertTrue(containsNodeType(catalog, "order_action"), "Catalog should include the sensitive action node");
	}

	private boolean containsNodeType(JsonNode catalog, String expectedType) {
		for (JsonNode node : catalog.path("nodes")) {
			if (expectedType.equals(node.path("type").asString())) {
				return true;
			}
		}
		return false;
	}
}