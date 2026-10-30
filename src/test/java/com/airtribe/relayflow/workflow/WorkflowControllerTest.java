package com.airtribe.relayflow.workflow;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.airtribe.relayflow.auth.DemoTokenFilter;
import com.airtribe.relayflow.workflow.dto.WorkflowStatusResponse;
import com.airtribe.relayflow.workflow.dto.WorkflowSummary;
import com.airtribe.relayflow.workflow.exceptions.WorkflowApiExceptionHandler;
import com.airtribe.relayflow.workflow.exceptions.WorkflowDefinitionException;
import com.airtribe.relayflow.workflow.service.WorkflowService;

import tools.jackson.databind.JsonNode;

class WorkflowControllerTest {
	private static final String DEMO_TOKEN = "phase-two-test-token";
	private final WorkflowService workflowService = Mockito.mock(WorkflowService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowController(workflowService))
				.addFilters(new DemoTokenFilter(DEMO_TOKEN))
				.setControllerAdvice(new WorkflowApiExceptionHandler())
				.build();
	}

	@Test
	void listsWorkflowsWithBearerToken() throws Exception {
		when(workflowService.list()).thenReturn(List.of(
				new WorkflowSummary("wf_demo", "Demo", "published")));

		mockMvc.perform(get("/workflows").header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value("wf_demo"))
				.andExpect(jsonPath("$[0].status").value("published"));
	}

	@Test
	void rejectsWorkflowListWithoutBearerToken() throws Exception {
		mockMvc.perform(get("/workflows"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("unauthorized"));
	}

	@Test
	void rejectsIncorrectBearerToken() throws Exception {
		mockMvc.perform(get("/workflows").header("Authorization", "Bearer incorrect"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("unauthorized"));
	}

	@Test
	void createsDraftUsingCanonicalWorkflowBody() throws Exception {
		when(workflowService.create(any(JsonNode.class)))
				.thenReturn(new WorkflowStatusResponse("wf_demo", "draft"));

		mockMvc.perform(post("/workflows")
					.header("Authorization", "Bearer " + DEMO_TOKEN)
					.contentType("application/json")
					.content("""
							{"id":"wf_demo","name":"Demo","trigger":{"type":"manual"},"entry":"hello","limits":{"max_steps":5},"nodes":[]}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("wf_demo"))
				.andExpect(jsonPath("$.status").value("draft"));
	}

	@Test
	void returnsStructuredClientErrorForInvalidDefinition() throws Exception {
		doThrow(new WorkflowDefinitionException("invalid_node_type", "Node 'n1' has unknown type 'teleport'"))
				.when(workflowService).create(any(JsonNode.class));

		mockMvc.perform(post("/workflows")
					.header("Authorization", "Bearer " + DEMO_TOKEN)
					.contentType("application/json")
					.content("{}"))
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.error.code").value("invalid_node_type"));
	}

	@Test
	void publishesWorkflowAtContractRoute() throws Exception {
		when(workflowService.publish("wf_demo"))
				.thenReturn(new WorkflowStatusResponse("wf_demo", "published"));

		mockMvc.perform(post("/workflows/wf_demo/publish")
					.header("Authorization", "Bearer " + DEMO_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("wf_demo"))
				.andExpect(jsonPath("$.status").value("published"));
	}
}
