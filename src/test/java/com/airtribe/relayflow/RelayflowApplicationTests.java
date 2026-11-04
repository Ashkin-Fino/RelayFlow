package com.airtribe.relayflow;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.airtribe.relayflow.common.repository.WorkflowRepository;
import com.airtribe.relayflow.config.AiProviderProperties;

@SpringBootTest(properties = {
		"relay.ai.adapter=mock",
		"relay.ai.base-url=http://localhost:9001/v1",
		"relay.ai.model=alpha-small"
})
class RelayflowApplicationTests {
	@Autowired
	private AiProviderProperties aiProviderProperties;

	@Autowired
	private WorkflowRepository workflowRepository;

	@Test
	void contextLoads() {
		assertEquals(AiProviderProperties.Adapter.MOCK, aiProviderProperties.adapter());
		assertEquals(URI.create("http://localhost:9001/v1"), aiProviderProperties.baseUrl());
		assertEquals("alpha-small", aiProviderProperties.model());
		assertTrue(workflowRepository.findById("wf_support_triage").isPresent());
	}
}
