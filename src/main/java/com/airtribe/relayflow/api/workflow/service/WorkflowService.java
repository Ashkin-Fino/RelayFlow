package com.airtribe.relayflow.api.workflow.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.api.workflow.dto.WorkflowStatusResponse;
import com.airtribe.relayflow.api.workflow.dto.WorkflowDetailResponse;
import com.airtribe.relayflow.api.workflow.dto.WorkflowSummary;
import com.airtribe.relayflow.common.entity.WorkflowEntity;
import com.airtribe.relayflow.common.exception.WorkflowConflictException;
import com.airtribe.relayflow.common.exception.WorkflowNotFoundException;
import com.airtribe.relayflow.common.exception.WorkflowStateConflictException;
import com.airtribe.relayflow.common.repository.WorkflowRepository;

import tools.jackson.databind.JsonNode;

@Service
public class WorkflowService {
	private final WorkflowRepository workflowRepository;
	private final WorkflowDefinitionValidator validator;
	private final WorkflowScheduleService workflowScheduleService;

	public WorkflowService(WorkflowRepository workflowRepository,
			WorkflowDefinitionValidator validator, WorkflowScheduleService workflowScheduleService) {
		this.workflowRepository = workflowRepository;
		this.validator = validator;
		this.workflowScheduleService = workflowScheduleService;
	}

	public List<WorkflowSummary> list() {
		return workflowRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
				.map(workflow -> new WorkflowSummary(workflow.getId(), workflow.getName(), workflow.getStatus()))
				.toList();
	}

	@Transactional(readOnly = true)
	public WorkflowDetailResponse get(String workflowId) {
		WorkflowEntity workflow = workflowRepository.findById(workflowId)
				.orElseThrow(() -> new WorkflowNotFoundException(workflowId));
		return new WorkflowDetailResponse(workflow.getId(), workflow.getName(),
				workflow.getDescription(), workflow.getStatus(), workflow.getDefinition(),
				workflow.getCreatedAt(), workflow.getUpdatedAt());
	}

	@Transactional
	public WorkflowStatusResponse create(JsonNode definition) {
		validator.validate(definition);
		String id = definition.path("id").asString().trim();
		String name = definition.path("name").asString().trim();
		String description = definition.path("description").isString()
				? definition.path("description").asString()
				: null;

		WorkflowEntity workflow = new WorkflowEntity(id, name, description, "draft", definition);
		try {
			workflowRepository.saveAndFlush(workflow);
		} catch (DataIntegrityViolationException exception) {
			throw new WorkflowConflictException(id);
		}
		return new WorkflowStatusResponse(id, "draft");
	}

	@Transactional
	public WorkflowStatusResponse updateDraft(String workflowId, JsonNode definition) {
		WorkflowEntity workflow = workflowRepository.findById(workflowId)
				.orElseThrow(() -> new WorkflowNotFoundException(workflowId));
		if (!"draft".equals(workflow.getStatus())) {
			throw new WorkflowStateConflictException(
					"Workflow '" + workflowId + "' is not a Draft and cannot be edited");
		}
		validator.validate(definition);
		String definitionId = definition.path("id").asString().trim();
		if (!workflowId.equals(definitionId)) {
			throw new IllegalArgumentException("Workflow ID in the definition must match the request path");
		}

		String name = definition.path("name").asString().trim();
		String description = definition.path("description").isString()
				? definition.path("description").asString()
				: null;
		workflow.updateDraft(name, description, definition);
		workflowRepository.save(workflow);
		return new WorkflowStatusResponse(workflowId, "draft");
	}

	@Transactional
	public WorkflowStatusResponse publish(String workflowId) {
		WorkflowEntity workflow = workflowRepository.findById(workflowId)
				.orElseThrow(() -> new WorkflowNotFoundException(workflowId));
		validator.validate(workflow.getDefinition());
		workflow.publish();
		workflowRepository.save(workflow);
		workflowScheduleService.syncPublishedWorkflow(workflowId, workflow.getDefinition());
		return new WorkflowStatusResponse(workflowId, "published");
	}
}