package com.airtribe.relayflow.worker.dto;

/**
 * A claimed queue job and the unique fencing token required for every later mutation.
 */
public record ClaimedQueueJob(long jobId, String runId, String leaseToken) {
}