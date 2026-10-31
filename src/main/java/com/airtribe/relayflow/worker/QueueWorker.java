package com.airtribe.relayflow.worker;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.airtribe.relayflow.common.repository.QueueJobRepository;
import com.airtribe.relayflow.worker.dto.ClaimedQueueJob;
import com.airtribe.relayflow.worker.service.WorkerExecutionService;

@Component
@ConditionalOnProperty(prefix = "relay.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QueueWorker {
    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);
    private final QueueJobRepository queue;
    private final WorkerExecutionService execution;
    private final String workerId;
    private final int leaseSeconds;

    public QueueWorker(QueueJobRepository queue, WorkerExecutionService execution,
            @Value("${relay.worker.id:relay-worker}") String workerId,
            @Value("${relay.worker.lease-seconds:60}") int leaseSeconds) {
        this.queue = queue; 
        this.execution = execution; 
        this.workerId = workerId; 
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${relay.worker.poll-interval-ms:1000}")
    public void poll() {
        Optional<ClaimedQueueJob> claimed;
        try { 
            claimed = queue.claimNext(workerId, leaseSeconds); 
        } catch (RuntimeException ex) { 
            log.error("Unable to claim Relay queue job", ex);
            return;
        }

        claimed.ifPresent(job -> {
            try { 
                execution.execute(job); 
            } catch (RuntimeException ex) {
                // Do not clear a failed claim; lease expiry enables safe reclaim.
                log.error("Worker failed for run {} (job {})", job.runId(), job.jobId(), ex);
            }
        });
    }
}