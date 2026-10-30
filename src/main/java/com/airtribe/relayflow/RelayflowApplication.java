package com.airtribe.relayflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RelayflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(RelayflowApplication.class, args);
	}

}
