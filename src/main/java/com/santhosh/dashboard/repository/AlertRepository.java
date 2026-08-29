package com.santhosh.dashboard.repository;

import com.santhosh.dashboard.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findTop10ByOrderByTimestampDesc();

    boolean existsByTitleAndSourceAndTimestampAfter(String title, String source, LocalDateTime since);

    default boolean existsRecentAlert(String title, String source, LocalDateTime since) {
        return existsByTitleAndSourceAndTimestampAfter(title, source, since);
    }
}
