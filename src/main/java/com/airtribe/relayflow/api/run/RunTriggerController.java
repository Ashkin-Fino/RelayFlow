package com.airtribe.relayflow.api.run;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.airtribe.relayflow.api.run.dto.RunAcceptedResponse;
import com.airtribe.relayflow.api.run.service.RunTriggerService;

import tools.jackson.databind.JsonNode;

@RestController
public class RunTriggerController {
	private final RunTriggerService runTriggerService;

	public RunTriggerController(RunTriggerService runTriggerService) {
		this.runTriggerService = runTriggerService;
	}

	@PostMapping("/workflows/{workflowId}/trigger")
	public ResponseEntity<RunAcceptedResponse> triggerManual(
			@PathVariable String workflowId, @RequestBody JsonNode request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(runTriggerService.triggerManual(workflowId, request));
	}

	@PostMapping("/hooks/{workflowId}")
	public ResponseEntity<RunAcceptedResponse> triggerWebhook(
			@PathVariable String workflowId,
			@RequestHeader(name = "X-Relay-Secret", required = false) String secret,
			@RequestBody JsonNode body) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(runTriggerService.triggerWebhook(workflowId, secret, body));
	}
}