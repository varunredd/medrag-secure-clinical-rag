package com.medrag.api.metering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsageMeterEventRepository extends JpaRepository<UsageMeterEvent, UUID> {}
