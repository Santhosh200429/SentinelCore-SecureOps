package com.santhosh.dashboard.controller;

import com.santhosh.dashboard.agent.AgentTelemetryService;
import com.santhosh.dashboard.model.SecurityEvent;
import com.santhosh.dashboard.repository.SecurityEventRepository;
import com.santhosh.dashboard.service.SecurityEventService;
import com.santhosh.dashboard.service.WindowsEventLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/security-events")
public class SecurityEventController {
    private final SecurityEventRepository repo; private final WindowsEventLogService service; private final SecurityEventService eventService;
    public SecurityEventController(SecurityEventRepository repo, WindowsEventLogService service, SecurityEventService eventService){this.repo=repo;this.service=service;this.eventService=eventService;}
    @GetMapping @PreAuthorize("hasAuthority('ASSET_VIEW')") public List<SecurityEvent> all(org.springframework.security.core.Authentication auth){return eventService.recent(auth.getName());}
    @PostMapping("/collect-windows") @PreAuthorize("hasAuthority('INTEGRATION_CONFIG')") public Map<String,Object> collect(@RequestParam(defaultValue="50") int count){return service.collect(count);}
}
