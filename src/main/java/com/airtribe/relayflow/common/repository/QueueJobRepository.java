package com.airtribe.relayflow.common.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.common.exception.StaleQueueLeaseException;
import com.airtribe.relayflow.worker.dto.ClaimedQueueJob;

@Repository
public class QueueJobRepository {
	private static final String CLAIM_NEXT = """
		WITH candidate AS (
			SELECT id
			FROM queue_jobs
			WHERE (status = 'queued' AND available_at <= now())
				OR (status = 'leased' AND lease_expires_at <= now())
			ORDER BY CASE WHEN status = 'queued' THEN available_at ELSE lease_expires_at END, id
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
		RETURNING job.id, job.run_id, job.lease_owner
	""";

	private final JdbcTemplate jdbcTemplate;

	public QueueJobRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void enqueue(String runId) {
		// Enqueues a new job for the specified run ID by inserting a record 
		// into the queue_jobs table with status 'queued'.
		jdbcTemplate.update("INSERT INTO queue_jobs (run_id, status) VALUES (?, 'queued')", runId);
	}

	@Transactional
	public void cancelQueuedRun(String runId) {
		jdbcTemplate.update("""
				UPDATE queue_jobs
				SET status = 'cancelled', lease_owner = NULL, lease_expires_at = NULL,
				    last_error = 'Run cancelled', updated_at = now()
				WHERE run_id = ? AND status IN ('queued', 'leased')
				""", runId);
	}

	@Transactional
	public void requeueCompletedRun(String runId) {
		int updated = jdbcTemplate.update("""
				UPDATE queue_jobs
				SET status = 'queued', available_at = now(), lease_owner = NULL,
				    lease_expires_at = NULL, last_error = NULL, updated_at = now()
				WHERE run_id = ? AND status = 'completed'
				""", runId);
		if (updated != 1) {
			throw new IllegalStateException("Unable to resume run queue job: " + runId);
		}
	}

	@Transactional
	public Optional<ClaimedQueueJob> claimNext(String workerId, int leaseSeconds) {
		/**
		 * Claims the next available queue job for processing by a worker.
		 * 
		 * @param workerId The ID of the worker claiming the job.
		 * @param leaseSeconds The number of seconds for which the job lease is valid.
		 * @return An Optional containing the claimed queue job, or empty if no job is available
		 */
		if (workerId == null || workerId.isBlank()) {
			throw new IllegalArgumentException("workerId must not be blank");
		}
		if (leaseSeconds < 1) {
			throw new IllegalArgumentException("leaseSeconds must be positive");
		}
		// Each claim gets a unique fencing token, even when the same worker process
		// reclaims a job after its previous lease expires.
		String leaseToken = workerId + ":" + UUID.randomUUID();
		List<ClaimedQueueJob> claims = jdbcTemplate.query(CLAIM_NEXT,
			(resultSet, rowNumber) -> new ClaimedQueueJob(
				resultSet.getLong("id"),
				resultSet.getString("run_id"),
				resultSet.getString("lease_owner")),
			leaseToken, leaseSeconds);
		return claims.stream().findFirst();
	}

	@Transactional
	public void complete(long jobId, String leaseToken, String error) {
		/**
		 * Completes a queue job by marking it as completed and clearing its lease information.
		 * 
		 * @param jobId The ID of the queue job to complete.
		 * @param leaseToken The lease token for the queue job.
		 * @param error The error message, if any.
		 */
		int updated = jdbcTemplate.update("""
				UPDATE queue_jobs
				SET status = 'completed', lease_owner = NULL, lease_expires_at = NULL,
				    last_error = ?, updated_at = now()
				WHERE id = ? AND status = 'leased' AND lease_owner = ?
				  AND lease_expires_at > now()
			""", error, jobId, leaseToken);
		if (updated != 1) {
			throw new StaleQueueLeaseException(jobId);
		}
	}

	@Transactional
	public void reschedule(long jobId, String leaseToken, long delaySeconds) {
		/**
		 * Reschedules a queue job by setting its status back to 'queued' and 
		 * updating its availability time.
		 * 
		 * @param jobId The ID of the queue job to reschedule.
		 * @param leaseToken The lease token for the queue job.
		 * @param delaySeconds The number of seconds to delay the job before it becomes available again.
		 * @throws IllegalArgumentException if delaySeconds is negative.
		 */
		if (delaySeconds < 0) {
			throw new IllegalArgumentException("delaySeconds must not be negative");
		}
		int updated = jdbcTemplate.update("""
				UPDATE queue_jobs
				SET status = 'queued', available_at = now() + (? * INTERVAL '1 second'),
				    lease_owner = NULL, lease_expires_at = NULL, updated_at = now()
				WHERE id = ? AND status = 'leased' AND lease_owner = ?
				  AND lease_expires_at > now()
			""", delaySeconds, jobId, leaseToken);
		if (updated != 1) {
			throw new StaleQueueLeaseException(jobId);
		}
	}
}