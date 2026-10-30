package com.airtribe.relayflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiProviderPropertiesTest {
	@Test
	void bindsAdapterAndProviderSettings() {
		var source = new MapConfigurationPropertySource(Map.of(
				"relay.ai.adapter", "mock",
				"relay.ai.base-url", "http://localhost:9001/v1",
				"relay.ai.model", "alpha-small",
				"relay.ai.api-key", "local-test-key"));

		var properties = new Binder(source)
            .bind("relay.ai", Bindable.of(AiProviderProperties.class))
			.get();

		assertEquals(AiProviderProperties.Adapter.MOCK, properties.adapter());
		assertEquals(URI.create("http://localhost:9001/v1"), properties.baseUrl());
		assertEquals("alpha-small", properties.model());
		assertEquals("local-test-key", properties.apiKey());
	}
}
