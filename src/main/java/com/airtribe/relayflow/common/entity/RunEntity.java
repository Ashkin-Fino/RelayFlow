package com.airtribe.relayflow.common.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "runs")
public class RunEntity {
	@Id
	@Column(name = "id", length = 128, nullable = false)
	private String id;

	@Column(name = "workflow_id", length = 128, nullable = false)
	private String workflowId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "definition_snapshot", columnDefinition = "jsonb", nullable = false)
	private JsonNode definitionSnapshot;

	@Column(name = "status", length = 24, nullable = false)
	private String status;

	@Column(name = "trigger_type", length = 16, nullable = false)
	private String triggerType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "trigger_input", columnDefinition = "jsonb", nullable = false)
	private JsonNode triggerInput;

	@Column(name = "current_node_id", length = 128)
	private String currentNodeId;

	@Column(name = "steps_executed", nullable = false)
	private int stepsExecuted;

	@Column(name = "ai_tokens_used", nullable = false)
	private long aiTokensUsed;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "error", columnDefinition = "jsonb")
	private JsonNode error;

	@Column(name = "cancel_requested", nullable = false)
	private boolean cancelRequested;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RunEntity() {
	}

	public RunEntity(String id, String workflowId, JsonNode definitionSnapshot,
			String triggerType, JsonNode triggerInput, String currentNodeId) {
		this.id = id;
		this.workflowId = workflowId;
		this.definitionSnapshot = definitionSnapshot;
		this.status = "queued";
		this.triggerType = triggerType;
		this.triggerInput = triggerInput;
		this.currentNodeId = currentNodeId;
		this.stepsExecuted = 0;
		this.aiTokensUsed = 0;
		this.cancelRequested = false;
	}

	public String getId() { 
        return id; 
    }

	public String getWorkflowId() { 
        return workflowId;
    }

	public JsonNode getDefinitionSnapshot() { 
        return definitionSnapshot; 
    }

	public String getStatus() { 
        return status; 
    }

	public String getTriggerType() { 
        return triggerType; 
    }

	public JsonNode getTriggerInput() { 
        return triggerInput; 
    }

	public String getCurrentNodeId() { 
        return currentNodeId; 
    }

	public int getStepsExecuted() { 
        return stepsExecuted; 
    }

	public long getAiTokensUsed() {
		return aiTokensUsed;
	}

	public boolean isCancelRequested() { 
        return cancelRequested; 
    }

	public Instant getStartedAt() { 
        return startedAt; 
    }

	public Instant getFinishedAt() { 
        return finishedAt; 
    }

	public Instant getCreatedAt() { 
        return createdAt; 
    }

	public void start() {
		if (!"queued".equals(this.status)) {
			return;
		}
		this.status = "running";
		if (this.startedAt == null) {
			this.startedAt = Instant.now();
		}
	}

	public void advance(String nextNodeId) {
		this.currentNodeId = nextNodeId;
		this.stepsExecuted++;
	}

	public void waitForApproval() {
		if (!"running".equals(status)) {
			throw new IllegalStateException("Only a running run can wait for approval");
		}
		this.status = "waiting_approval";
	}

	public void completeApproval(String nextNodeId) {
		if (!"waiting_approval".equals(status)) {
			throw new IllegalStateException("Run is not waiting for approval");
		}
		this.status = "running";
		advance(nextNodeId);
		if (nextNodeId == null) {
			succeed();
		}
	}

	public void requestCancellation() {
		if ("running".equals(status)) {
			this.cancelRequested = true;
		}
	}

	public void cancel() {
		if (!"queued".equals(status) && !"running".equals(status) && !"waiting_approval".equals(status)) {
			throw new IllegalStateException("Only a non-terminal run can be cancelled");
		}
		this.cancelRequested = true;
		this.status = "cancelled";
		this.currentNodeId = null;
		this.finishedAt = Instant.now();
	}

	public void addAiTokensUsed(long tokens) {
		if (tokens < 0) {
			throw new IllegalArgumentException("AI token usage cannot be negative");
		}
		this.aiTokensUsed = Math.addExact(this.aiTokensUsed, tokens);
	}

	public void succeed() {
		this.status = "succeeded";
		this.currentNodeId = null;
		this.finishedAt = Instant.now();
	}

	public void fail(JsonNode error) {
		this.status = "failed";
		this.error = error;
		this.finishedAt = Instant.now();
	}
}