package com.airtribe.relayflow.api.workflow.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.airtribe.relayflow.api.run.service.RunTriggerService;
import com.airtribe.relayflow.common.entity.WorkflowEntity;
import com.airtribe.relayflow.common.repository.WorkflowRepository;
import com.airtribe.relayflow.common.repository.WorkflowScheduleRepository;

import tools.jackson.databind.JsonNode;

@Service
public class WorkflowScheduleService {
	private static final int MAX_SCHEDULES_PER_POLL = 50;

	private final WorkflowScheduleRepository scheduleRepository;
	private final WorkflowRepository workflowRepository;
	private final RunTriggerService runTriggerService;

	public WorkflowScheduleService(WorkflowScheduleRepository scheduleRepository,
			WorkflowRepository workflowRepository, RunTriggerService runTriggerService) {
		this.scheduleRepository = scheduleRepository;
		this.workflowRepository = workflowRepository;
		this.runTriggerService = runTriggerService;
	}

	@Transactional
	public void syncPublishedWorkflow(String workflowId, JsonNode definition) {
		scheduleRepository.delete(workflowId);
		JsonNode trigger = definition.path("trigger");
		if (!"schedule".equals(trigger.path("type").asString())) {
			return;
		}
		String cron = trigger.path("cron").asString();
		scheduleRepository.upsert(workflowId, cron, CronSchedule.nextFireAt(cron, Instant.now()));
	}

	@Transactional
	public void dispatchDueSchedules() {
		List<WorkflowScheduleRepository.DueWorkflowSchedule> dueSchedules =
				scheduleRepository.lockDueSchedules(MAX_SCHEDULES_PER_POLL);
		for (WorkflowScheduleRepository.DueWorkflowSchedule schedule : dueSchedules) {
			WorkflowEntity workflow = workflowRepository.findById(schedule.workflowId()).orElse(null);
			if (workflow == null || !"published".equals(workflow.getStatus())
					|| !"schedule".equals(workflow.getDefinition().path("trigger").path("type").asString())) {
				scheduleRepository.delete(schedule.workflowId());
				continue;
			}
			runTriggerService.triggerScheduled(schedule.workflowId());
			scheduleRepository.advance(schedule.workflowId(),
					CronSchedule.nextFireAt(schedule.cron(), Instant.now()));
		}
	}
}
