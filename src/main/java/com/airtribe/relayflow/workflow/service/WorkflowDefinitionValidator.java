package com.airtribe.relayflow.workflow.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.airtribe.relayflow.catalog.NodeCatalog;
import com.airtribe.relayflow.workflow.exceptions.WorkflowDefinitionException;
import tools.jackson.databind.JsonNode;

@Component
public class WorkflowDefinitionValidator {
	private final NodeCatalog nodeCatalog;

	public WorkflowDefinitionValidator(NodeCatalog nodeCatalog) {
		this.nodeCatalog = nodeCatalog;
	}

	public void validate(JsonNode definition) {
		if (definition == null || !definition.isObject()) {
			throw invalid("invalid_definition", "Workflow definition must be a JSON object");
		}

		requiredText(definition, "id", "Workflow");
		requiredText(definition, "name", "Workflow");
		validateTrigger(definition.path("trigger"));
		validateLimits(definition.path("limits"));

		JsonNode nodes = definition.path("nodes");
		if (!nodes.isArray() || nodes.isEmpty()) {
			throw invalid("invalid_definition", "Workflow must contain a non-empty nodes array");
		}

		Map<String, JsonNode> nodesById = new HashMap<>();
		List<JsonNode> nodeSpecs = new ArrayList<>();
		Set<String> nodeIds = new HashSet<>();
		for (JsonNode node : nodes) {
			String nodeId = requiredText(node, "id", "Node");
			String nodeType = requiredText(node, "type", "Node '" + nodeId + "'");
			if (!nodeIds.add(nodeId)) {
				throw invalid("duplicate_node_id", "Workflow has duplicate node id '" + nodeId + "'");
			}

			JsonNode nodeSpec = findByType(nodeCatalog.definition().path("nodes"), nodeType);
			if (nodeSpec == null) {
				throw invalid("invalid_node_type", "Node '" + nodeId + "' has unknown type '" + nodeType + "'");
			}
			JsonNode params = node.path("params");
			if (!params.isObject()) {
				throw invalid("invalid_node_params", "Node '" + nodeId + "' params must be a JSON object");
			}
			validateParameters(params, nodeSpec.path("params"), "Node '" + nodeId + "'");
			nodesById.put(nodeId, node);
			nodeSpecs.add(nodeSpec);
		}

		String entry = requiredText(definition, "entry", "Workflow");
		if (!nodeIds.contains(entry)) {
			throw invalid("invalid_entry", "Workflow entry '" + entry + "' does not identify a node");
		}

		int index = 0;
		for (JsonNode node : nodes) {
			validateEdges(node, nodeSpecs.get(index++), nodesById.keySet());
		}
	}

	private void validateTrigger(JsonNode trigger) {
		if (!trigger.isObject()) {
			throw invalid("invalid_trigger", "Workflow trigger must be a JSON object");
		}
		String triggerType = requiredText(trigger, "type", "Workflow trigger");
		JsonNode triggerSpec = findByType(nodeCatalog.definition().path("triggers"), triggerType);
		if (triggerSpec == null) {
			throw invalid("invalid_trigger_type", "Workflow has unknown trigger type '" + triggerType + "'");
		}
		validateParameters(trigger, triggerSpec.path("config"), "Trigger '" + triggerType + "'");
	}

	private void validateLimits(JsonNode limits) {
		if (!limits.isObject()) {
			throw invalid("invalid_limits", "Workflow limits must be a JSON object");
		}
		JsonNode maxSteps = limits.path("max_steps");
		if (!maxSteps.isIntegralNumber() || maxSteps.asInt() < 1) {
			throw invalid("invalid_limits", "Workflow limits.max_steps must be a positive integer");
		}
		for (String optionalLimit : List.of("timeout_seconds", "max_ai_tokens")) {
			JsonNode value = limits.path(optionalLimit);
			if (!value.isMissingNode() && (!value.isNumber() || value.asDouble() < 0)) {
				throw invalid("invalid_limits", "Workflow limits." + optionalLimit + " must be a non-negative number");
			}
		}
	}

	private void validateParameters(JsonNode values, JsonNode specs, String context) {
		for (var parameter : specs.properties()) {
			String name = parameter.getKey();
			JsonNode spec = parameter.getValue();
			JsonNode value = values.path(name);
			boolean present = values.has(name) && !value.isNull();
			if (!present) {
				if (spec.path("required").asBoolean(false)) {
					throw invalid("missing_required_param", context + " is missing required parameter '" + name + "'");
				}
				continue;
			}

			String type = spec.path("type").asString("");
			if (!matchesType(value, type, spec.path("templatable").asBoolean(false))) {
				throw invalid("invalid_param_type", context + " parameter '" + name + "' must be " + type);
			}
			if (spec.path("required").asBoolean(false) && value.isString() && value.asString().isBlank()) {
				throw invalid("missing_required_param", context + " parameter '" + name + "' must not be blank");
			}
			validateEnum(value, spec.path("enum"), context, name);
		}
	}

	private boolean matchesType(JsonNode value, String type, boolean templatable) {
		return switch (type) {
			case "string" -> value.isString();
			case "number" -> value.isNumber();
			case "object" -> value.isObject() || (templatable && isWholeTemplate(value));
			case "boolean" -> value.isBoolean();
			default -> true;
		};
	}

	private boolean isWholeTemplate(JsonNode value) {
		return value.isString() && value.asString().trim().matches("\\{\\{\\s*[^{}]+\\s*}}");
	}

	private void validateEnum(JsonNode value, JsonNode allowedValues, String context, String name) {
		if (!allowedValues.isArray()) {
			return;
		}
		for (JsonNode allowedValue : allowedValues) {
			if (allowedValue.equals(value)) {
				return;
			}
		}
		throw invalid("invalid_enum_value", context + " parameter '" + name + "' has an unsupported value");
	}

	private void validateEdges(JsonNode node, JsonNode nodeSpec, Set<String> nodeIds) {
		JsonNode branches = nodeSpec.path("branches");
		List<String> edgeNames = new ArrayList<>();
		if (branches.isArray()) {
			for (JsonNode branch : branches) {
				edgeNames.add(branch.asString());
			}
		} else {
			edgeNames.add("next");
		}

		String nodeId = node.path("id").asString();
		for (String edgeName : edgeNames) {
			if (!node.has(edgeName)) {
				throw invalid("invalid_pointer", "Node '" + nodeId + "' is missing edge '" + edgeName + "'");
			}
			JsonNode target = node.path(edgeName);
			if (!target.isNull() && (!target.isString() || !nodeIds.contains(target.asString()))) {
				throw invalid("invalid_pointer", "Node '" + nodeId + "' edge '" + edgeName + "' points to an unknown node");
			}
		}
	}

	private JsonNode findByType(JsonNode definitions, String type) {
		for (JsonNode definition : definitions) {
			if (type.equals(definition.path("type").asString())) {
				return definition;
			}
		}
		return null;
	}

	private String requiredText(JsonNode node, String field, String context) {
		JsonNode value = node.path(field);
		if (!value.isString() || value.asString().isBlank()) {
			throw invalid("invalid_definition", context + " is missing required text field '" + field + "'");
		}
		return value.asString().trim();
	}

	private WorkflowDefinitionException invalid(String code, String message) {
		return new WorkflowDefinitionException(code, message);
	}
}