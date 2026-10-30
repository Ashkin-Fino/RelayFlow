package com.airtribe.relayflow.run.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.airtribe.relayflow.run.entity.RunEntity;
import com.airtribe.relayflow.run.exceptions.WebhookUnauthorizedException;
import com.airtribe.relayflow.run.repository.QueueJobRepository;
import com.airtribe.relayflow.run.repository.RunRepository;
import com.airtribe.relayflow.workflow.entity.WorkflowEntity;
import com.airtribe.relayflow.workflow.repository.WorkflowRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RunTriggerServiceTest {
	private final WorkflowRepository workflowRepository = Mockito.mock(WorkflowRepository.class);
	private final RunRepository runRepository = Mockito.mock(RunRepository.class);
	private final QueueJobRepository queueJobRepository = Mockito.mock(QueueJobRepository.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private RunTriggerService runTriggerService;

	@BeforeEach
	void setUp() {
		runTriggerService = new RunTriggerService(workflowRepository, runRepository, queueJobRepository);
	}

	@Test
	void manualTriggerPersistsRunAndEnqueuesIt() {
		WorkflowEntity workflow = workflow("manual", "");
		when(workflowRepository.findById("wf_trigger_test")).thenReturn(Optional.of(workflow));
		JsonNode request = json("{\"input\":{\"customer_email\":\"maya@example.com\"}}");

		var response = runTriggerService.triggerManual("wf_trigger_test", request);

		assertEquals("run_".length() + 32, response.runId().length());
		ArgumentCaptor<RunEntity> runCaptor = ArgumentCaptor.forClass(RunEntity.class);
		verify(runRepository).save(runCaptor.capture());
		assertEquals("manual", runCaptor.getValue().getTriggerType());
		assertEquals("maya@example.com", runCaptor.getValue().getTriggerInput().path("customer_email").asString());
		verify(queueJobRepository).enqueue(response.runId());
	}

	@Test
	void webhookTriggerRejectsWrongSecretBeforeCreatingRun() {
		when(workflowRepository.findById("wf_trigger_test")).thenReturn(Optional.of(workflow("webhook", "expected")));

		assertThrows(WebhookUnauthorizedException.class, () -> runTriggerService.triggerWebhook(
				"wf_trigger_test", "wrong", json("{\"order_id\":\"ord_1\"}")));

		verifyNoInteractions(runRepository, queueJobRepository);
	}

	@Test
	void webhookTriggerPersistsBodyAndEnqueuesWhenSecretMatches() {
		when(workflowRepository.findById("wf_trigger_test")).thenReturn(Optional.of(workflow("webhook", "expected")));
		JsonNode body = json("{\"order_id\":\"ord_1\"}");

		var response = runTriggerService.triggerWebhook("wf_trigger_test", "expected", body);

		ArgumentCaptor<RunEntity> runCaptor = ArgumentCaptor.forClass(RunEntity.class);
		verify(runRepository).save(runCaptor.capture());
		assertEquals("webhook", runCaptor.getValue().getTriggerType());
		assertEquals("ord_1", runCaptor.getValue().getTriggerInput().path("order_id").asString());
		verify(queueJobRepository).enqueue(response.runId());
		verify(queueJobRepository, never()).complete(any(Long.class), any());
	}

	private WorkflowEntity workflow(String triggerType, String secret) {
		String secretField = secret.isEmpty() ? "" : ",\"secret\":\"" + secret + "\"";
		JsonNode definition = json("""
				{"id":"wf_trigger_test","name":"Trigger test","trigger":{"type":"%s"%s},"entry":"hello","limits":{"max_steps":5},"nodes":[{"id":"hello","type":"notify","params":{"channel":"chat","to":"#test","message":"hello"},"next":null}]}
				""".formatted(triggerType, secretField));
		return new WorkflowEntity("wf_trigger_test", "Trigger test", null, "published", definition);
	}

	private JsonNode json(String value) {
		return objectMapper.readTree(value);
	}
}
