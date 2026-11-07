package com.airtribe.relayflow.api.run;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.airtribe.relayflow.api.run.dto.RunCancellationResponse;
import com.airtribe.relayflow.api.run.dto.RunSummary;
import com.airtribe.relayflow.api.run.dto.RunTraceResponse;
import com.airtribe.relayflow.api.run.service.RunCancellationService;
import com.airtribe.relayflow.api.run.service.RunQueryService;
import com.airtribe.relayflow.api.run.service.RunTraceService;

@RestController
@RequestMapping("/runs")
public class RunController {
	private final RunCancellationService cancellationService;
	private final RunQueryService queryService;
	private final RunTraceService traceService;

	public RunController(RunCancellationService cancellationService, RunQueryService queryService,
			RunTraceService traceService) {
		this.cancellationService = cancellationService;
		this.queryService = queryService;
		this.traceService = traceService;
	}

	@GetMapping
	public List<RunSummary> list(@RequestParam(required = false) String workflowId,
			@RequestParam(required = false) String status) {
		return queryService.list(workflowId, status);
	}

	@GetMapping("/{runId}")
	public RunTraceResponse getRunTrace(@PathVariable String runId) {
		return traceService.getTrace(runId);
	}

	@PostMapping("/{runId}/cancel")
	public ResponseEntity<RunCancellationResponse> cancel(@PathVariable String runId) {
		return ResponseEntity.ok(cancellationService.cancel(runId));
	}
}
