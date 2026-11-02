package com.airtribe.relayflow.api.run;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.airtribe.relayflow.api.run.dto.RunTraceResponse;
import com.airtribe.relayflow.api.run.dto.RunCancellationResponse;
import com.airtribe.relayflow.api.run.dto.StepTraceResponse;
import com.airtribe.relayflow.api.run.service.RunCancellationService;
import com.airtribe.relayflow.api.run.service.RunQueryService;
import com.airtribe.relayflow.api.run.service.RunTraceService;
import com.airtribe.relayflow.auth.DemoTokenFilter;
import com.airtribe.relayflow.common.exception.GlobalExceptionHandler;
import com.airtribe.relayflow.common.exception.RunNotFoundException;

import tools.jackson.databind.ObjectMapper;

class RunTraceControllerTest {
	private static final String DEMO_TOKEN = "run-trace-test-token";
	private final RunTraceService service = Mockito.mock(RunTraceService.class);
	private final RunQueryService queryService = Mockito.mock(RunQueryService.class);
	private final RunCancellationService cancellationService = Mockito.mock(RunCancellationService.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
				new RunController(cancellationService, queryService, service))
				.addFilters(new DemoTokenFilter(DEMO_TOKEN))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsContractRunTraceWithBearerToken() throws Exception {
		when(service.getTrace("run_1")).thenReturn(new RunTraceResponse(
				"run_1", "wf_1", "succeeded", "manual", null, 1, 0,
				false, null, null, null, List.of(new StepTraceResponse(
						"hello", "notify", "succeeded", 1, 1,
						mapper.readTree("{\"message\":\"hello\"}"),
						mapper.readTree("{\"notification_id\":\"n1\"}"),
						null, null, null, 8L, null, null, "run_1:hello:1",
						null, null, null, null, null))));

		mockMvc.perform(get("/runs/run_1").header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run_id").value("run_1"))
				.andExpect(jsonPath("$.workflow_id").value("wf_1"))
				.andExpect(jsonPath("$.status").value("succeeded"))
				.andExpect(jsonPath("$.steps[0].node_id").value("hello"))
				.andExpect(jsonPath("$.steps[0].status").value("succeeded"))
				.andExpect(jsonPath("$.steps[0].input.message").value("hello"))
				.andExpect(jsonPath("$.steps[0].duration_ms").value(8));
	}

	@Test
	void unknownRunReturnsStructured404() throws Exception {
		when(service.getTrace("missing")).thenThrow(new RunNotFoundException("missing"));

		mockMvc.perform(get("/runs/missing").header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("run_not_found"));
	}

	@Test
	void cancellationRouteRemainsAvailableOnRunController() throws Exception {
		when(cancellationService.cancel(anyString()))
				.thenReturn(new RunCancellationResponse("run_1", "running", true));

		mockMvc.perform(post("/runs/run_1/cancel")
						.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run_id").value("run_1"))
				.andExpect(jsonPath("$.status").value("running"))
				.andExpect(jsonPath("$.cancel_requested").value(true));
	}
}
