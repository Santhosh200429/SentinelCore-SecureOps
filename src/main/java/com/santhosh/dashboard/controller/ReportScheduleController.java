package com.santhosh.dashboard.controller;

import com.santhosh.dashboard.model.ReportSchedule;
import com.santhosh.dashboard.repository.ReportScheduleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report-schedules")
public class ReportScheduleController {
    private final ReportScheduleRepository repository;

    public ReportScheduleController(ReportScheduleRepository repository) { this.repository = repository; }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public List<ReportSchedule> list() { return repository.findByActiveTrueOrderByLocalTimeAsc(); }

    @PostMapping
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<?> create(@RequestBody Map<String,Object> body) {
        try {
            String type = text(body.get("reportType"));
            String frequency = text(body.get("frequency"));
            String time = text(body.get("localTime"));
            String recipient = text(body.get("recipient"));
            if (type == null || frequency == null || time == null || recipient == null) return ResponseEntity.badRequest().body(Map.of("message","reportType, frequency, localTime and recipient are required"));
            ReportSchedule s = new ReportSchedule();
            s.setReportType(type);
            s.setFrequency(ReportSchedule.Frequency.valueOf(frequency.toUpperCase()));
            s.setLocalTime(LocalTime.parse(time));
            s.setRecipient(recipient.trim());
            if (s.getFrequency() == ReportSchedule.Frequency.WEEKLY) s.setDayOfWeek(body.get("dayOfWeek") == null ? 1 : Integer.parseInt(String.valueOf(body.get("dayOfWeek"))));
            if (s.getFrequency() == ReportSchedule.Frequency.MONTHLY) s.setDayOfMonth(body.get("dayOfMonth") == null ? 1 : Integer.parseInt(String.valueOf(body.get("dayOfMonth"))));
            return ResponseEntity.ok(repository.save(s));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("message","Invalid schedule: " + e.getMessage())); }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private static String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
}
