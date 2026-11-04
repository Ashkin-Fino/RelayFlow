package com.airtribe.relayflow.api.run.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.api.run.dto.RunSummary;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.exception.WorkflowNotFoundException;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.WorkflowRepository;

@Service
public class RunQueryService {
	private static final List<String> RUN_STATUSES = List.of(
			"queued", "running", "waiting_approval", "succeeded", "failed", "cancelled");

	private final RunRepository runs;
	private final WorkflowRepository workflows;

	public RunQueryService(RunRepository runs, WorkflowRepository workflows) {
		this.runs = runs;
		this.workflows = workflows;
	}

	@Transactional(readOnly = true)
	public List<RunSummary> list(String workflowId, String status) {
		validateWorkflowId(workflowId);
		validateStatus(status);

		if (workflowId != null && !workflows.existsById(workflowId)) {
			throw new WorkflowNotFoundException(workflowId);
		}

		if (workflowId != null && status != null) {
			return runs.findByWorkflowIdAndStatusOrderByCreatedAtDesc(workflowId, status).stream()
					.map(this::toSummary)
					.toList();
		}
		if (workflowId != null) {
			return runs.findByWorkflowIdOrderByCreatedAtDesc(workflowId).stream()
					.map(this::toSummary)
					.toList();
		}
		if (status != null) {
			return runs.findByStatusOrderByCreatedAtDesc(status).stream()
					.map(this::toSummary)
					.toList();
		}
		return runs.findAllByOrderByCreatedAtDesc().stream()
				.map(this::toSummary)
				.toList();
	}

	private void validateWorkflowId(String workflowId) {
		if (workflowId != null && workflowId.isBlank()) {
			throw new IllegalArgumentException("workflowId must not be blank");
		}
	}

	private void validateStatus(String status) {
		if (status != null && status.isBlank()) {
			throw new IllegalArgumentException("status must not be blank");
		}
		if (status != null && !RUN_STATUSES.contains(status)) {
			throw new IllegalArgumentException("status must be one of: " + String.join(", ", RUN_STATUSES));
		}
	}

	private RunSummary toSummary(RunEntity run) {
		return new RunSummary(run.getId(), run.getWorkflowId(), run.getStatus(),
				run.getTriggerType(), run.getCurrentNodeId(), run.getStepsExecuted(),
				run.getAiTokensUsed(), run.isCancelRequested(), run.getCreatedAt(),
				run.getStartedAt(), run.getFinishedAt());
	}
}
