package com.santhosh.dashboard.repository;

import com.santhosh.dashboard.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findTop10ByOrderByTimestampDesc();
}
