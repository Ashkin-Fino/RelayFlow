package com.airtribe.relayflow.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {
	private final List<String> frontendOrigins;

	public WebCorsConfiguration(@Value("${relay.frontend-origin:http://localhost:5173,http://127.0.0.1:5173}") String frontendOrigins) {
		this.frontendOrigins = Arrays.stream(frontendOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins(frontendOrigins.toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "OPTIONS")
				.allowedHeaders("Accept", "Authorization", "Content-Type")
				.maxAge(3600);
	}
}
