package com.airtribe.relayflow.worker.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airtribe.relayflow.catalog.NodeCatalog;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;
import com.airtribe.relayflow.worker.dto.ClaimedQueueJob;
import com.airtribe.relayflow.worker.dto.NodeExecutionResult;
import com.airtribe.relayflow.worker.exception.NodeExecutionException;
import com.airtribe.relayflow.worker.excutor.DeterministicNodeExecutor;
import com.airtribe.relayflow.worker.excutor.TemplateResolver;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WorkerExecutionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void executesOneNodeAndPersistsItsTransition() {
        TestDependencies d = dependencies();
        when(d.executor.execute(eq("run_1"), eq(1), any(JsonNode.class), any(JsonNode.class)))
            .thenReturn(NodeExecutionResult.next(mapper.readTree("{\"notification_id\":\"n1\"}"), null));

        d.service.execute(d.job);

        verify(d.progress).prepareStep(any(StepEntity.class));
        verify(d.progress).completeStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), isNull(), eq(0L));
    }

    @Test
    void transientFailureIsPersistedAndRescheduledWithBackoff() {
        TestDependencies d = dependencies();
        when(d.executor.execute(eq("run_1"), eq(1), any(JsonNode.class), any(JsonNode.class)))
            .thenThrow(new NodeExecutionException("connection refused", true));

        d.service.execute(d.job);

        verify(d.progress).prepareStep(any(StepEntity.class));
        verify(d.progress).retryStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), any(JsonNode.class), anyLong(), eq(2L));
        verify(d.progress, never()).failStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), any(JsonNode.class));
    }

    @Test
    void secondTransientFailureUsesDoubledBackoff() {
        TestDependencies d = dependencies();
        StepEntity step = new StepEntity("run_1", "send", "notify", 1,
            mapper.readTree("{\"channel\":\"chat\",\"to\":\"#ops\",\"message\":\"hello\"}"),
            "run_1:send:1");
        step.waitForRetry(mapper.createObjectNode().put("message", "first failure"), 10);
        step.beginRetry();
        when(d.steps.findByRunIdAndSequence("run_1", 1)).thenReturn(Optional.of(step));
        when(d.executor.execute(eq("run_1"), eq(1), any(JsonNode.class), any(JsonNode.class)))
            .thenThrow(new NodeExecutionException("service unavailable", true, 503));

        d.service.execute(d.job);

        verify(d.progress).retryStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), any(JsonNode.class), anyLong(), eq(4L));
    }

    @Test
    void permanentFailureIsNotRetried() {
        TestDependencies d = dependencies();
        when(d.executor.execute(eq("run_1"), eq(1), any(JsonNode.class), any(JsonNode.class)))
            .thenThrow(new NodeExecutionException("HTTP request returned status 400", false, 400));

        d.service.execute(d.job);

        verify(d.progress).failStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), any(JsonNode.class));
        verify(d.progress, never()).retryStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), any(JsonNode.class), anyLong(), anyLong());
    }

    @Test
    void retryExhaustionFailsTheRunInsteadOfSchedulingAnotherAttempt() {
        TestDependencies d = dependencies();
        StepEntity step = new StepEntity("run_1", "send", "notify", 1,
            mapper.readTree("{\"channel\":\"chat\",\"to\":\"#ops\",\"message\":\"hello\"}"),
            "run_1:send:1");
        step.waitForRetry(mapper.createObjectNode().put("message", "first failure"), 10);
        step.beginRetry();
        step.waitForRetry(mapper.createObjectNode().put("message", "second failure"), 10);
        step.beginRetry();
        when(d.steps.findByRunIdAndSequence("run_1", 1)).thenReturn(Optional.of(step));
        when(d.executor.execute(eq("run_1"), eq(1), any(JsonNode.class), any(JsonNode.class)))
            .thenThrow(new NodeExecutionException("service unavailable", true, 503));

        d.service.execute(d.job);

        verify(d.progress).failStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), any(JsonNode.class));
        verify(d.progress, never()).retryStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), any(JsonNode.class), anyLong(), anyLong());
    }

    @Test
    void sensitiveNodeCannotExecuteWithoutEarlierApprovedApprovalStep() {
        RunRepository runs = mock(RunRepository.class);
        StepRepository steps = mock(StepRepository.class);
        QueueJobRepository queue = mock(QueueJobRepository.class);
        RunProgressService progress = mock(RunProgressService.class);
        DeterministicNodeExecutor executor = mock(DeterministicNodeExecutor.class);
        NodeCatalog catalog = mock(NodeCatalog.class);
        JsonNode definition = mapper.readTree("""
            {"limits":{"max_steps":3},
             "nodes":[{"id":"refund","type":"order_action","params":{"action":"refund"},"next":null}]}
            """);
        RunEntity run = new RunEntity("run_1", "wf_test", definition, "manual",
            mapper.createObjectNode(), "refund");
        when(runs.findById("run_1")).thenReturn(Optional.of(run));
        when(progress.start("run_1")).thenAnswer(i -> {
            run.start();
            return run;
        });
        when(steps.findByRunIdOrderBySequenceAsc("run_1")).thenReturn(List.of());
        when(catalog.definition()).thenReturn(mapper.readTree("""
            {"nodes":[{"type":"order_action","requires_approval":true}]}
            """));
        WorkerExecutionService service = new WorkerExecutionService(runs, steps, queue, progress,
            executor, new TemplateResolver(mapper), catalog, mapper, 3, 2, 30);

        service.execute(new ClaimedQueueJob(9L, "run_1", "worker:lease"));

        verify(progress).failRun(eq("run_1"), eq(9L), eq("worker:lease"),
            argThat(error -> "approval_required".equals(error.path("code").asString())));
        verify(executor, never()).execute(anyString(), anyInt(), any(), any());
    }

    @Test
    void sensitiveNodeRequiresApprovalOutputToExplicitlySayApproved() {
        RunRepository runs = mock(RunRepository.class);
        StepRepository steps = mock(StepRepository.class);
        QueueJobRepository queue = mock(QueueJobRepository.class);
        RunProgressService progress = mock(RunProgressService.class);
        DeterministicNodeExecutor executor = mock(DeterministicNodeExecutor.class);
        NodeCatalog catalog = mock(NodeCatalog.class);
        JsonNode definition = mapper.readTree("""
            {"limits":{"max_steps":3},
             "nodes":[{"id":"refund","type":"order_action","params":{"action":"refund"},"next":null}]}
            """);
        RunEntity run = new RunEntity("run_1", "wf_test", definition, "manual",
            mapper.createObjectNode(), "refund");
        run.advance("refund");
        StepEntity approval = new StepEntity("run_1", "manager_gate", "approval", 1,
            mapper.createObjectNode(), null);
        approval.succeed(mapper.createObjectNode().put("decision", "approved"), 1);
        when(runs.findById("run_1")).thenReturn(Optional.of(run));
        when(progress.start("run_1")).thenAnswer(i -> {
            run.start();
            return run;
        });
        when(steps.findByRunIdOrderBySequenceAsc("run_1")).thenReturn(List.of(approval));
        when(steps.findByRunIdAndSequence("run_1", 2)).thenReturn(Optional.empty());
        when(progress.prepareStep(any(StepEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(catalog.definition()).thenReturn(mapper.readTree("""
            {"nodes":[{"type":"order_action","side_effect":true,"requires_approval":true}]}
            """));
        when(executor.execute(eq("run_1"), eq(2), any(JsonNode.class), any(JsonNode.class)))
            .thenReturn(NodeExecutionResult.next(mapper.createObjectNode().put("status", "refunded"), null));
        WorkerExecutionService service = new WorkerExecutionService(runs, steps, queue, progress,
            executor, new TemplateResolver(mapper), catalog, mapper, 3, 2, 30);

        service.execute(new ClaimedQueueJob(9L, "run_1", "worker:lease"));

        verify(executor).execute(eq("run_1"), eq(2), any(JsonNode.class), any(JsonNode.class));
        verify(progress).completeStep(eq("run_1"), eq(9L), eq("worker:lease"),
            any(StepEntity.class), isNull(), eq(0L));
    }

    private TestDependencies dependencies() {
        RunRepository runs = mock(RunRepository.class);
        StepRepository steps = mock(StepRepository.class);
        QueueJobRepository queue = mock(QueueJobRepository.class);
        RunProgressService progress = mock(RunProgressService.class);
        DeterministicNodeExecutor executor = mock(DeterministicNodeExecutor.class);
        NodeCatalog catalog = mock(NodeCatalog.class);

        JsonNode snapshot = mapper.readTree("""
          {"limits":{"max_steps":3},"nodes":[{"id":"send","type":"notify","params":{"channel":"chat","to":"#ops","message":"hello"},"next":null}]}
          """);
        RunEntity run = new RunEntity("run_1", "wf_test", snapshot, "manual",
            mapper.readTree("{\"message\":\"hello\"}"), "send");
        when(runs.findById("run_1")).thenReturn(Optional.of(run));
        when(progress.start("run_1")).thenAnswer(i -> {
            run.start();
            return run;
        });
        when(steps.findByRunIdOrderBySequenceAsc("run_1")).thenReturn(List.of());
        when(steps.findByRunIdAndSequence("run_1", 1)).thenReturn(Optional.empty());
        when(progress.prepareStep(any(StepEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(catalog.definition()).thenReturn(mapper.readTree(
            "{\"nodes\":[{\"type\":\"notify\",\"side_effect\":true}]}"));

        WorkerExecutionService service = new WorkerExecutionService(runs, steps, queue, progress,
            executor, new TemplateResolver(mapper), catalog, mapper, 3, 2, 30);
        return new TestDependencies(service, runs, steps, progress, executor,
            new ClaimedQueueJob(9L, "run_1", "worker:lease"));
    }

    private record TestDependencies(WorkerExecutionService service, RunRepository runs,
            StepRepository steps, RunProgressService progress, DeterministicNodeExecutor executor,
            ClaimedQueueJob job) {
    }
}
