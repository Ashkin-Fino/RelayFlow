package com.airtribe.relayflow.common.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.airtribe.relayflow.common.entity.StepEntity;

public interface StepRepository extends JpaRepository<StepEntity, Long> {
    List<StepEntity> findByRunIdOrderBySequenceAsc(String runId);

    Optional<StepEntity> findByRunIdAndSequence(String runId, int sequence);
}
