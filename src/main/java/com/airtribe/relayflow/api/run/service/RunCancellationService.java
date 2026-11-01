package com.airtribe.relayflow.api.run.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.api.run.dto.RunCancellationResponse;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.exception.RunConflictException;
import com.airtribe.relayflow.common.exception.RunNotFoundException;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;

@Service
public class RunCancellationService {
	private final RunRepository runs;
	private final ApprovalRepository approvals;
	private final QueueJobRepository queue;

	public RunCancellationService(RunRepository runs, ApprovalRepository approvals, QueueJobRepository queue) {
		this.runs = runs;
		this.approvals = approvals;
		this.queue = queue;
	}

	@Transactional
	public RunCancellationResponse cancel(String runId) {
		RunEntity run = runs.findByIdForUpdate(runId).orElseThrow(() -> new RunNotFoundException(runId));
		switch (run.getStatus()) {
			case "queued" -> {
				run.cancel();
				queue.cancelQueuedRun(runId);
			}
			case "running" -> run.requestCancellation();
			case "waiting_approval" -> {
				run.cancel();
				approvals.findByRunIdAndStatus(runId, "pending").forEach(approval -> {
					approval.cancel();
					approvals.save(approval);
				});
				queue.cancelQueuedRun(runId);
			}
			case "succeeded", "failed", "cancelled" ->
					throw new RunConflictException("Terminal runs cannot be cancelled");
			default -> throw new RunConflictException("Run has an unsupported state: " + run.getStatus());
		}
		runs.save(run);
		return new RunCancellationResponse(runId, run.getStatus(), run.isCancelRequested());
	}
}
