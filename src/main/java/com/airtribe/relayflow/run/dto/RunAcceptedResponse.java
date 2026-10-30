package com.airtribe.relayflow.run.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunAcceptedResponse(@JsonProperty("run_id") String runId) {
}