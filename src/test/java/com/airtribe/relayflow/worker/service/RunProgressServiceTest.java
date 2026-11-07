package com.airtribe.relayflow.worker.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airtribe.relayflow.common.entity.RunEntity;
import com.airtribe.relayflow.common.entity.StepEntity;
import com.airtribe.relayflow.common.repository.ApprovalRepository;
import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.common.repository.RunRepository;
import com.airtribe.relayflow.common.repository.StepRepository;

import tools.jackson.databind.ObjectMapper;

class RunProgressServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cancellationBeforeExecutionMarksPreparedStepCancelled() {
        RunRepository runs = mock(RunRepository.class);
        StepRepository steps = mock(StepRepository.class);
        QueueJobRepository queue = mock(QueueJobRepository.class);
        ApprovalRepository approvals = mock(ApprovalRepository.class);
        RunEntity run = new RunEntity("run_1", "wf_test", mapper.createObjectNode(),
            "manual", mapper.createObjectNode(), "send");
        run.start();
        run.requestCancellation();
        StepEntity step = new StepEntity("run_1", "send", "notify", 1,
            mapper.createObjectNode(), "run_1:send:1");
        when(runs.findByIdForUpdate("run_1")).thenReturn(Optional.of(run));
        RunProgressService service = new RunProgressService(runs, steps, queue, approvals);

        assertFalse(service.prepareStepForExecution("run_1", 9L, "worker:lease", step));

        assertEquals("cancelled", run.getStatus());
        assertEquals("cancelled", step.getStatus());
        verify(steps).save(step);
        verify(runs).save(run);
        verify(queue).complete(9L, "worker:lease", "Run cancelled");
    }
}
