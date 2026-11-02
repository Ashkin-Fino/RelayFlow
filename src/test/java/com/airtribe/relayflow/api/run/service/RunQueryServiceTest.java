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

		var result = new RunQueryService(runs, workflows).listByWorkflow("wf_1");

		assertEquals(2, result.size());
		assertEquals("run_recent", result.get(0).runId());
		assertEquals("run_older", result.get(1).runId());
		assertEquals("succeeded", result.get(1).status());
		verify(runs).findByWorkflowIdOrderByCreatedAtDesc("wf_1");
	}

	@Test
	void unknownWorkflowDoesNotQueryRuns() {
		RunRepository runs = mock(RunRepository.class);
		WorkflowRepository workflows = mock(WorkflowRepository.class);
		when(workflows.existsById("missing")).thenReturn(false);

		assertThrows(WorkflowNotFoundException.class,
				() -> new RunQueryService(runs, workflows).listByWorkflow("missing"));

		verifyNoInteractions(runs);
	}
}
