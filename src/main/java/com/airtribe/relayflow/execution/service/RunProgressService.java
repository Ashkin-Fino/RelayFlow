package com.airtribe.relayflow.execution.service;

import com.airtribe.relayflow.execution.entity.RunEntity;
import com.airtribe.relayflow.execution.entity.StepEntity;
import com.airtribe.relayflow.execution.repository.QueueJobRepository;
import com.airtribe.relayflow.execution.repository.RunRepository;
import com.airtribe.relayflow.execution.repository.StepRepository;
import com.airtribe.relayflow.run.exceptions.RunNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
			// Release after every node; zero makes the next node immediately eligible.
			queueJobRepository.reschedule(jobId, leaseToken, delaySeconds);
		}
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