package com.airtribe.relayflow.workflow.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.dao.DataIntegrityViolationException;

import com.airtribe.relayflow.catalog.NodeCatalog;
import com.airtribe.relayflow.common.entity.WorkflowEntity;
import com.airtribe.relayflow.common.exception.WorkflowConflictException;
import com.airtribe.relayflow.common.repository.WorkflowRepository;
import com.airtribe.relayflow.workflow.dto.WorkflowStatusResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WorkflowServiceTest {
	private final WorkflowRepository workflowRepository = Mockito.mock(WorkflowRepository.class);
	private WorkflowService workflowService;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper();
		workflowService = new WorkflowService(
				workflowRepository,
				new WorkflowDefinitionValidator(new NodeCatalog(objectMapper)));
	}

	@Test
	void createsValidatedDefinitionAsDraft() {
		JsonNode definition = validDefinition();
		when(workflowRepository.saveAndFlush(any(WorkflowEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		WorkflowStatusResponse response = workflowService.create(definition);

		assertEquals(new WorkflowStatusResponse("wf_service_test", "draft"), response);
		ArgumentCaptor<WorkflowEntity> saved = ArgumentCaptor.forClass(WorkflowEntity.class);
		verify(workflowRepository).saveAndFlush(saved.capture());
		assertEquals("draft", saved.getValue().getStatus());
	}

	@Test
	void mapsDuplicateIdConstraintToConflict() {
		when(workflowRepository.saveAndFlush(any(WorkflowEntity.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate id"));

		WorkflowConflictException exception = org.junit.jupiter.api.Assertions.assertThrows(
				WorkflowConflictException.class, () -> workflowService.create(validDefinition()));

		assertEquals("Workflow 'wf_service_test' already exists", exception.getMessage());
	}

	@Test
	void revalidatesStoredDefinitionBeforePublishing() {
		WorkflowEntity entity = new WorkflowEntity(
				"wf_service_test", "Service test", null, "draft", validDefinition());
		when(workflowRepository.findById("wf_service_test")).thenReturn(Optional.of(entity));

		WorkflowStatusResponse response = workflowService.publish("wf_service_test");

		assertEquals(new WorkflowStatusResponse("wf_service_test", "published"), response);
		assertEquals("published", entity.getStatus());
		verify(workflowRepository).save(entity);
	}

	private JsonNode validDefinition() {
		return new ObjectMapper().readTree("""
				{
				  "id": "wf_service_test",
				  "name": "Service test",
				  "trigger": { "type": "manual" },
				  "entry": "hello",
				  "limits": { "max_steps": 5 },
				  "nodes": [{
				    "id": "hello",
				    "type": "notify",
				    "params": { "channel": "chat", "to": "#test", "message": "Hello" },
				    "next": null
				  }]
				}
				""");
	}
}