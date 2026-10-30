package com.airtribe.relayflow.run.repository;

import com.airtribe.relayflow.run.entity.StepEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepRepository extends JpaRepository<StepEntity, Long> {
	List<StepEntity> findByRunIdOrderBySequenceAsc(String runId);
}