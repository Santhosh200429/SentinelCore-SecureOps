package com.santhosh.dashboard.controller;
import com.santhosh.dashboard.dto.RecentAlertDTO; import com.santhosh.dashboard.model.Alert; import com.santhosh.dashboard.repository.AlertRepository; import com.santhosh.dashboard.service.RealtimeAlertService;
import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import org.springframework.web.servlet.mvc.method.annotation.SseEmitter; import java.util.*;
@RestController @RequestMapping("/api/alerts") public class RealtimeAlertController { private final AlertRepository repo; private final RealtimeAlertService service; public RealtimeAlertController(AlertRepository repo,RealtimeAlertService service){this.repo=repo;this.service=service;}
 @GetMapping("/live") @PreAuthorize("hasAuthority('ASSET_VIEW')") public ResponseEntity<List<RecentAlertDTO>> getLiveAlerts(){return ResponseEntity.ok(repo.findTop10ByOrderByTimestampDesc().stream().map(a->new RecentAlertDTO(String.valueOf(a.getId()),a.getTitle(),a.getSeverity(),a.getSource(),a.getTimestamp())).toList());}
 @GetMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE) @PreAuthorize("hasAuthority('ASSET_VIEW')") public SseEmitter stream(){return service.subscribe();}
}
