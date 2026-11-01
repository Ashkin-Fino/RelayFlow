package com.airtribe.relayflow.common.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.airtribe.relayflow.common.entity.ApprovalEntity;

import jakarta.persistence.LockModeType;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
	List<ApprovalEntity> findByStatusOrderByCreatedAtAsc(String status);

	List<ApprovalEntity> findByRunIdAndStatus(String runId, String status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from ApprovalEntity a where a.id = :id")
	Optional<ApprovalEntity> findByIdForUpdate(@Param("id") String id);
}
