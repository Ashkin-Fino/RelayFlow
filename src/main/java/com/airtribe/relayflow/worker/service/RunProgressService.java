package com.airtribe.relayflow.worker.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.RunNotFoundException;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;

import tools.jackson.databind.JsonNode;

@Service
public class RunProgressService {
    private final RunRepository runRepository;
    private final StepRepository stepRepository;
    private final QueueJobRepository queueJobRepository;

    public RunProgressService(RunRepository runRepository, StepRepository stepRepository,
            QueueJobRepository queueJobRepository) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.queueJobRepository = queueJobRepository;
    }

    @Transactional
    public RunEntity start(String runId) {
        RunEntity run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
        run.start();
        return runRepository.save(run);
    }

    /**
     * Persist the running attempt before calling an external service. If the
     * worker dies, a reclaimed job sees this row and reuses the same attempt
     * number and idempotency key.
     */
    @Transactional
    public StepEntity prepareStep(StepEntity step) {
        return stepRepository.save(step);
    }

    @Transactional
    public void completeStep(String runId, long jobId, String leaseToken, StepEntity step,
            String nextNodeId, long delaySeconds) {
        RunEntity run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
        stepRepository.save(step);
        run.advance(nextNodeId);
        if (nextNodeId == null) {
            run.succeed();
        }
        runRepository.save(run);
        if (run.getStatus().equals("succeeded")) {
            queueJobRepository.complete(jobId, leaseToken, null);
        } else {
            queueJobRepository.reschedule(jobId, leaseToken, delaySeconds);
        }
    }

    /**
     * Persist the transient failure and release the queue lease in the same
     * transaction. The job becomes eligible again only after the backoff.
     */
    @Transactional
    public void retryStep(String runId, long jobId, String leaseToken,
            StepEntity step, JsonNode error, long delaySeconds) {
        runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
        step.waitForRetry(error, 0);
        stepRepository.save(step);
        queueJobRepository.reschedule(jobId, leaseToken, delaySeconds);
    }

    @Transactional
    public void failStep(String runId, long jobId, String leaseToken, StepEntity step, JsonNode error) {
        RunEntity run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
        stepRepository.save(step);
        run.fail(error);
        runRepository.save(run);
        queueJobRepository.complete(jobId, leaseToken, error.path("message").asString());
    }

    @Transactional
    public void failRun(String runId, long jobId, String leaseToken, JsonNode error) {
        RunEntity run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
        run.fail(error);
        runRepository.save(run);
        queueJobRepository.complete(jobId, leaseToken, error.path("message").asString());
    }
}
