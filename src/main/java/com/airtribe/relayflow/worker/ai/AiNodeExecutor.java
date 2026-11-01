package com.airtribe.relayflow.worker.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import com.airtribe.relayflow.worker.dto.NodeExecutionResult;
import com.airtribe.relayflow.worker.exception.NodeExecutionException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AiNodeExecutor {
    private final AiProviderClient provider;
    private final AiOutputSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    public AiNodeExecutor(AiProviderClient provider, AiOutputSchemaValidator schemaValidator,
            ObjectMapper objectMapper) {
        this.provider = provider;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
    }

    public NodeExecutionResult execute(JsonNode node, JsonNode params) {
        String prompt = params.path("prompt").asString();
        JsonNode outputSchema = params.path("output_schema");
        if (prompt.isBlank() || outputSchema.isMissingNode() || !outputSchema.isObject()) {
            throw new NodeExecutionException("AI node requires a prompt and object output_schema", false);
        }

        schemaValidator.validate(outputSchema, objectMapper.createObjectNode());
        AiProviderClient.Completion first = provider.complete(prompt, outputSchema);
        JsonNode output;
        List<String> errors;
        try {
            output = parseOutput(first.content());
            errors = schemaValidator.validate(outputSchema, output);
        } catch (OutputFormatException exception) {
            output = null;
            errors = List.of(exception.getMessage());
        }
        if (errors.isEmpty()) {
            return success(node, output, first.promptTokens(), first.completionTokens());
        }

        String repairPrompt = prompt + "\n\nYour previous response did not satisfy output_schema. "
                + "Validation errors:\n- " + String.join("\n- ", errors)
                + "\nReturn a corrected JSON object only.";
        AiProviderClient.Completion repaired;
        try {
            repaired = provider.complete(repairPrompt, outputSchema);
        } catch (NodeExecutionException exception) {
            throw withTokenUsage(exception, first.promptTokens(), first.completionTokens());
        }

        try {
            output = parseOutput(repaired.content());
            errors = schemaValidator.validate(outputSchema, output);
        } catch (OutputFormatException exception) {
            output = null;
            errors = List.of(exception.getMessage());
        }
        int promptTokens = Math.addExact(first.promptTokens(), repaired.promptTokens());
        int completionTokens = Math.addExact(first.completionTokens(), repaired.completionTokens());
        if (!errors.isEmpty()) {
            throw new NodeExecutionException(
                    "AI output remained invalid after one repair attempt: " + String.join("; ", errors),
                    false, null, null, promptTokens, completionTokens);
        }
        return success(node, output, promptTokens, completionTokens);
    }

    private JsonNode parseOutput(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            if (parsed == null || parsed.isNull()) {
                throw new OutputFormatException("model response was empty or JSON null");
            }
            return parsed;
        } catch (JacksonException exception) {
            throw new OutputFormatException("model response was not valid JSON");
        }
    }

    private NodeExecutionResult success(JsonNode node, JsonNode output, int promptTokens, int completionTokens) {
        String next = node.path("next").isNull() ? null : node.path("next").asString();
        return NodeExecutionResult.next(output, next, promptTokens, completionTokens);
    }

    private NodeExecutionException withTokenUsage(NodeExecutionException cause, int promptTokens,
            int completionTokens) {
        Integer promptTotal = cause.getTokensPrompt() == null
                ? promptTokens : Math.addExact(promptTokens, cause.getTokensPrompt());
        Integer completionTotal = cause.getTokensCompletion() == null
                ? completionTokens : Math.addExact(completionTokens, cause.getTokensCompletion());
        return new NodeExecutionException(cause.getMessage(), cause.isRetryable(), cause.getHttpStatus(),
                cause, promptTotal, completionTotal);
    }

    private static final class OutputFormatException extends RuntimeException {
        private OutputFormatException(String message) {
            super(message);
        }
    }
}
