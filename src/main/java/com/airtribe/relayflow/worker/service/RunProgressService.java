package com.airtribe.relayflow.worker.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.common.entity.ApprovalEntity;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.RunNotFoundException;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;

import tools.jackson.databind.JsonNode;

@Service
public class RunProgressService {
    private final RunRepository runRepository;
    private final StepRepository stepRepository;
    private final QueueJobRepository queueJobRepository;
    private final ApprovalRepository approvalRepository;

    public RunProgressService(RunRepository runRepository, StepRepository stepRepository,
            QueueJobRepository queueJobRepository, ApprovalRepository approvalRepository) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.queueJobRepository = queueJobRepository;
        this.approvalRepository = approvalRepository;
    }

    @Transactional
    public RunEntity start(String runId) {
        // Lock the run row for update to prevent concurrent modifications.
        // This ensures that only one worker can modify the run at a time,
        // preventing race conditions and ensuring data consistency.
        RunEntity run = lockedRun(runId);
        run.start();
        return runRepository.save(run);
    }

    @Transactional
    public StepEntity prepareStep(StepEntity step) {
        // Persist the step in the same transaction. 
        // This ensures that the step is saved before it is executed, 
        // preventing race conditions and ensuring data consistency.
        return stepRepository.save(step);
    }

    @Transactional
    public boolean prepareStepForExecution(String runId, long jobId, 
            String leaseToken, StepEntity step) {
        /**
         * Persist the step and update the run status in the same transaction. 
         * If the run is cancelled, the step is also cancelled and the job is completed.
         * 
         * @param runId the id of the run
         * @param jobId the id of the queue job
         * @param leaseToken the lease token of the queue job
         * @param step the step entity to be persisted
         * @return true if the step is ready for execution, false if the run is cancelled
         */
        RunEntity run = lockedRun(runId);
        if (run.isCancelRequested() && "running".equals(run.getStatus())) {
            step.cancel();
            stepRepository.save(step);
            run.cancel();
            runRepository.save(run);
            queueJobRepository.complete(jobId, leaseToken, "Run cancelled");
            return false;
        }
        if (!"running".equals(run.getStatus())) {
            return false;
        }
        stepRepository.save(step);
        return true;
    }

    @Transactional
    public void completeStep(String runId, long jobId, String leaseToken, StepEntity step,
            String nextNodeId, long delaySeconds) {
        /**
         * Persist the step completion and update the run status in the same transaction.
         * 
         * @param runId the id of the run
         * @param jobId the id of the queue job
         * @param leaseToken the lease token of the queue job
         * @param step the step entity to be persisted
         * @param nextNodeId the id of the next node to execute, or 
         *        null if the run is complete
         * @param delaySeconds the delay before the next node is executed
         */
        RunEntity run = lockedRun(runId);
        stepRepository.save(step);
        run.addAiTokensUsed(tokenCount(step));
        run.advance(nextNodeId);
        if (run.isCancelRequested()) {
            run.cancel();
            runRepository.save(run);
            queueJobRepository.complete(jobId, leaseToken, "Run cancelled");
        } else if (nextNodeId == null) {
            run.succeed();
            runRepository.save(run);
            queueJobRepository.complete(jobId, leaseToken, null);
        } else {
            runRepository.save(run);
            queueJobRepository.reschedule(jobId, leaseToken, delaySeconds);
        }
    }

    @Transactional
    public void awaitApproval(String runId, long jobId, String leaseToken, StepEntity step, String message) {
        /**
         * Persist the approval request and release the queue lease in the same
         * transaction. The job becomes eligible again only after the approval is
         * decided. The approval step is persisted so that the approval can be
         * completed even if the worker dies before the approval is decided.
         * 
         * @param runId the id of the run
         * @param jobId the id of the queue job
         * @param leaseToken the lease token of the queue job
         * @param step the step entity to be persisted
         * @param message the approval message
         */
        RunEntity run = lockedRun(runId);
        if (run.isCancelRequested()) {
            run.cancel();
            runRepository.save(run);
            step.cancel();
            stepRepository.save(step);
            queueJobRepository.complete(jobId, leaseToken, "Run cancelled");
            return;
        }
        if (!"running".equals(run.getStatus()) || step.getId() == null) {
            throw new IllegalStateException("Approval step must be persisted for a running run");
        }
        step.waitForApproval();
        stepRepository.save(step);
        String approvalId = "apr_" + java.util.UUID.randomUUID().toString().replace("-", "");
        approvalRepository.save(new ApprovalEntity(
                approvalId, runId, step.getId(), step.getNodeId(), message));
        run.waitForApproval();
        runRepository.save(run);
        queueJobRepository.complete(jobId, leaseToken, "Waiting for approval");
    }

    @Transactional
    public void cancelAtBoundary(String runId, long jobId, String leaseToken) {
        /**
         * Persist the cancel request and release the queue lease in the same
         * transaction. The job becomes eligible again only after the cancel is
         * processed. The cancel request is persisted so that the run can be cancelled
         * even if the worker dies before the cancel is processed.
         * 
         * @param runId the id of the run
         * @param jobId the id of the queue job
         * @param leaseToken the lease token of the queue job
         */
        RunEntity run = lockedRun(runId);
        if (run.isCancelRequested() && "running".equals(run.getStatus())) {
            run.cancel();
            runRepository.save(run);
            queueJobRepository.complete(jobId, leaseToken, "Run cancelled");
        }
    }

    
    @Transactional
    public void retryStep(String runId, long jobId, String leaseToken,
            StepEntity step, JsonNode error, long durationMs, long delaySeconds) {
        /**
         * Persist the retry request and release the queue lease in the same
         * transaction. The job becomes eligible again only after the retry is processed.
         * 
         * @param runId the id of the run
         * @param jobId the id of the queue job
         * @param leaseToken the lease token of the queue job
         * @param step the step entity to be persisted
         * @param error the error that caused the retry
         * @param durationMs the duration of the step before the retry
         * @param delaySeconds the delay before the retry
         */
        RunEntity run = lockedRun(runId);
        if (run.isCancelRequested()) {
            step.fail(error, durationMs);
            stepRepository.save(step);
            run.cancel();
            runRepository.save(run);
            queueJobRepository.complete(jobId, leaseToken, "Run cancelled");
            return;
        }
        step.waitForRetry(error, durationMs);
        stepRepository.save(step);
        queueJobRepository.reschedule(jobId, leaseToken, delaySeconds);
    }

    @Transactional
    public void failStep(String runId, long jobId, String leaseToken, StepEntity step, JsonNode error) {
        /**
         * Persist the failure and release the queue lease in the same transaction. 
         * The job becomes eligible again only after the failure is processed.
         * 
         * @param runId the id of the run
         * @param jobId the id of the queue job
         * @param leaseToken the lease token of the queue job
         * @param step the step entity to be persisted
         * @param error the error that caused the failure
         */
        RunEntity run = lockedRun(runId);
        stepRepository.save(step);
        run.addAiTokensUsed(tokenCount(step));
        if (run.isCancelRequested()) {
            run.cancel();
        } else {
            run.fail(error);
        }
        runRepository.save(run);
        queueJobRepository.complete(jobId, leaseToken,
                run.isCancelRequested() ? "Run cancelled" : error.path("message").asString());
    }

    @Transactional
    public void failRun(String runId, long jobId, String leaseToken, JsonNode error) {
        /**
         * Persist the failure and release the queue lease in the same transaction.
         * The job becomes eligible again only after the failure is processed.
         * 
         * @param runId the id of the run
         * @param jobId the id of the queue job
         * @param leaseToken the lease token of the queue job
         * @param error the error that caused the failure
         */
        RunEntity run = lockedRun(runId);
        if (run.isCancelRequested()) {
            run.cancel();
        } else {
            run.fail(error);
        }
        runRepository.save(run);
        queueJobRepository.complete(jobId, leaseToken,
                run.isCancelRequested() ? "Run cancelled" : error.path("message").asString());
    }

    private RunEntity lockedRun(String runId) {
        // Lock the run row for update to prevent concurrent modifications. 
        // This ensures that only one worker can modify the run at a time,
        // preventing race conditions and ensuring data consistency.
        return runRepository.findByIdForUpdate(runId).orElseThrow(
            () -> new RunNotFoundException(runId));
    }

    private long tokenCount(StepEntity step) {
        // Return the total number of tokens used by the step, 
        // including both prompt and completion tokens.
        return (long) (step.getTokensPrompt() == null ? 0 : step.getTokensPrompt())
                + (step.getTokensCompletion() == null ? 0 : step.getTokensCompletion());
    }
}
