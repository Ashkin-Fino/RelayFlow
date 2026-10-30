package com.airtribe.relayflow.workflow.entity;

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
@Table(name = "workflows")
public class WorkflowEntity {
	@Id
	@Column(name = "id", length = 128, nullable = false)
	private String id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "status", length = 16, nullable = false)
	private String status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "definition", columnDefinition = "jsonb", nullable = false)
	private JsonNode definition;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WorkflowEntity() {
	}

	public WorkflowEntity(String id, String name, String description, String status, JsonNode definition) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.status = status;
		this.definition = definition;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getStatus() {
		return status;
	}

	public JsonNode getDefinition() {
		return definition;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void publish() {
		this.status = "published";
	}
}