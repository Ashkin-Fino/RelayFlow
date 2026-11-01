package com.airtribe.relayflow.worker.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.fasterxml.jackson.databind.ObjectMapper;

import tools.jackson.databind.JsonNode;

@Component
public class AiOutputSchemaValidator {
	private final ObjectMapper schemaMapper =
			new ObjectMapper();
	private final JsonSchemaFactory schemaFactory =
			JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

	public List<String> validate(JsonNode schemaDefinition, JsonNode output) {
		/**
		 * Validates the AI output against the provided schema.
		 *
		 * @param schemaDefinition The JSON schema definition.
		 * @param output The AI output to validate.
		 * @return A list of validation errors, if any.
		 */
		if (!schemaDefinition.isObject() || !schemaDefinition.path("type").asString().equals("object")) {
			throw new IllegalArgumentException("AI output_schema must describe a JSON object");
		}
		rejectRemoteReferences(schemaDefinition);

		try {
			com.fasterxml.jackson.databind.JsonNode schemaNode =
					schemaMapper.readTree(schemaDefinition.toString());
			com.fasterxml.jackson.databind.JsonNode outputNode = schemaMapper.readTree(output.toString());
			JsonSchema schema = schemaFactory.getSchema(schemaNode);
			List<String> errors = new ArrayList<>();
			schema.validate(outputNode).stream()
					.map(Object::toString)
					.sorted()
					.forEach(errors::add);
			return errors;
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("AI output_schema is invalid", exception);
		}
	}

	private void rejectRemoteReferences(JsonNode schema) {
		if (schema.isObject()) {
			for (var property : schema.properties()) {
				if ((property.getKey().equals("$ref") || property.getKey().equals("$dynamicRef"))
						&& property.getValue().isString()
						&& !property.getValue().asString().startsWith("#")) {
					throw new IllegalArgumentException("AI output_schema may only use local JSON Schema references");
				}
				rejectRemoteReferences(property.getValue());
			}
		} else if (schema.isArray()) {
			for (JsonNode child : schema) {
				rejectRemoteReferences(child);
			}
		}
	}
}
