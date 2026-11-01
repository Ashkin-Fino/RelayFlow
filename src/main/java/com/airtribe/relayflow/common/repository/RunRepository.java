package com.airtribe.relayflow.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.airtribe.relayflow.common.entity.RunEntity;

import jakarta.persistence.LockModeType;

public interface RunRepository extends JpaRepository<RunEntity, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RunEntity r where r.id = :id")
	java.util.Optional<RunEntity> findByIdForUpdate(@Param("id") String id);
}