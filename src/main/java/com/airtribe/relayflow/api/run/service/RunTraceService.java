package com.airtribe.relayflow.api.run.service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.api.run.dto.RunTraceResponse;
import com.airtribe.relayflow.api.run.dto.StepTraceResponse;
import com.airtribe.relayflow.common.entity.ApprovalEntity;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.RunNotFoundException;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;

@Service
public class RunTraceService {
	private final RunRepository runs;
	private final StepRepository steps;
	private final ApprovalRepository approvals;

	public RunTraceService(RunRepository runs, StepRepository steps, ApprovalRepository approvals) {
		this.runs = runs;
		this.steps = steps;
		this.approvals = approvals;
	}

	@Transactional(readOnly = true)
	public RunTraceResponse getTrace(String runId) {
		RunEntity run = runs.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
		Map<Long, ApprovalEntity> approvalsByStepId = approvals
				.findByRunIdOrderByCreatedAtAsc(runId).stream()
				.collect(Collectors.toMap(ApprovalEntity::getStepId, Function.identity()));
		var stepTraces = steps.findByRunIdOrderBySequenceAsc(runId).stream()
				.map(step -> toStepTrace(step, approvalsByStepId.get(step.getId())))
				.toList();

		return new RunTraceResponse(run.getId(), run.getWorkflowId(), run.getStatus(),
				run.getTriggerType(), run.getCurrentNodeId(), run.getStepsExecuted(),
				run.getAiTokensUsed(), run.isCancelRequested(), run.getError(),
				run.getStartedAt(), run.getFinishedAt(), stepTraces);
	}

	private StepTraceResponse toStepTrace(StepEntity step, ApprovalEntity approval) {
		return new StepTraceResponse(step.getNodeId(), step.getNodeType(), step.getStatus(),
				step.getSequence(), step.getAttempt(), step.getResolvedInput(), step.getOutput(),
				step.getError(), step.getStartedAt(), step.getFinishedAt(), step.getDurationMs(),
				step.getTokensPrompt(), step.getTokensCompletion(), step.getIdempotencyKey(),
				approval == null ? null : approval.getId(),
				approval == null ? null : approval.getStatus(),
				approval == null ? null : approval.getDecidedBy(),
				approval == null ? null : approval.getDecidedAt(),
				approval == null ? null : approval.getReason());
	}
}
