package com.airtribe.relayflow.common.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "steps")
public class StepEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", length = 128, nullable = false)
    private String runId;

    @Column(name = "node_id", length = 128, nullable = false)
    private String nodeId;

    @Column(name = "node_type", length = 64, nullable = false)
    private String nodeType;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "status", length = 24, nullable = false)
    private String status;

    @Column(name = "attempt", nullable = false)
    private int attempt = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_input", columnDefinition = "jsonb", nullable = false)
    private JsonNode resolvedInput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private JsonNode output;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error", columnDefinition = "jsonb")
    private JsonNode error;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "tokens_prompt")
    private Integer tokensPrompt;

    @Column(name = "tokens_completion")
    private Integer tokensCompletion;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected StepEntity() {
    }

    public StepEntity(String runId, String nodeId, String nodeType, int sequence,
            JsonNode resolvedInput, String idempotencyKey) {
        this.runId = runId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.sequence = sequence;
        this.resolvedInput = resolvedInput;
        this.idempotencyKey = idempotencyKey;
        this.status = "running";
        this.attempt = 1;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getRunId() {
        return runId;
    }

    public JsonNode getOutput() {
        return output;
    }

    public JsonNode getResolvedInput() {
        return resolvedInput;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getSequence() {
        return sequence;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Integer getTokensPrompt() {
        return tokensPrompt;
    }

    public Integer getTokensCompletion() {
        return tokensCompletion;
    }

    /**
     * Called only when a previous attempt was durably marked waiting.
     * A row left running by a worker crash keeps its current attempt number.
     */
    public void beginRetry() {
        if (!"waiting".equals(status)) {
            throw new IllegalStateException("Only a waiting step can begin another retry attempt");
        }
        this.attempt++;
        this.status = "running";
        this.error = null;
        this.finishedAt = null;
        this.durationMs = null;
        this.output = null;
    }

    public void waitForRetry(JsonNode error, long durationMs) {
        this.error = error;
        this.status = "waiting";
        this.durationMs = durationMs;
        this.finishedAt = Instant.now();
    }

    public void succeed(JsonNode output, long durationMs) {
        succeed(output, durationMs, null, null);
    }

    public void succeed(JsonNode output, long durationMs, Integer promptTokens, Integer completionTokens) {
        this.output = output;
        this.error = null;
        this.status = "succeeded";
        this.durationMs = durationMs;
        this.finishedAt = Instant.now();
        addTokenUsage(promptTokens, completionTokens);
    }

    public void fail(JsonNode error, long durationMs) {
        fail(error, durationMs, null, null);
    }

    public void fail(JsonNode error, long durationMs, Integer promptTokens, Integer completionTokens) {
        this.error = error;
        this.status = "failed";
        this.durationMs = durationMs;
        this.finishedAt = Instant.now();
        addTokenUsage(promptTokens, completionTokens);
    }

    public void addTokenUsage(Integer promptTokens, Integer completionTokens) {
        if ((promptTokens != null && promptTokens < 0)
                || (completionTokens != null && completionTokens < 0)) {
            throw new IllegalArgumentException("AI token usage cannot be negative");
        }
        if (promptTokens != null) {
            this.tokensPrompt = Math.addExact(this.tokensPrompt == null ? 0 : this.tokensPrompt, promptTokens);
        }
        if (completionTokens != null) {
            this.tokensCompletion = Math.addExact(
                    this.tokensCompletion == null ? 0 : this.tokensCompletion, completionTokens);
        }
    }
}
