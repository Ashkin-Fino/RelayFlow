package com.airtribe.relayflow.run.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class QueueJobRepository {
	private static final String CLAIM_NEXT = """
			WITH candidate AS (
				SELECT id
				FROM queue_jobs
				WHERE status = 'queued' AND available_at <= now()
				ORDER BY available_at, id
				LIMIT 1
				FOR UPDATE SKIP LOCKED
			)
			UPDATE queue_jobs AS job
			SET status = 'leased',
			    lease_owner = ?,
			    lease_expires_at = now() + (? * INTERVAL '1 second'),
			    attempts = attempts + 1,
			    updated_at = now()
			FROM candidate
			WHERE job.id = candidate.id
			RETURNING job.id, job.run_id
			""";

	private final JdbcTemplate jdbcTemplate;

	public QueueJobRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void enqueue(String runId) {
		jdbcTemplate.update("INSERT INTO queue_jobs (run_id, status) VALUES (?, 'queued')", runId);
	}

	@Transactional
	public Optional<ClaimedQueueJob> claimNext(String workerId, int leaseSeconds) {
		List<ClaimedQueueJob> claims = jdbcTemplate.query(CLAIM_NEXT,
				(resultSet, rowNumber) -> new ClaimedQueueJob(
						resultSet.getLong("id"), resultSet.getString("run_id")),
				workerId, leaseSeconds);
		return claims.stream().findFirst();
	}

	@Transactional
	public void complete(long jobId, String error) {
		jdbcTemplate.update("""
				UPDATE queue_jobs
				SET status = 'completed', lease_owner = NULL, lease_expires_at = NULL,
				    last_error = ?, updated_at = now()
				WHERE id = ?
				""", error, jobId);
	}

	@Transactional
	public void reschedule(long jobId, long delaySeconds) {
		jdbcTemplate.update("""
				UPDATE queue_jobs
				SET status = 'queued', available_at = now() + (? * INTERVAL '1 second'),
				    lease_owner = NULL, lease_expires_at = NULL, updated_at = now()
				WHERE id = ?
				""", delaySeconds, jobId);
	}
}