package com.airtribe.relayflow.api.run.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.airtribe.relayflow.common.entity.ApprovalEntity;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.exception.RunConflictException;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;

import tools.jackson.databind.ObjectMapper;

class RunCancellationServiceTest {
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void queuedCancellationStopsQueuedJobImmediately() {
		Fixture fixture = fixture("queued");

		var result = fixture.service.cancel("run_1");

		assertEquals("cancelled", result.status());
		assertEquals("cancelled", fixture.run.getStatus());
		verify(fixture.queue).cancelQueuedRun("run_1");
	}

	@Test
	void runningCancellationRequestsCooperativeStop() {
		Fixture fixture = fixture("running");

		var result = fixture.service.cancel("run_1");

		assertEquals("running", result.status());
		assertEquals(true, result.cancelRequested());
		assertEquals(true, fixture.run.isCancelRequested());
	}

	@Test
	void waitingApprovalCancellationClosesPendingApproval() {
		Fixture fixture = fixture("waiting_approval");
		ApprovalEntity approval = new ApprovalEntity("apr_1", "run_1", 1L, "gate", "Approve?");
		when(fixture.approvals.findByRunIdAndStatus("run_1", "pending")).thenReturn(List.of(approval));

		var result = fixture.service.cancel("run_1");

		assertEquals("cancelled", result.status());
		assertEquals("cancelled", approval.getStatus());
		verify(fixture.approvals).save(approval);
	}

	@Test
	void terminalRunCannotBeCancelled() {
		Fixture fixture = fixture("succeeded");

		assertThrows(RunConflictException.class, () -> fixture.service.cancel("run_1"));
	}

	private Fixture fixture(String status) {
		RunRepository runs = mock(RunRepository.class);
		ApprovalRepository approvals = mock(ApprovalRepository.class);
		QueueJobRepository queue = mock(QueueJobRepository.class);
		RunEntity run = new RunEntity("run_1", "wf_1", mapper.createObjectNode(),
				"manual", mapper.createObjectNode(), "entry");
		switch (status) {
			case "running" -> run.start();
			case "waiting_approval" -> {
				run.start();
				run.waitForApproval();
			}
			case "succeeded" -> {
				run.start();
				run.succeed();
			}
			default -> {
			}
		}
		when(runs.findByIdForUpdate("run_1")).thenReturn(Optional.of(run));
		when(runs.save(any(RunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		return new Fixture(new RunCancellationService(runs, approvals, queue), run, approvals, queue);
	}

	private record Fixture(RunCancellationService service, RunEntity run,
			ApprovalRepository approvals, QueueJobRepository queue) {
	}
}
