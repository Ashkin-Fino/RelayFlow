package com.airtribe.relayflow.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.airtribe.relayflow.api.workflow.service.WorkflowScheduleService;

@Component
public class WorkflowSchedulePoller {
	private static final Logger log = LoggerFactory.getLogger(WorkflowSchedulePoller.class);
	private final WorkflowScheduleService workflowScheduleService;

	public WorkflowSchedulePoller(WorkflowScheduleService workflowScheduleService) {
		this.workflowScheduleService = workflowScheduleService;
	}

	@Scheduled(fixedDelayString = "${relay.schedule.poll-interval-ms:1000}")
	public void poll() {
		try {
			workflowScheduleService.dispatchDueSchedules();
		} catch (RuntimeException exception) {
			log.error("Unable to dispatch due workflow schedules", exception);
		}
	}
}
