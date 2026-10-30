package com.airtribe.relayflow.execution.executor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class DeterministicNodeExecutor {
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final URI mockWorldUrl;

	public DeterministicNodeExecutor(ObjectMapper objectMapper,
			@Value("${relay.mock-world.url:http://localhost:9210}") String mockWorldUrl) {
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		this.mockWorldUrl = URI.create(mockWorldUrl);
	}

	public NodeExecutionResult execute(String runId, int sequence, JsonNode node, JsonNode params) {
		String nodeId = node.path("id").asString();
		String nodeType = node.path("type").asString();
		return switch (nodeType) {
			case "condition" -> executeCondition(node, params);
			case "delay" -> executeDelay(node, params);
			case "notify" -> executeNotify(runId, sequence, node, nodeId, params);
			case "http_request" -> executeHttpRequest(runId, sequence, node, nodeId, params);
			default -> throw new IllegalStateException("Node type '" + nodeType + "' is not implemented by this worker");
		};
	}

	private NodeExecutionResult executeCondition(JsonNode node, JsonNode params) {
		String left = params.path("left").asString();
		String right = params.path("right").asString();
		String operation = params.path("op").asString();
		boolean result = switch (operation) {
			case "equals" -> left.equals(right);
			case "not_equals" -> !left.equals(right);
			case "contains" -> left.contains(right);
			case "greater_than" -> parseNumber(left, operation) > parseNumber(right, operation);
			case "less_than" -> parseNumber(left, operation) < parseNumber(right, operation);
			default -> throw new IllegalStateException("Unsupported condition operator '" + operation + "'");
		};
		ObjectNode output = objectMapper.createObjectNode().put("result", result);
		String branch = node.path(result ? "on_true" : "on_false").isNull()
				? null
				: node.path(result ? "on_true" : "on_false").asString();
		return NodeExecutionResult.next(output, branch);
	}

	private double parseNumber(String value, String operation) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("Condition '" + operation + "' requires numeric values", exception);
		}
	}

	private NodeExecutionResult executeDelay(JsonNode node, JsonNode params) {
		long seconds = params.path("seconds").asLong(-1);
		if (seconds < 0) {
			throw new IllegalStateException("Delay seconds must be a non-negative number");
		}
		String next = node.path("next").isNull() ? null : node.path("next").asString();
		return new NodeExecutionResult(objectMapper.createObjectNode(), next, seconds);
	}

	private NodeExecutionResult executeNotify(String runId, int sequence, JsonNode node,
			String nodeId, JsonNode params) {
		String channel = params.path("channel").asString();
		ObjectNode body = objectMapper.createObjectNode();
		String path;
		if (channel.equals("email")) {
			path = "/email/send";
			body.put("to", params.path("to").asString());
			if (params.has("subject")) {
				body.set("subject", params.path("subject").deepCopy());
			}
		} else if (channel.equals("chat")) {
			path = "/chat/message";
			body.put("channel", params.path("to").asString());
		} else {
			throw new IllegalStateException("Unsupported notify channel '" + channel + "'");
		}
		body.set("message", params.path("message").deepCopy());
		JsonNode response = send("POST", mockWorldUrl.resolve(path), body,
				runId + ":" + nodeId + ":" + sequence);
		String next = node.path("next").isNull() ? null : node.path("next").asString();
		return NodeExecutionResult.next(response.path("body"), next);
	}

	private NodeExecutionResult executeHttpRequest(String runId, int sequence, JsonNode node, String nodeId, JsonNode params) {
		String method = params.path("method").asString();
		URI uri = URI.create(params.path("url").asString());
		JsonNode body = params.path("body");
		String idempotencyKey = runId + ":" + nodeId + ":" + sequence;
		JsonNode response = send(method, uri, body.isMissingNode() || body.isNull()
				? objectMapper.createObjectNode() : body, idempotencyKey);
		String next = node.path("next").isNull() ? null : node.path("next").asString();
		return NodeExecutionResult.next(response, next);
	}

	private JsonNode send(String method, URI uri, JsonNode body, String idempotencyKey) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/json");
			if (idempotencyKey != null) {
				request.header("Idempotency-Key", idempotencyKey);
			}
			HttpRequest.BodyPublisher publisher = method.equals("GET")
					? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
			HttpResponse<String> response = httpClient.send(
					request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("HTTP request returned status " + response.statusCode());
			}
			JsonNode responseBody;
			try {
				responseBody = objectMapper.readTree(response.body());
			} catch (Exception notJson) {
				responseBody = objectMapper.getNodeFactory().textNode(response.body());
			}
			return objectMapper.createObjectNode()
					.put("status", response.statusCode())
					.set("body", responseBody);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to encode HTTP node request", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("HTTP node request was interrupted", exception);
		}
	}
}