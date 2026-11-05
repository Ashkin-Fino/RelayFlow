package com.airtribe.relayflow.common.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowScheduleRepository {
	private final JdbcTemplate jdbcTemplate;

	public WorkflowScheduleRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void upsert(String workflowId, String cron, Instant nextFireAt) {
		jdbcTemplate.update("""
				INSERT INTO workflow_schedules (workflow_id, cron, next_fire_at, enabled)
				VALUES (?, ?, ?, true)
				ON CONFLICT (workflow_id) DO UPDATE
				SET cron = EXCLUDED.cron, next_fire_at = EXCLUDED.next_fire_at,
				    enabled = true, updated_at = now()
				""", workflowId, cron, Timestamp.from(nextFireAt));
	}

	public void delete(String workflowId) {
		jdbcTemplate.update("DELETE FROM workflow_schedules WHERE workflow_id = ?", workflowId);
	}

	public List<DueWorkflowSchedule> lockDueSchedules(int limit) {
		return jdbcTemplate.query("""
				SELECT workflow_id, cron
				FROM workflow_schedules
				WHERE enabled = true AND next_fire_at <= now()
				ORDER BY next_fire_at, workflow_id
				LIMIT ?
				FOR UPDATE SKIP LOCKED
				""", (resultSet, rowNumber) -> new DueWorkflowSchedule(
						resultSet.getString("workflow_id"), resultSet.getString("cron")), limit);
	}

	public void advance(String workflowId, Instant nextFireAt) {
		jdbcTemplate.update("""
				UPDATE workflow_schedules
				SET next_fire_at = ?, updated_at = now()
				WHERE workflow_id = ? AND enabled = true
				""", Timestamp.from(nextFireAt), workflowId);
	}

	public record DueWorkflowSchedule(String workflowId, String cron) {
	}
}
