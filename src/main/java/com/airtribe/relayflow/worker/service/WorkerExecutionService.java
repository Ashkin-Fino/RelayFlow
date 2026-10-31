package com.airtribe.relayflow.worker.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.airtribe.relayflow.catalog.NodeCatalog;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.RunNotFoundException;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;
import com.airtribe.relayflow.worker.dto.ClaimedQueueJob;
import com.airtribe.relayflow.worker.dto.NodeExecutionResult;
import com.airtribe.relayflow.worker.excutor.DeterministicNodeExecutor;
import com.airtribe.relayflow.worker.excutor.TemplateResolver;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class WorkerExecutionService {
    private final RunRepository runs;
    private final StepRepository steps;
    private final QueueJobRepository queue;
    private final RunProgressService progress;
    private final DeterministicNodeExecutor executor;
    private final TemplateResolver templates;
    private final NodeCatalog catalog;
    private final ObjectMapper mapper;

    public WorkerExecutionService(RunRepository runs, StepRepository steps, QueueJobRepository queue,
            RunProgressService progress, DeterministicNodeExecutor executor, TemplateResolver templates,
            NodeCatalog catalog, ObjectMapper mapper) {
        this.runs = runs;
        this.steps = steps;
        this.queue = queue;
        this.progress = progress;
        this.executor = executor;
        this.templates = templates;
        this.catalog = catalog;
        this.mapper = mapper;
    }

    /** One node per queue claim: persist progress, then release or delay the job. */
    public void execute(ClaimedQueueJob job) {
        /**
         * Executes a claimed queue job by processing the associated run and its current node.
        */

        // Get the run entity of the claimed job, or fail the claim if it doesn't exist
        RunEntity run = runs.findById(job.runId()).orElseThrow(() -> new RunNotFoundException(job.runId()));
        
        if (terminal(run.getStatus())) {
            queue.complete(job.jobId(), job.leaseToken(), "Run is already terminal"); 
            return;
        }

        if ("waiting_approval".equals(run.getStatus())) {
            queue.reschedule(job.jobId(), job.leaseToken(), 30); 
            return;
        }

        if ("queued".equals(run.getStatus())) {
            run = progress.start(run.getId());
        } else if (!"running".equals(run.getStatus())) {
            failRun(job, "invalid_run_state", "Unexpected run state: " + run.getStatus());
            return;
        }

        int sequence = run.getStepsExecuted() + 1;
        int maxSteps = run.getDefinitionSnapshot().path("limits")
            .path("max_steps")
            .asInt(0);
        if (maxSteps < 1 || sequence > maxSteps) {
            failRun(job, "max_steps_exceeded", "Workflow exceeded max_steps=" + maxSteps);
            return;
        }

        //Get the current node's definition from the run snapshot, or fail the claim if it doesn't exist
        JsonNode node = findNode(run.getDefinitionSnapshot().path("nodes"), run.getCurrentNodeId());
        if (node == null) {
            failRun(job, "current_node_not_found", "Node not found in run snapshot: " + run.getCurrentNodeId()); 
            return;
        }
        String nodeId = node.path("id").asString(), nodeType = node.path("type").asString();
        Map<String, JsonNode> outputs = new LinkedHashMap<>();
        steps.findByRunIdOrderBySequenceAsc(run.getId()).stream()
            .filter(s -> "succeeded".equals(s.getStatus()) && s.getOutput() != null)
            .forEach(s -> outputs.put(s.getNodeId(), s.getOutput()));

        // Resolve the node's parameters using the run's trigger input and previous step outputs, 
        // or fail the step if resolution fails
        JsonNode resolved;
        try {
            resolved = templates.resolve(node.path("params"), run.getTriggerInput(), outputs);
        } catch (RuntimeException ex) {
            ObjectNode err = error("template_resolution_failed", ex);
            StepEntity failed = new StepEntity(run.getId(), nodeId, nodeType, sequence,
                node.path("params").deepCopy(), idempotencyKey(run.getId(), nodeId, sequence, nodeType));
            failed.fail(err, 0);
            progress.failStep(run.getId(), job.jobId(), job.leaseToken(), failed, err);
            return;
        }

        StepEntity step = new StepEntity(run.getId(), nodeId, nodeType, sequence, resolved.deepCopy(),
            idempotencyKey(run.getId(), nodeId, sequence, nodeType));
        long started = System.nanoTime();
        try {
            NodeExecutionResult result = executor.execute(run.getId(), sequence, node, resolved);
            step.succeed(result.output(), (System.nanoTime() - started) / 1_000_000);
            progress.completeStep(run.getId(), job.jobId(), job.leaseToken(),
                step, result.nextNodeId(), result.delaySeconds());
        } catch (RuntimeException ex) {
            step.fail(error("node_execution_failed", ex), (System.nanoTime() - started) / 1_000_000);
            progress.failStep(run.getId(), job.jobId(), job.leaseToken(), step, error("node_execution_failed", ex));
        }
    }

    private JsonNode findNode(JsonNode nodes, String id) {
        /**
         * Finds a node by its ID in a list of nodes.
         *
         * @param nodes The list of nodes to search through.
         * @param id The ID of the node to find.
         * @return The node if found, otherwise null.
        */
        if (id == null || !nodes.isArray()) {
            return null;
        }

        for (JsonNode n : nodes) {
            if (id.equals(n.path("id").asString())) return n;
        }

        return null;
    }

    private String idempotencyKey(String runId, String nodeId, int sequence, String type) {
        /**
         * Generates an idempotency key for a step based on the run ID, node ID, 
         * sequence number, and node type.
         *
         * @param runId The ID of the run.
         * @param nodeId The ID of the node.
         * @param sequence The sequence number of the step.
         * @param type The type of the node.
         * @return A string representing the idempotency key, or null if the 
         *         node type is not a side effect.
        */
        for (JsonNode n : catalog.definition().path("nodes")) {
            if (type.equals(n.path("type").asString()) && n.path("side_effect").asBoolean(false)) {
                return runId + ":" + nodeId + ":" + sequence;
            }
        }
        return null;
    }

    private void failRun(ClaimedQueueJob job, String code, String message) {
        // Fail the run and complete the queue job with an error message.
        progress.failRun(job.runId(), job.jobId(), job.leaseToken(),
            mapper.createObjectNode().put("code", code).put("message", message));
    }

    private ObjectNode error(String code, RuntimeException ex) {
        // Create an error object with a code and message based on the exception.
        String msg = ex.getMessage() == null || ex.getMessage().isBlank()
            ? ex.getClass().getSimpleName() : ex.getMessage();
        return mapper.createObjectNode().put("code", code).put("message", msg);
    }

    private boolean terminal(String s) {
        // Check if the run status is terminal (succeeded, failed, or cancelled).
        return "succeeded".equals(s) || "failed".equals(s) || "cancelled".equals(s);
    }
}