package com.airtribe.relayflow.worker.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.airtribe.relayflow.catalog.NodeCatalog;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.exception.NodeExecutionException;
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
    private final int maxAttempts;
    private final long initialRetryDelaySeconds;
    private final long maxRetryDelaySeconds;

    public WorkerExecutionService(RunRepository runs, StepRepository steps, QueueJobRepository queue,
            RunProgressService progress, DeterministicNodeExecutor executor, TemplateResolver templates,
            NodeCatalog catalog, ObjectMapper mapper,
            @Value("${relay.worker.retry.max-attempts:3}") int maxAttempts,
            @Value("${relay.worker.retry.initial-delay-seconds:2}") long initialRetryDelaySeconds,
            @Value("${relay.worker.retry.max-delay-seconds:30}") long maxRetryDelaySeconds) {
        this.runs = runs;
        this.steps = steps;
        this.queue = queue;
        this.progress = progress;
        this.executor = executor;
        this.templates = templates;
        this.catalog = catalog;
        this.mapper = mapper;
        if (maxAttempts < 1 || initialRetryDelaySeconds < 0 || maxRetryDelaySeconds < initialRetryDelaySeconds) {
            throw new IllegalArgumentException("Invalid worker retry configuration");
        }
        this.maxAttempts = maxAttempts;
        this.initialRetryDelaySeconds = initialRetryDelaySeconds;
        this.maxRetryDelaySeconds = maxRetryDelaySeconds;
    }

    /** One node per queue claim: persist progress, then release or delay the job. */
    public void execute(ClaimedQueueJob job) {
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
        int maxSteps = run.getDefinitionSnapshot().path("limits").path("max_steps").asInt(0);
        if (maxSteps < 1 || sequence > maxSteps) {
            failRun(job, "max_steps_exceeded", "Workflow exceeded max_steps=" + maxSteps);
            return;
        }

        JsonNode node = findNode(run.getDefinitionSnapshot().path("nodes"), run.getCurrentNodeId());
        if (node == null) {
            failRun(job, "current_node_not_found", "Node not found in run snapshot: " + run.getCurrentNodeId());
            return;
        }

        String nodeId = node.path("id").asString();
        String nodeType = node.path("type").asString();
        var priorSteps = steps.findByRunIdOrderBySequenceAsc(run.getId());
        if (requiresApproval(nodeType) && !hasPriorApproval(priorSteps, sequence)) {
            failRun(job, "approval_required",
                "Node " + nodeId + " requires an earlier approved approval step");
            return;
        }
        Map<String, JsonNode> outputs = new LinkedHashMap<>();
        priorSteps.stream()
            .filter(s -> "succeeded".equals(s.getStatus()) && s.getOutput() != null)
            .forEach(s -> outputs.put(s.getNodeId(), s.getOutput()));

        StepEntity step = steps.findByRunIdAndSequence(run.getId(), sequence).orElse(null);
        JsonNode resolved;
        if (step != null) {
            // The resolved input and idempotency key are immutable for this logical
            // execution, including retries and lease-based crash recovery.
            resolved = step.getResolvedInput();
            if ("waiting".equals(step.getStatus())) {
                step.beginRetry();
            } else if (!"running".equals(step.getStatus())) {
                failRun(job, "invalid_step_state",
                    "Cannot resume node " + nodeId + " from step status " + step.getStatus());
                return;
            }
        } else {
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
            step = new StepEntity(run.getId(), nodeId, nodeType, sequence, resolved.deepCopy(),
                idempotencyKey(run.getId(), nodeId, sequence, nodeType));
        }

        // Persist the running attempt before making a potentially non-idempotent
        // external call. If the process dies, the lease reclaim reuses this attempt.
        step = progress.prepareStep(step);

        long started = System.nanoTime();
        try {
            NodeExecutionResult result = executor.execute(run.getId(), sequence, node, resolved);
            step.succeed(result.output(), (System.nanoTime() - started) / 1_000_000,
                result.tokensPrompt(), result.tokensCompletion());
            progress.completeStep(run.getId(), job.jobId(), job.leaseToken(),
                step, result.nextNodeId(), result.delaySeconds());
        } catch (RuntimeException ex) {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            NodeExecutionException classified = findNodeExecutionException(ex);
            boolean retryable = classified != null && classified.isRetryable();
            ObjectNode err = error("node_execution_failed", ex)
                .put("attempt", step.getAttempt())
                .put("max_attempts", maxAttempts)
                .put("retryable", retryable);
            if (classified != null && classified.getHttpStatus() != null) {
                err.put("http_status", classified.getHttpStatus());
            }
            if (classified != null) {
                step.addTokenUsage(classified.getTokensPrompt(), classified.getTokensCompletion());
            }

            if (retryable && step.getAttempt() < maxAttempts) {
                long delay = retryDelaySeconds(step.getAttempt());
                progress.retryStep(run.getId(), job.jobId(), job.leaseToken(), step, err, durationMs, delay);
                return;
            }

            if (retryable) {
                err.put("retry_exhausted", true);
            }
            step.fail(err, durationMs);
            progress.failStep(run.getId(), job.jobId(), job.leaseToken(), step, err);
        }
    }

    private long retryDelaySeconds(int failedAttempt) {
        long delay = initialRetryDelaySeconds;
        for (int i = 1; i < failedAttempt && delay < maxRetryDelaySeconds; i++) {
            delay = delay > maxRetryDelaySeconds / 2
                ? maxRetryDelaySeconds : Math.min(maxRetryDelaySeconds, delay * 2);
        }
        return delay;
    }

    private NodeExecutionException findNodeExecutionException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof NodeExecutionException nodeError) {
                return nodeError;
            }
            current = current.getCause();
        }
        return null;
    }

    private JsonNode findNode(JsonNode nodes, String id) {
        if (id == null || !nodes.isArray()) {
            return null;
        }
        for (JsonNode n : nodes) {
            if (id.equals(n.path("id").asString())) {
                return n;
            }
        }
        return null;
    }

    private String idempotencyKey(String runId, String nodeId, int sequence, String type) {
        for (JsonNode n : catalog.definition().path("nodes")) {
            if (type.equals(n.path("type").asString()) && n.path("side_effect").asBoolean(false)) {
                return runId + ":" + nodeId + ":" + sequence;
            }
        }
        return null;
    }

    private boolean requiresApproval(String nodeType) {
        for (JsonNode definition : catalog.definition().path("nodes")) {
            if (nodeType.equals(definition.path("type").asString())) {
                return definition.path("requires_approval").asBoolean(false);
            }
        }
        return false;
    }

    private boolean hasPriorApproval(Iterable<StepEntity> priorSteps, int currentSequence) {
        for (StepEntity step : priorSteps) {
            if (step.getSequence() < currentSequence
                    && "approval".equals(step.getNodeType())
                    && "succeeded".equals(step.getStatus())
                    && "approved".equals(step.getOutput() == null
                            ? null : step.getOutput().path("decision").asString(null))) {
                return true;
            }
        }
        return false;
    }

    private void failRun(ClaimedQueueJob job, String code, String message) {
        progress.failRun(job.runId(), job.jobId(), job.leaseToken(),
            mapper.createObjectNode().put("code", code).put("message", message));
    }

    private ObjectNode error(String code, RuntimeException ex) {
        String msg = ex.getMessage() == null || ex.getMessage().isBlank()
            ? ex.getClass().getSimpleName() : ex.getMessage();
        return mapper.createObjectNode().put("code", code).put("message", msg);
    }

    private boolean terminal(String status) {
        return "succeeded".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }
}
