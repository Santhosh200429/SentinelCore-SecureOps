package com.santhosh.dashboard.repository;

import com.santhosh.dashboard.model.SystemTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SystemTelemetryRepository extends JpaRepository<SystemTelemetry, Long> {
    Optional<SystemTelemetry> findTopByAssetIdOrderByTimestampDesc(Long assetId);
    long countByAssetIdAndTimestampAfter(Long assetId, LocalDateTime timestamp);
}
