package com.airtribe.relayflow.api.approval.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.api.approval.dto.ApprovalDecisionResponse;
import com.airtribe.relayflow.api.approval.dto.ApprovalSummary;
import com.airtribe.relayflow.common.entity.ApprovalEntity;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.ApprovalConflictException;
import com.airtribe.relayflow.common.exception.ApprovalNotFoundException;
import com.airtribe.relayflow.common.exception.RunNotFoundException;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ApprovalService {
	private final ApprovalRepository approvals;
	private final RunRepository runs;
	private final StepRepository steps;
	private final QueueJobRepository queue;
	private final ObjectMapper objectMapper;
	private final String decider;

	public ApprovalService(ApprovalRepository approvals, RunRepository runs, StepRepository steps,
			QueueJobRepository queue, ObjectMapper objectMapper,
			@Value("${relay.approvals.decided-by:demo-operator}") String decider) {
		this.approvals = approvals;
		this.runs = runs;
		this.steps = steps;
		this.queue = queue;
		this.objectMapper = objectMapper;
		if (decider == null || decider.isBlank()) {
			throw new IllegalArgumentException("Approval decider must not be blank");
		}
		this.decider = decider;
	}

	@Transactional(readOnly = true)
	public List<ApprovalSummary> list(String status) {
		/**
		 * Lists all approvals with the specified status, ordered by creation time.
		 * 
		 * @param status The status of the approvals to list 
		 *        (pending, approved, rejected, cancelled).
		 * @return A list of ApprovalSummary objects representing the approvals 
		 *         with the specified status.
		 */
		if (!List.of("pending", "approved", "rejected", "cancelled").contains(status)) {
			throw new IllegalArgumentException("Unsupported approval status: " + status);
		}
		return approvals.findByStatusOrderByCreatedAtAsc(status).stream()
			.map(approval -> new ApprovalSummary(approval.getId(), approval.getRunId(),
				approval.getNodeId(), approval.getMessage(), approval.getStatus(), 
				approval.getCreatedAt()))
			.toList();
	}

	@Transactional
	public ApprovalDecisionResponse approve(String approvalId) {
		// Approves the specified approval request and updates the associated 
		// run and step entities accordingly.
		return decide(approvalId, "approved", null);
	}

	@Transactional
	public ApprovalDecisionResponse reject(String approvalId, String reason) {
		// Rejects the specified approval request with an optional reason and
		// updates the associated run and step entities accordingly.
		return decide(approvalId, "rejected", reason);
	}

	private ApprovalDecisionResponse decide(String approvalId, String decision, String reason) {
		/**
		 * Decides on an approval request by either approving or rejecting it,
		 * and updates the associated run and step entities accordingly.
		 * 
		 * @param approvalId The ID of the approval request to decide on.
		 * @param decision The decision to make ("approved" or "rejected").
		 * @param reason An optional reason for the decision (only applicable for rejection).
		 * @return An ApprovalDecisionResponse containing the updated approval 
		 *         and run information.
		 * @throws ApprovalNotFoundException if the approval request does not exist.
		 * @throws RunNotFoundException if the associated run does not exist.
		 */
		ApprovalEntity initial = approvals.findById(approvalId)
			.orElseThrow(() -> new ApprovalNotFoundException(approvalId));
		RunEntity run = runs.findByIdForUpdate(initial.getRunId())
			.orElseThrow(() -> new RunNotFoundException(initial.getRunId()));
		ApprovalEntity approval = approvals.findByIdForUpdate(approvalId)
			.orElseThrow(() -> new ApprovalNotFoundException(approvalId));

		if (!"pending".equals(approval.getStatus())) {
			throw new ApprovalConflictException("Approval has already been decided");
		}

		if (!"waiting_approval".equals(run.getStatus())) {
			throw new ApprovalConflictException("Run is not waiting for approval");
		}

		StepEntity step = steps.findById(approval.getStepId())
				.orElseThrow(() -> new ApprovalConflictException("Approval step no longer exists"));

		if (!"approval".equals(step.getNodeType()) || !"waiting".equals(step.getStatus())) {
			throw new ApprovalConflictException("Approval step is not waiting for a decision");
		}

		String nextNodeId = nextNodeId(run, approval.getNodeId());

		approval.decide(decision, decider, reason);
		ObjectNode output = objectMapper.createObjectNode()
			.put("decision", decision)
			.put("decided_by", decider);

		if (reason != null && !reason.isBlank()) {
			output.put("reason", reason);
		}

		step.completeApproval(output);
		steps.save(step);
		approvals.save(approval);

		if ("approved".equals(decision)) {
			run.completeApproval(nextNodeId);
			runs.save(run);
			if ("running".equals(run.getStatus())) {
				queue.requeueCompletedRun(run.getId());
			}
		} else {
			run.cancel();
			runs.save(run);
		}

		return new ApprovalDecisionResponse(approval.getId(), run.getId(), approval.getStatus());
	}

	private String nextNodeId(RunEntity run, String nodeId) {
		// Finds the next node ID in the run's definition snapshot after the specified node ID.
		for (var node : run.getDefinitionSnapshot().path("nodes")) {
			if (nodeId.equals(node.path("id").asString())) {
				return node.path("next").isNull() || 
					node.path("next").isMissingNode() 
						? null : node.path("next").asString();
			}
		}
		throw new ApprovalConflictException("Approval node is missing from the run snapshot");
	}
}
