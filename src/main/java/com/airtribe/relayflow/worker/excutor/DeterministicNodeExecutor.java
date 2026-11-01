package com.airtribe.relayflow.worker.excutor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.airtribe.relayflow.common.exception.NodeExecutionException;
import com.airtribe.relayflow.worker.ai.AiNodeExecutor;
import com.airtribe.relayflow.worker.dto.NodeExecutionResult;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class DeterministicNodeExecutor {
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final URI mockWorldUrl;
	private final AiNodeExecutor aiNodeExecutor;

	private static final Logger log = LoggerFactory.getLogger(DeterministicNodeExecutor.class);

	@Autowired
	public DeterministicNodeExecutor(ObjectMapper objectMapper,
			@Value("${relay.mock-world.url}") String mockWorldUrl, AiNodeExecutor aiNodeExecutor) {
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		this.mockWorldUrl = URI.create(mockWorldUrl);
		this.aiNodeExecutor = aiNodeExecutor;
	}

	public DeterministicNodeExecutor(ObjectMapper objectMapper, String mockWorldUrl) {
		this(objectMapper, mockWorldUrl, null);
	}

	public NodeExecutionResult execute(String runId, int sequence, JsonNode node, JsonNode params) {
		/**
		 * Executes a node based on its type and parameters.
		 *
		 * @param runId The ID of the run.
		 * @param sequence The sequence number of the step.
		 * @param node The node definition.
		 * @param params The resolved parameters for the node.
		 * @return The result of the node execution.
		 */
        log.info(
            "Worker executing run={}, node={}",
            runId, node.path("id").asText()
        );
		String nodeId = node.path("id").asString();
		String nodeType = node.path("type").asString();
		return switch (nodeType) {
			case "condition" -> executeCondition(node, params);
			case "delay" -> executeDelay(node, params);
			case "notify" -> executeNotify(runId, sequence, node, nodeId, params);
			case "http_request" -> executeHttpRequest(runId, sequence, node, nodeId, params);
			case "ai" -> executeAi(node, params);
			default -> throw new IllegalStateException("Node type '" + nodeType + "' is not implemented by this worker");
		};
	}

	private NodeExecutionResult executeAi(JsonNode node, JsonNode params) {
		if (aiNodeExecutor == null) {
			throw new IllegalStateException("AI node execution is not configured");
		}
		return aiNodeExecutor.execute(node, params);
	}

	private NodeExecutionResult executeCondition(JsonNode node, JsonNode params) {
		/**
		 * Executes a condition node by evaluating the specified operation on the left and right parameters.
		 * 
		 * @param node The node definition containing the condition details.
		 * @param params The resolved parameters for the condition node.
		 * @return The result of the condition node execution, including the output and the next branch to follow.
		 */
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
				? null : node.path(result ? "on_true" : "on_false").asString();
		return NodeExecutionResult.next(output, branch);
	}

	private double parseNumber(String value, String operation) {
		// Parses a string value into a double for numeric comparisons in condition nodes.
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("Condition '" + operation + "' requires numeric values", exception);
		}
	}

	private NodeExecutionResult executeDelay(JsonNode node, JsonNode params) {
		/**
		 * Executes a delay node by returning the specified number of seconds 
		 * to delay before proceeding to the next node.
		 * 
		 * @param node The node definition containing the delay details.
		 * @param params The resolved parameters for the delay node.
		 * @return The result of the delay node execution, including the output and 
		 *         the next node to proceed to after the delay.
		 */
		long seconds = params.path("seconds").asLong(-1);
		if (seconds < 0) {
			throw new IllegalStateException("Delay seconds must be a non-negative number");
		}
		String next = node.path("next").isNull() ? null : node.path("next").asString();
		return new NodeExecutionResult(objectMapper.createObjectNode(), next, seconds);
	}

	private NodeExecutionResult executeNotify(String runId, int sequence, JsonNode node,
			String nodeId, JsonNode params) {
		/**
		 * Executes a notify node by sending a notification through the 
		 * specified channel (email or chat).
		 * 
		 * @param runId The ID of the run.
		 * @param sequence The sequence number of the step.
		 * @param node The node definition containing the notification details.
		 * @param nodeId The ID of the node.
		 * @param params The resolved parameters for the notify node.
		 * @return The result of the notify node execution.
		 */
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

	private NodeExecutionResult executeHttpRequest(String runId, int sequence, 
			JsonNode node, String nodeId, JsonNode params) {
		/**
		 * Executes an HTTP request node by sending a request to the specified 
		 * URL with the given method and body.
		 * 
		 * @param runId The ID of the run.
		 * @param sequence The sequence number of the step.
		 * @param node The node definition containing the HTTP request details.
		 * @param nodeId The ID of the node.
		 * @param params The resolved parameters for the HTTP request node.
		 * @return The result of the HTTP request execution.
		 */
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
		String serializedBody;
		try {
			serializedBody = objectMapper.writeValueAsString(body);
		} catch (JacksonException exception) {
			throw new NodeExecutionException("Unable to encode HTTP node request", false, null, exception);
		}

		HttpRequest.Builder request;
		try {
			request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/json");
		} catch (IllegalArgumentException exception) {
			throw new NodeExecutionException("Invalid HTTP node request URI", false, null, exception);
		}
		if (idempotencyKey != null) {
			request.header("Idempotency-Key", idempotencyKey);
		}
		HttpRequest.BodyPublisher publisher = method.equals("GET")
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(serializedBody);

		HttpResponse<String> response;
		try {
			response = httpClient.send(
					request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
		} catch (IOException exception) {
			// Connection failures and request timeouts are transient. The same
			// idempotency key makes replay safe if the remote side already acted.
			throw new NodeExecutionException("HTTP node transport failure: " + exception.getMessage(),
					true, null, exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new NodeExecutionException("HTTP node request was interrupted", true, null, exception);
		}

		int status = response.statusCode();
		if (status < 200 || status >= 300) {
			boolean retryable = status == 408 || status == 425 || status == 429
					|| (status >= 500 && status <= 599);
			throw new NodeExecutionException("HTTP request returned status " + status,
					retryable, status);
		}

		JsonNode responseBody;
		try {
			responseBody = objectMapper.readTree(response.body());
		} catch (Exception notJson) {
			responseBody = objectMapper.getNodeFactory().textNode(response.body());
		}
		return objectMapper.createObjectNode()
				.put("status", status)
				.set("body", responseBody);
	}
}
