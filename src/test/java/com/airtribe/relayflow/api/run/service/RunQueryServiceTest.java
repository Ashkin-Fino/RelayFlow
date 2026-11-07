package com.airtribe.relayflow.api.run.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.exception.WorkflowNotFoundException;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.WorkflowRepository;

import tools.jackson.databind.ObjectMapper;

class RunQueryServiceTest {
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void listsRunsForWorkflowInRepositoryOrder() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);
		RunEntity recent = new RunEntity("run_recent", "wf_1", mapper.createObjectNode(),
				"manual", mapper.createObjectNode(), "entry");
		RunEntity older = new RunEntity("run_older", "wf_1", mapper.createObjectNode(),
				"webhook", mapper.createObjectNode(), "entry");
		older.start();
		older.advance(null);
		older.succeed();
		when(workflows.existsById("wf_1")).thenReturn(true);
		when(runs.findByWorkflowIdOrderByCreatedAtDesc("wf_1")).thenReturn(List.of(recent, older));

		var result = new RunQueryService(runs, workflows).list("wf_1", null);

		assertEquals(2, result.size());
		assertEquals("run_recent", result.get(0).runId());
		assertEquals("run_older", result.get(1).runId());
		assertEquals("succeeded", result.get(1).status());
		verify(runs).findByWorkflowIdOrderByCreatedAtDesc("wf_1");
	}

	@Test
	void listsAllRunsInRepositoryOrderWhenWorkflowFilterIsOmitted() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);
		RunEntity recent = new RunEntity("run_recent", "wf_1", mapper.createObjectNode(),
				"manual", mapper.createObjectNode(), "entry");
		RunEntity older = new RunEntity("run_older", "wf_2", mapper.createObjectNode(),
				"webhook", mapper.createObjectNode(), "entry");
		when(runs.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(recent, older));

		var result = new RunQueryService(runs, workflows).list(null, null);

		assertEquals(2, result.size());
		assertEquals("run_recent", result.get(0).runId());
		assertEquals("wf_1", result.get(0).workflowId());
		assertEquals("run_older", result.get(1).runId());
		verify(runs).findAllByOrderByCreatedAtDesc();
		verifyNoInteractions(workflows);
	}

	@Test
	void unknownWorkflowDoesNotQueryRuns() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);
		when(workflows.existsById("missing")).thenReturn(false);

		assertThrows(WorkflowNotFoundException.class,
				() -> new RunQueryService(runs, workflows).list("missing", null));

		verifyNoInteractions(runs);
	}

	@Test
	void blankWorkflowFilterIsRejected() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);

		assertThrows(IllegalArgumentException.class,
				() -> new RunQueryService(runs, workflows).list("  ", null));

		verifyNoInteractions(runs, workflows);
	}

	@Test
	void listsRunsByStatusInRepositoryOrder() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);
		RunEntity recent = new RunEntity("run_recent", "wf_1", mapper.createObjectNode(),
				"manual", mapper.createObjectNode(), "entry");
		RunEntity older = new RunEntity("run_older", "wf_2", mapper.createObjectNode(),
				"webhook", mapper.createObjectNode(), "entry");
		when(runs.findByStatusOrderByCreatedAtDesc("failed")).thenReturn(List.of(recent, older));

		var result = new RunQueryService(runs, workflows).list(null, "failed");

		assertEquals(List.of("run_recent", "run_older"),
				result.stream().map(summary -> summary.runId()).toList());
		verify(runs).findByStatusOrderByCreatedAtDesc("failed");
		verifyNoInteractions(workflows);
	}

	@Test
	void combinesWorkflowAndStatusFilters() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);
		RunEntity matching = new RunEntity("run_1", "wf_1", mapper.createObjectNode(),
				"manual", mapper.createObjectNode(), "entry");
		when(workflows.existsById("wf_1")).thenReturn(true);
		when(runs.findByWorkflowIdAndStatusOrderByCreatedAtDesc("wf_1", "running"))
				.thenReturn(List.of(matching));

		var result = new RunQueryService(runs, workflows).list("wf_1", "running");

		assertEquals(1, result.size());
		assertEquals("run_1", result.get(0).runId());
		verify(runs).findByWorkflowIdAndStatusOrderByCreatedAtDesc("wf_1", "running");
	}

	@Test
	void unsupportedStatusIsRejectedBeforeQuerying() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);

		assertThrows(IllegalArgumentException.class,
				() -> new RunQueryService(runs, workflows).list(null, "unknown"));

		verifyNoInteractions(runs, workflows);
	}

	@Test
	void blankStatusIsRejectedBeforeQuerying() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);

		assertThrows(IllegalArgumentException.class,
				() -> new RunQueryService(runs, workflows).list(null, " "));

		verifyNoInteractions(runs, workflows);
	}
}
