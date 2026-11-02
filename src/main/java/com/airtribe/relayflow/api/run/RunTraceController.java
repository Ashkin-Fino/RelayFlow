package com.airtribe.relayflow.api.run;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.airtribe.relayflow.api.run.dto.RunTraceResponse;
import com.airtribe.relayflow.api.run.service.RunTraceService;

@RestController
public class RunTraceController {
	private final RunTraceService service;

	public RunTraceController(RunTraceService service) {
		this.service = service;
	}

	@GetMapping("/runs/{runId}")
	public RunTraceResponse getRunTrace(@PathVariable String runId) {
		return service.getTrace(runId);
	}
}
