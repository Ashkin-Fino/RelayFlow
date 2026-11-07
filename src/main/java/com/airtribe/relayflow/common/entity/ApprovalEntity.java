package com.airtribe.relayflow.common.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "approvals")
public class ApprovalEntity {
	@Id
	@Column(name = "id", length = 128, nullable = false)
	private String id;

	@Column(name = "run_id", length = 128, nullable = false)
	private String runId;

	@Column(name = "step_id", nullable = false, unique = true)
	private Long stepId;

	@Column(name = "node_id", length = 128, nullable = false)
	private String nodeId;

	@Column(name = "message", nullable = false, columnDefinition = "text")
	private String message;

	@Column(name = "status", length = 16, nullable = false)
	private String status;

	@Column(name = "decided_by", length = 128)
	private String decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "reason", columnDefinition = "text")
	private String reason;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ApprovalEntity() {
	}

	public ApprovalEntity(String id, String runId, Long stepId, String nodeId, String message) {
		this.id = id;
		this.runId = runId;
		this.stepId = stepId;
		this.nodeId = nodeId;
		this.message = message;
		this.status = "pending";
	}

	public String getId() {
		return id;
	}

	public String getRunId() {
		return runId;
	}

	public Long getStepId() {
		return stepId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public String getMessage() {
		return message;
	}

	public String getStatus() {
		return status;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void decide(String decision, String actor, String reason) {
		if (!"pending".equals(status)) {
			throw new IllegalStateException("Only a pending approval can be decided");
		}
		this.status = decision;
		this.decidedBy = actor;
		this.decidedAt = Instant.now();
		this.reason = reason;
	}

	public void cancel() {
		if ("pending".equals(status)) {
			this.status = "cancelled";
		}
	}
}
