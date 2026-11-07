package com.airtribe.relayflow.api.run.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunAcceptedResponse(@JsonProperty("run_id") String runId) {
}