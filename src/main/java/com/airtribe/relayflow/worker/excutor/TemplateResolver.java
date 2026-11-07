package com.airtribe.relayflow.worker.excutor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class TemplateResolver {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

	private final ObjectMapper objectMapper;

	public TemplateResolver(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public JsonNode resolve(JsonNode value, JsonNode triggerBody, Map<String, JsonNode> nodeOutputs) {
		/**
		 * Resolves placeholders in the given JSON value using the provided 
		 * trigger body and node outputs.
		 * 
		 * @param value The JSON value containing placeholders to resolve.
		 * @param triggerBody The JSON body of the trigger event.
		 * @param nodeOutputs A map of node IDs to their respective output JSON nodes.
		 * @return A new JSON node with all placeholders resolved.
		 */
		if (value.isObject()) {
			ObjectNode resolved = objectMapper.createObjectNode();
			for (var property : value.properties()) {
				resolved.set(property.getKey(), resolve(property.getValue(), triggerBody, nodeOutputs));
			}
			return resolved;
		}
		if (value.isArray()) {
			ArrayNode resolved = objectMapper.createArrayNode();
			for (JsonNode element : value) {
				resolved.add(resolve(element, triggerBody, nodeOutputs));
			}
			return resolved;
		}
		if (!value.isString()) {
			return value.deepCopy();
		}

		String text = value.asString();
		Matcher wholeValue = PLACEHOLDER.matcher(text.trim());
		if (wholeValue.matches()) {
			return resolvePath(wholeValue.group(1), triggerBody, nodeOutputs).deepCopy();
		}

		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder resolved = new StringBuilder();
		int previousEnd = 0;
		while (matcher.find()) {
			resolved.append(text, previousEnd, matcher.start());
			JsonNode replacement = resolvePath(matcher.group(1), triggerBody, nodeOutputs);
			resolved.append(replacement.isString() ? replacement.asString() : replacement.toString());
			previousEnd = matcher.end();
		}
		if (previousEnd == 0) {
			return value.deepCopy();
		}
		resolved.append(text.substring(previousEnd));
		return objectMapper.getNodeFactory().textNode(resolved.toString());
	}

	private JsonNode resolvePath(String expression, JsonNode triggerBody, Map<String, JsonNode> nodeOutputs) {
		/**
		 * Resolves a template path expression to a JSON node using the provided
		 * trigger body and node outputs.
		 * 
		 * @param expression The template path expression to resolve.
		 * @param triggerBody The JSON body of the trigger event.
		 * @param nodeOutputs A map of node IDs to their respective output JSON nodes.
		 * @return The resolved JSON node corresponding to the template path.
		 */
		String[] parts = expression.trim().split("\\.");
		JsonNode current;
		int index;
		if (parts.length >= 2 && parts[0].equals("trigger") && parts[1].equals("body")) {
			current = triggerBody;
			index = 2;
		} else if (parts.length >= 4 && parts[0].equals("nodes") && parts[2].equals("output")) {
			current = nodeOutputs.get(parts[1]);
			index = 3;
		} else {
			throw new IllegalArgumentException("Unsupported template path '{{" + expression + "}}'");
		}

		for (; index < parts.length && current != null; index++) {
			String part = parts[index];
			if (current.isArray()) {
				try {
					int arrayIndex = Integer.parseInt(part);
					current = arrayIndex < 0 ? null : current.get(arrayIndex);
				} catch (NumberFormatException exception) {
					current = null;
				}
			} else if (current.isObject()) {
				current = current.get(part);
			} else {
				current = null;
			}
		}
		if (current == null || current.isMissingNode() || current.isNull()) {
			throw new IllegalArgumentException("Unresolved template path '{{" + expression + "}}'");
		}
		return current;
	}
}