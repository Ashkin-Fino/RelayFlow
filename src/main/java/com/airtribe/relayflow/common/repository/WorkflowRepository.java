package com.airtribe.relayflow.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.airtribe.relayflow.common.entity.WorkflowEntity;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {
}