package com.airtribe.relayflow.run.service;

import com.airtribe.relayflow.run.dto.RunAcceptedResponse;
import com.airtribe.relayflow.run.entity.RunEntity;
import com.airtribe.relayflow.run.exceptions.InvalidTriggerTypeException;
import com.airtribe.relayflow.run.exceptions.InvalidTriggerInputException;
import com.airtribe.relayflow.run.exceptions.WebhookUnauthorizedException;
import com.airtribe.relayflow.run.exceptions.WorkflowNotPublishedException;
import com.airtribe.relayflow.run.repository.QueueJobRepository;
import com.airtribe.relayflow.run.repository.RunRepository;
import com.airtribe.relayflow.workflow.entity.WorkflowEntity;
import com.airtribe.relayflow.workflow.exceptions.WorkflowNotFoundException;
import com.airtribe.relayflow.workflow.repository.WorkflowRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class RunTriggerService {
	private final WorkflowRepository workflowRepository;
	private final RunRepository runRepository;
	private final QueueJobRepository queueJobRepository;

	public RunTriggerService(WorkflowRepository workflowRepository, RunRepository runRepository,
			QueueJobRepository queueJobRepository) {
		this.workflowRepository = workflowRepository;
		this.runRepository = runRepository;
		this.queueJobRepository = queueJobRepository;
	}

	@Transactional
	public RunAcceptedResponse triggerManual(String workflowId, JsonNode request) {
		WorkflowEntity workflow = publishedWorkflow(workflowId);
		if (!"manual".equals(triggerType(workflow))) {
			throw new InvalidTriggerTypeException("manual");
		}
		JsonNode input = request == null ? null : request.path("input");
		if (input == null || !input.isObject()) {
			throw new InvalidTriggerInputException("Manual trigger body must contain an object field 'input'");
		}
		return enqueue(workflow, "manual", input);
	}

	@Transactional
	public RunAcceptedResponse triggerWebhook(String workflowId, String providedSecret, JsonNode body) {
		WorkflowEntity workflow = publishedWorkflow(workflowId);
		if (!"webhook".equals(triggerType(workflow))) {
			throw new InvalidTriggerTypeException("webhook");
		}
		String expectedSecret = workflow.getDefinition().path("trigger").path("secret").asString("");
		if (!secretMatches(expectedSecret, providedSecret)) {
			throw new WebhookUnauthorizedException();
		}
		if (body == null || !body.isObject()) {
			throw new InvalidTriggerInputException("Webhook body must be a JSON object");
		}
		return enqueue(workflow, "webhook", body);
	}

	private WorkflowEntity publishedWorkflow(String workflowId) {
		WorkflowEntity workflow = workflowRepository.findById(workflowId)
				.orElseThrow(() -> new WorkflowNotFoundException(workflowId));
		if (!"published".equals(workflow.getStatus())) {
			throw new WorkflowNotPublishedException(workflowId);
		}
		return workflow;
	}

	private String triggerType(WorkflowEntity workflow) {
		return workflow.getDefinition().path("trigger").path("type").asString();
	}

	private boolean secretMatches(String expected, String provided) {
		if (expected.isEmpty() || provided == null || provided.isEmpty()) {
			return false;
		}
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				provided.getBytes(StandardCharsets.UTF_8));
	}

	private RunAcceptedResponse enqueue(WorkflowEntity workflow, String triggerType, JsonNode input) {
		String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
		String entry = workflow.getDefinition().path("entry").asString();
		RunEntity run = new RunEntity(runId, workflow.getId(), workflow.getDefinition().deepCopy(),
				triggerType, input.deepCopy(), entry);
		runRepository.save(run);
		queueJobRepository.enqueue(runId);
		return new RunAcceptedResponse(runId);
	}
}