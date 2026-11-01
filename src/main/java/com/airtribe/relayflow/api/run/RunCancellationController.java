package com.airtribe.relayflow.api.run;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.airtribe.relayflow.api.run.dto.RunCancellationResponse;
import com.airtribe.relayflow.api.run.service.RunCancellationService;

@RestController
public class RunCancellationController {
	private final RunCancellationService service;

	public RunCancellationController(RunCancellationService service) {
		this.service = service;
	}

	@PostMapping("/runs/{runId}/cancel")
	public ResponseEntity<RunCancellationResponse> cancel(@PathVariable String runId) {
		return ResponseEntity.ok(service.cancel(runId));
	}
}
