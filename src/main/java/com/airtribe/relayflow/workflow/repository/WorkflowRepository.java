package com.airtribe.relayflow.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.airtribe.relayflow.workflow.entity.WorkflowEntity;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {
}