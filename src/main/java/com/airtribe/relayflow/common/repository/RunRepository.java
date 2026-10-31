package com.airtribe.relayflow.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.airtribe.relayflow.common.entity.RunEntity;

public interface RunRepository extends JpaRepository<RunEntity, String> {
}