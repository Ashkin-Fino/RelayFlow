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
	private final RunRepository runs;
	private final WorkflowRepository workflows;

	public RunQueryService(RunRepository runs, WorkflowRepository workflows) {
		this.runs = runs;
		this.workflows = workflows;
	}

	@Transactional(readOnly = true)
	public List<RunSummary> listByWorkflow(String workflowId) {
		if (!workflows.existsById(workflowId)) {
			throw new WorkflowNotFoundException(workflowId);
		}
		return runs.findByWorkflowIdOrderByCreatedAtDesc(workflowId).stream()
				.map(this::toSummary)
				.toList();
	}

	private RunSummary toSummary(RunEntity run) {
		return new RunSummary(run.getId(), run.getWorkflowId(), run.getStatus(),
				run.getTriggerType(), run.getCurrentNodeId(), run.getStepsExecuted(),
				run.getAiTokensUsed(), run.isCancelRequested(), run.getCreatedAt(),
				run.getStartedAt(), run.getFinishedAt());
	}
}
