package com.airtribe.relayflow.workflow.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.common.entity.WorkflowEntity;
import com.airtribe.relayflow.common.exception.WorkflowConflictException;
import com.airtribe.relayflow.common.exception.WorkflowNotFoundException;
import com.airtribe.relayflow.common.repository.WorkflowRepository;
import com.airtribe.relayflow.workflow.dto.WorkflowStatusResponse;
import com.airtribe.relayflow.workflow.dto.WorkflowSummary;

import tools.jackson.databind.JsonNode;

@Service
public class WorkflowService {
	private final WorkflowRepository workflowRepository;
	private final WorkflowDefinitionValidator validator;

	public WorkflowService(WorkflowRepository workflowRepository,
			WorkflowDefinitionValidator validator) {
		this.workflowRepository = workflowRepository;
		this.validator = validator;
	}

	public List<WorkflowSummary> list() {
		return workflowRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
				.map(workflow -> new WorkflowSummary(workflow.getId(), workflow.getName(), workflow.getStatus()))
				.toList();
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
	public WorkflowStatusResponse publish(String workflowId) {
		WorkflowEntity workflow = workflowRepository.findById(workflowId)
				.orElseThrow(() -> new WorkflowNotFoundException(workflowId));
		validator.validate(workflow.getDefinition());
		workflow.publish();
		workflowRepository.save(workflow);
		return new WorkflowStatusResponse(workflowId, "published");
	}
}