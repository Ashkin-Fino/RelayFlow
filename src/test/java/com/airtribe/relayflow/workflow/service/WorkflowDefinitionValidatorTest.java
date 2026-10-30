package com.airtribe.relayflow.workflow.service;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.airtribe.relayflow.catalog.NodeCatalog;
import com.airtribe.relayflow.workflow.exceptions.WorkflowDefinitionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class WorkflowDefinitionValidatorTest {
	private final ObjectMapper objectMapper = new ObjectMapper();
	private WorkflowDefinitionValidator validator;

	@BeforeEach
	void setUp() {
		validator = new WorkflowDefinitionValidator(new NodeCatalog(objectMapper));
	}

	@Test
	void validatesAllCanonicalSeedDefinitions() throws IOException {
		JsonNode seeds;
		try (var input = new ClassPathResource("data/seed_workflows.json").getInputStream()) {
			seeds = objectMapper.readTree(input).path("workflows");
		}

		for (JsonNode workflow : seeds) {
			assertDoesNotThrow(() -> validator.validate(workflow), workflow.path("id").asString());
		}
	}

	@Test
	void allowsBackwardAndSelfEdgesForLoops() throws IOException {
		ObjectNode workflow = parseWorkflow();
		workflow.put("entry", "wait");
		ObjectNode node = (ObjectNode) workflow.path("nodes").get(0);
		node.put("type", "delay");
		node.putObject("params").put("seconds", 1);
		node.put("id", "wait");
		node.put("next", "wait");

		assertDoesNotThrow(() -> validator.validate(workflow));
	}

	@Test
	void rejectsUnknownNodeType() throws IOException {
		ObjectNode workflow = parseWorkflow();
		((ObjectNode) workflow.path("nodes").get(0)).put("type", "teleport");

		assertError("invalid_node_type", workflow);
	}

	@Test
	void rejectsMissingRequiredParameter() throws IOException {
		ObjectNode workflow = parseWorkflow();
		((ObjectNode) workflow.path("nodes").get(0).path("params")).remove("message");

		assertError("missing_required_param", workflow);
	}

	@Test
	void rejectsInvalidEnumValue() throws IOException {
		ObjectNode workflow = parseWorkflow();
		((ObjectNode) workflow.path("nodes").get(0).path("params")).put("channel", "sms");

		assertError("invalid_enum_value", workflow);
	}

	@Test
	void rejectsEntryThatDoesNotIdentifyANode() throws IOException {
		ObjectNode workflow = parseWorkflow();
		workflow.put("entry", "ghost");

		assertError("invalid_entry", workflow);
	}

	@Test
	void rejectsEdgeThatDoesNotIdentifyANode() throws IOException {
		ObjectNode workflow = parseWorkflow();
		((ObjectNode) workflow.path("nodes").get(0)).put("next", "ghost");

		assertError("invalid_pointer", workflow);
	}

	private ObjectNode parseWorkflow() throws IOException {
		return (ObjectNode) objectMapper.readTree("""
				{
				  "id": "wf_validation_test",
				  "name": "Validation test",
				  "trigger": { "type": "manual" },
				  "entry": "hello",
				  "limits": { "max_steps": 5 },
				  "nodes": [{
				    "id": "hello",
				    "type": "notify",
				    "params": { "channel": "chat", "to": "#test", "message": "Hello" },
				    "next": null
				  }]
				}
				""");
	}

	private void assertError(String code, JsonNode definition) {
		WorkflowDefinitionException exception = assertThrows(
				WorkflowDefinitionException.class,
				() -> validator.validate(definition));
		assertEquals(code, exception.code());
	}
}