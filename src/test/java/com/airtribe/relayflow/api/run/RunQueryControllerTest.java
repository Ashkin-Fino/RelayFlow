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
		when(service.listByWorkflow("wf_1")).thenReturn(List.of(new RunSummary(
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
	void workflowIdIsRequired() throws Exception {
		mockMvc.perform(get("/runs").header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isBadRequest());
	}
}
