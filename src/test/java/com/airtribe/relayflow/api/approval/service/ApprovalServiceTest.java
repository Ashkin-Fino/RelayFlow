package com.airtribe.relayflow.api.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.airtribe.relayflow.common.entity.ApprovalEntity;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.ApprovalConflictException;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;

import tools.jackson.databind.ObjectMapper;

class ApprovalServiceTest {
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void approvalRecordsDecisionAndRequeuesRun() {
		Fixture fixture = fixture();

		var response = fixture.service.approve("apr_1");

		assertEquals("approved", response.status());
		assertEquals("running", fixture.run.getStatus());
		assertEquals("notify", fixture.run.getCurrentNodeId());
		assertEquals(1, fixture.run.getStepsExecuted());
		assertEquals("succeeded", fixture.step.getStatus());
		assertEquals("approved", fixture.step.getOutput().path("decision").asString());
		assertEquals("demo-operator", fixture.step.getOutput().path("decided_by").asString());
		verify(fixture.queue).requeueCompletedRun("run_1");
	}

	@Test
	void rejectionCancelsRunAndDoesNotRequeue() {
		Fixture fixture = fixture();

		var response = fixture.service.reject("apr_1", "Not in budget");

		assertEquals("rejected", response.status());
		assertEquals("cancelled", fixture.run.getStatus());
		assertEquals("rejected", fixture.step.getOutput().path("decision").asString());
		assertEquals("Not in budget", fixture.step.getOutput().path("reason").asString());
	}

	@Test
	void alreadyDecidedApprovalIsRejected() {
		Fixture fixture = fixture();
		fixture.approval.decide("approved", "someone", null);

		assertThrows(ApprovalConflictException.class, () -> fixture.service.approve("apr_1"));
	}

	private Fixture fixture() {
		ApprovalRepository approvals = mock(ApprovalRepository.class);
		RunRepository runs = mock(RunRepository.class);
		StepRepository steps = mock(StepRepository.class);
		QueueJobRepository queue = mock(QueueJobRepository.class);
		ApprovalEntity approval = new ApprovalEntity("apr_1", "run_1", 1L, "gate", "Approve transfer?");
		StepEntity step = new StepEntity("run_1", "gate", "approval", 1,
				mapper.createObjectNode().put("message", "Approve transfer?"), null);
		step.waitForApproval();
		RunEntity run = new RunEntity("run_1", "wf_1", mapper.readTree("""
				{"nodes":[{"id":"gate","type":"approval","next":"notify"},
				          {"id":"notify","type":"notify","next":null}]}
				"""), "manual", mapper.createObjectNode(), "gate");
		run.start();
		run.waitForApproval();

		when(approvals.findById("apr_1")).thenReturn(Optional.of(approval));
		when(approvals.findByIdForUpdate("apr_1")).thenReturn(Optional.of(approval));
		when(runs.findByIdForUpdate("run_1")).thenReturn(Optional.of(run));
		when(steps.findById(1L)).thenReturn(Optional.of(step));
		when(steps.save(any(StepEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(approvals.save(any(ApprovalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(runs.save(any(RunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		return new Fixture(new ApprovalService(approvals, runs, steps, queue, mapper, "demo-operator"),
				approval, run, step, queue);
	}

	private record Fixture(ApprovalService service, ApprovalEntity approval,
			RunEntity run, StepEntity step, QueueJobRepository queue) {
	}
}
