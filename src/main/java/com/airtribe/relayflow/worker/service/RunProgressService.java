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
		/**
		 * Starts a run by retrieving the run entity from the repository, 
		 * marking it as started, and saving it back to the repository.
		 * 
		 * @param runId The ID of the run to be started.
		 * @return The updated run entity after being marked as started.
		 * @throws RunNotFoundException if the run with the specified ID does not exist.
		 */
		RunEntity run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
		run.start();
		return runRepository.save(run);
	}

	@Transactional
	public void completeStep(String runId, long jobId, String leaseToken, StepEntity step,
			String nextNodeId, long delaySeconds) {
		/**
		 * Completes a step in a run by saving the step, advancing the run 
		 * to the next node, and handling the run's status.
		 * 
		 * @param runId The ID of the run.
		 * @param jobId The ID of the queue job associated with the step.
		 * @param leaseToken The lease token for the queue job.
		 * @param step The step entity to be completed.
		 * @param nextNodeId The ID of the next node to proceed to after this step
		 * @param delaySeconds The number of seconds to delay the next node.
		 */
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
			// Release after every node; zero makes the next node immediately eligible.
			queueJobRepository.reschedule(jobId, leaseToken, delaySeconds);
		}
	}

	@Transactional
	public void failStep(String runId, long jobId, String leaseToken, StepEntity step, JsonNode error) {
		/**
		 * Fails a step in a run by saving the step, marking the run as failed, 
		 * and completing the queue job with an error message.
		 * 
		 * @param runId The ID of the run.
		 * @param jobId The ID of the queue job associated with the step.
		 * @param leaseToken The lease token for the queue job.
		 * @param step The step entity to be marked as failed.
		 * @param error The error details associated with the failure.
		 * @throws RunNotFoundException if the run with the specified ID does not exist.
		 */
		RunEntity run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
		stepRepository.save(step);
		run.fail(error);
		runRepository.save(run);
		queueJobRepository.complete(jobId, leaseToken, error.path("message").asString());
	}

	@Transactional
	public void failRun(String runId, long jobId, String leaseToken, JsonNode error) {
		/**
		 * Fails an entire run by marking it as failed and completing the queue 
		 * job with an error message.
		 * 
		 * @param runId The ID of the run to be marked as failed.
		 * @param jobId The ID of the queue job associated with the run.
		 * @param leaseToken The lease token for the queue job.
		 * @param error The error details associated with the failure.
		 * @throws RunNotFoundException if the run with the specified ID does not exist.
		 */
		RunEntity run = runRepository.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
		run.fail(error);
		runRepository.save(run);
		queueJobRepository.complete(jobId, leaseToken, error.path("message").asString());
	}
}