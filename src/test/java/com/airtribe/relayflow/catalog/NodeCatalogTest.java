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
		boolean containsOrderAction = false;
		for (JsonNode node : catalog.path("nodes")) {
			if ("order_action".equals(node.path("type").asString())) {
				containsOrderAction = true;
				break;
			}
		}
		assertTrue(containsOrderAction);
	}
}