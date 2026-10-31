package com.airtribe.relayflow.worker.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airtribe.relayflow.catalog.NodeCatalog;
import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;
import com.airtribe.relayflow.worker.dto.ClaimedQueueJob;
import com.airtribe.relayflow.worker.excutor.DeterministicNodeExecutor;
import com.airtribe.relayflow.worker.excutor.NodeExecutionResult;
import com.airtribe.relayflow.worker.excutor.TemplateResolver;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WorkerExecutionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void executesOneNodeAndPersistsItsTransition() {
        RunRepository runs=mock(RunRepository.class); StepRepository steps=mock(StepRepository.class);
        QueueJobRepository queue=mock(QueueJobRepository.class); RunProgressService progress=mock(RunProgressService.class);
        DeterministicNodeExecutor executor=mock(DeterministicNodeExecutor.class); NodeCatalog catalog=mock(NodeCatalog.class);
        JsonNode snapshot=mapper.readTree("""
          {"limits":{"max_steps":3},"nodes":[{"id":"send","type":"notify","params":{"channel":"chat","to":"#ops","message":"hello"},"next":null}]}
          """);
        RunEntity run=new RunEntity("run_1","wf_test",snapshot,"manual",mapper.readTree("{\"message\":\"hello\"}"),"send");
        when(runs.findById("run_1")).thenReturn(Optional.of(run));
        when(progress.start("run_1")).thenAnswer(i->{run.start();return run;});
        when(steps.findByRunIdOrderBySequenceAsc("run_1")).thenReturn(List.of());
        when(catalog.definition()).thenReturn(mapper.readTree("{\"nodes\":[{\"type\":\"notify\",\"side_effect\":true}]}"));
        when(executor.execute(eq("run_1"),eq(1),any(JsonNode.class),any(JsonNode.class)))
          .thenReturn(NodeExecutionResult.next(mapper.readTree("{\"notification_id\":\"n1\"}"),null));
        new WorkerExecutionService(runs,steps,queue,progress,executor,new TemplateResolver(mapper),catalog,mapper)
          .execute(new ClaimedQueueJob(9L,"run_1","worker:lease"));
        verify(progress).completeStep(eq("run_1"),eq(9L),eq("worker:lease"),any(),isNull(),eq(0L));
    }
}