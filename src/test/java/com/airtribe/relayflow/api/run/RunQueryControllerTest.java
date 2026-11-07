package com.airtribe.relayflow.api.run;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.airtribe.relayflow.api.run.service.RunCancellationService;
import com.airtribe.relayflow.api.run.service.RunTraceService;
import com.airtribe.relayflow.api.run.dto.RunSummary;
import com.airtribe.relayflow.api.run.service.RunQueryService;
import com.airtribe.relayflow.auth.DemoTokenFilter;
import com.airtribe.relayflow.common.exception.GlobalExceptionHandler;

class RunQueryControllerTest {
	private static final String DEMO_TOKEN = "run-query-test-token";
	private final RunQueryService service = Mockito.mock(RunQueryService.class);
	private final RunTraceService traceService = Mockito.mock(RunTraceService.class);
	private final RunCancellationService cancellationService = Mockito.mock(RunCancellationService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
				new RunController(cancellationService, service, traceService))
				.addFilters(new DemoTokenFilter(DEMO_TOKEN))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void listsWorkflowRunsWithBearerToken() throws Exception {
		when(service.list("wf_1", null)).thenReturn(List.of(new RunSummary(
				"run_1", "wf_1", "running", "manual", "notify", 2, 0,
				false, null, null, null)));

		mockMvc.perform(get("/runs").param("workflowId", "wf_1")
						.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].run_id").value("run_1"))
				.andExpect(jsonPath("$[0].workflow_id").value("wf_1"))
				.andExpect(jsonPath("$[0].status").value("running"))
				.andExpect(jsonPath("$[0].steps_executed").value(2));
	}

	@Test
	void listsRunsByStatus() throws Exception {
		when(service.list(null, "failed")).thenReturn(List.of(new RunSummary(
				"run_1", "wf_1", "failed", "manual", "notify", 2, 0,
				false, null, null, null)));

		mockMvc.perform(get("/runs").param("status", "failed")
						.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("failed"));
	}

	@Test
	void combinesWorkflowAndStatusFilters() throws Exception {
		when(service.list("wf_1", "running")).thenReturn(List.of(new RunSummary(
				"run_1", "wf_1", "running", "manual", "notify", 2, 0,
				false, null, null, null)));

		mockMvc.perform(get("/runs").param("workflowId", "wf_1").param("status", "running")
						.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].workflow_id").value("wf_1"))
				.andExpect(jsonPath("$[0].status").value("running"));
	}

	@Test
	void listsAllRunsWhenWorkflowFilterIsOmitted() throws Exception {
		when(service.list(null, null)).thenReturn(List.of(new RunSummary(
				"run_1", "wf_1", "succeeded", "manual", null, 1, 0,
				false, null, null, null)));

		mockMvc.perform(get("/runs").header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[0].run_id").value("run_1"))
				.andExpect(jsonPath("$[0].workflow_id").value("wf_1"))
				.andExpect(jsonPath("$[0].status").value("succeeded"));
	}

	@Test
	void rejectsBlankWorkflowFilter() throws Exception {
		when(service.list("", null)).thenThrow(new IllegalArgumentException("workflowId must not be blank"));

		mockMvc.perform(get("/runs").param("workflowId", "")
						.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("invalid_request"));
	}

	@Test
	void rejectsUnsupportedStatusFilter() throws Exception {
		when(service.list(null, "unknown"))
				.thenThrow(new IllegalArgumentException("unsupported status"));

		mockMvc.perform(get("/runs").param("status", "unknown")
						.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("invalid_request"));
	}

	@Test
	void rejectsBlankStatusFilter() throws Exception {
		when(service.list(null, ""))
				.thenThrow(new IllegalArgumentException("status must not be blank"));

		mockMvc.perform(get("/runs").param("status", "")
						.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("invalid_request"));
	}
}
