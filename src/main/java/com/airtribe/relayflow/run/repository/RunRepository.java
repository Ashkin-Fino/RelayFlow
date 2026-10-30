package com.airtribe.relayflow.run.repository;

import com.airtribe.relayflow.run.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<RunEntity, String> {
}