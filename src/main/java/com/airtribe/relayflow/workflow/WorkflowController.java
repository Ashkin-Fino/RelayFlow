package com.airtribe.relayflow.workflow;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.airtribe.relayflow.workflow.dto.WorkflowStatusResponse;
import com.airtribe.relayflow.workflow.dto.WorkflowSummary;
import com.airtribe.relayflow.workflow.service.WorkflowService;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {
	private final WorkflowService workflowService;

	public WorkflowController(WorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@GetMapping
	public List<WorkflowSummary> list() {
		return workflowService.list();
	}

	@PostMapping
	public ResponseEntity<WorkflowStatusResponse> create(@RequestBody JsonNode definition) {
		return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.create(definition));
	}

	@PostMapping("/{workflowId}/publish")
	public WorkflowStatusResponse publish(@PathVariable String workflowId) {
		return workflowService.publish(workflowId);
	}
}