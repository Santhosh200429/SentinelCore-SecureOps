package com.santhosh.dashboard.repository;

import com.santhosh.dashboard.model.ReportSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {
    List<ReportSchedule> findByActiveTrueOrderByLocalTimeAsc();
}
