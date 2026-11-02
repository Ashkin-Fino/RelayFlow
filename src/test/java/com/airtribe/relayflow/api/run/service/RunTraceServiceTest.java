package com.airtribe.relayflow.api.run.service;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.airtribe.relayflow.common.entity.ApprovalEntity;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.RunNotFoundException;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;

import tools.jackson.databind.ObjectMapper;

class RunTraceServiceTest {
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void returnsRunAndOrderedStepDetailsIncludingApprovalAndAiUsage() throws ReflectiveOperationException {
		RunRepository runs = mock(RunRepository.class);
		StepRepository steps = mock(StepRepository.class);
		ApprovalRepository approvals = mock(ApprovalRepository.class);
		RunEntity run = new RunEntity("run_1", "wf_1", mapper.createObjectNode(),
				"webhook", mapper.createObjectNode(), "gate");
		run.start();
		run.waitForApproval();
		run.addAiTokensUsed(16);

		StepEntity approvalStep = new StepEntity("run_1", "gate", "approval", 1,
				mapper.readTree("{\"message\":\"Approve order ord_1?\"}"), null);
		setId(approvalStep, 11L);
		approvalStep.waitForApproval();
		ApprovalEntity approval = new ApprovalEntity("apr_1", "run_1", 11L,
				"gate", "Approve order ord_1?");
		StepEntity aiStep = new StepEntity("run_1", "classify", "ai", 2,
				mapper.readTree("{\"prompt\":\"classify\"}"), null);
		aiStep.succeed(mapper.readTree("{\"category\":\"delivery\"}"), 17, 12, 4);

		when(runs.findById("run_1")).thenReturn(Optional.of(run));
		when(steps.findByRunIdOrderBySequenceAsc("run_1")).thenReturn(List.of(approvalStep, aiStep));
		when(approvals.findByRunIdOrderByCreatedAtAsc("run_1")).thenReturn(List.of(approval));
		RunTraceService service = new RunTraceService(runs, steps, approvals);

		var trace = service.getTrace("run_1");

		assertEquals("run_1", trace.runId());
		assertEquals("wf_1", trace.workflowId());
		assertEquals("waiting_approval", trace.status());
		assertEquals(2, trace.steps().size());
		assertEquals("gate", trace.steps().get(0).nodeId());
		assertEquals("waiting", trace.steps().get(0).status());
		assertEquals("apr_1", trace.steps().get(0).approvalId());
		assertEquals("pending", trace.steps().get(0).approvalStatus());
		assertEquals("classify", trace.steps().get(1).nodeId());
		assertEquals("delivery", trace.steps().get(1).output().path("category").asString());
		assertEquals(12, trace.steps().get(1).tokensPrompt());
		assertEquals(4, trace.steps().get(1).tokensCompletion());
		assertEquals(16, trace.aiTokensUsed());
		var response = mapper.valueToTree(trace);
		assertEquals("run_1", response.path("run_id").asString());
		assertEquals("waiting_approval", response.path("status").asString());
		assertEquals("gate", response.path("steps").get(0).path("node_id").asString());
		assertEquals("apr_1", response.path("steps").get(0).path("approval_id").asString());
		assertEquals(12, response.path("steps").get(1).path("tokens_prompt").asInt());
	}

	@Test
	void missingRunReturnsRunNotFound() {
		RunRepository runs = mock(RunRepository.class);
		when(runs.findById("missing")).thenReturn(Optional.empty());
		RunTraceService service = new RunTraceService(runs,
				mock(StepRepository.class), mock(ApprovalRepository.class));

		assertThrows(RunNotFoundException.class, () -> service.getTrace("missing"));
	}

	private void setId(StepEntity step, Long id) throws ReflectiveOperationException {
		Field field = StepEntity.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(step, id);
	}
}
