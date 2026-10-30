package com.airtribe.relayflow.execution.repository;

import com.airtribe.relayflow.execution.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<RunEntity, String> {
}