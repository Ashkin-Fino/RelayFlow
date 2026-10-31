package com.airtribe.relayflow;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.airtribe.relayflow.config.AiProviderProperties;

@SpringBootTest
class RelayflowApplicationTests {
	@Autowired
	private AiProviderProperties aiProviderProperties;

	@Test
	void contextLoads() {
		assertEquals(AiProviderProperties.Adapter.MOCK, aiProviderProperties.adapter());
		assertEquals(URI.create("http://localhost:9001/v1"), aiProviderProperties.baseUrl());
		assertEquals("alpha-small", aiProviderProperties.model());
	}
}
