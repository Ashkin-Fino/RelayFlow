package com.airtribe.relayflow.api.workflow.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airtribe.relayflow.api.run.service.RunTriggerService;
import com.airtribe.relayflow.common.entity.WorkflowEntity;
import com.airtribe.relayflow.common.repository.WorkflowRepository;
import com.airtribe.relayflow.common.repository.WorkflowScheduleRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WorkflowScheduleServiceTest {
	private final WorkflowScheduleRepository scheduleRepository =
			Mockito.mock(WorkflowScheduleRepository.class);
	private final WorkflowRepository workflowRepository = Mockito.mock(WorkflowRepository.class);
	private final RunTriggerService runTriggerService = Mockito.mock(RunTriggerService.class);
	private final WorkflowScheduleService service = new WorkflowScheduleService(
			scheduleRepository, workflowRepository, runTriggerService);
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void registersPublishedScheduleAtItsNextUtcExecution() {
		JsonNode definition = scheduledDefinition("*/5 * * * *");
		Instant before = Instant.now();

		service.syncPublishedWorkflow("wf_schedule", definition);

		ArgumentCaptor<Instant> nextFireAt = ArgumentCaptor.forClass(Instant.class);
		verify(scheduleRepository).delete("wf_schedule");
		verify(scheduleRepository).upsert(Mockito.eq("wf_schedule"),
				Mockito.eq("*/5 * * * *"), nextFireAt.capture());
		assertTrue(nextFireAt.getValue().isAfter(before));
	}

	@Test
	void duePublishedScheduleCreatesNormalScheduledRunAndAdvances() {
		when(scheduleRepository.lockDueSchedules(50)).thenReturn(
				List.of(new WorkflowScheduleRepository.DueWorkflowSchedule(
						"wf_schedule", "*/5 * * * *")));
		WorkflowEntity workflow = new WorkflowEntity("wf_schedule", "Scheduled", null, "published",
				scheduledDefinition("*/5 * * * *"));
		when(workflowRepository.findById("wf_schedule")).thenReturn(Optional.of(workflow));
		Instant before = Instant.now();

		service.dispatchDueSchedules();

		verify(runTriggerService).triggerScheduled("wf_schedule");
		ArgumentCaptor<Instant> nextFireAt = ArgumentCaptor.forClass(Instant.class);
		verify(scheduleRepository).advance(Mockito.eq("wf_schedule"), nextFireAt.capture());
		assertTrue(nextFireAt.getValue().isAfter(before));
	}

	private JsonNode scheduledDefinition(String cron) {
		return objectMapper.readTree("""
				{"id":"wf_schedule","name":"Scheduled","trigger":{"type":"schedule","cron":"%s"},
				 "entry":"hello","limits":{"max_steps":5},
				 "nodes":[{"id":"hello","type":"notify",
				   "params":{"channel":"chat","to":"#test","message":"Hello"},"next":null}]}
				""".formatted(cron));
	}
}
