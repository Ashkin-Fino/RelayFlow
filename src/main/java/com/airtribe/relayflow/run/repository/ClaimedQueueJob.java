package com.airtribe.relayflow.run.repository;

/**
 * A claimed queue job and the unique fencing token required for every later mutation.
 */
public record ClaimedQueueJob(long jobId, String runId, String leaseToken) {
}