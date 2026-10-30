package com.airtribe.relayflow.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "relay.ai")
public record AiProviderProperties(Adapter adapter, URI baseUrl, String model, String apiKey) {
	public enum Adapter {
		MOCK,
		REAL
	}
}