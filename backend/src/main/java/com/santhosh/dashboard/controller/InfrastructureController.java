package com.santhosh.dashboard.controller;

import com.santhosh.dashboard.model.Asset;
import com.santhosh.dashboard.service.LiveTelemetryService;
import com.santhosh.dashboard.service.WindowsTelemetryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api/infrastructure")
public class InfrastructureController {
    private final LiveTelemetryService live;
    private final WindowsTelemetryService host;
    public InfrastructureController(LiveTelemetryService live, WindowsTelemetryService host){ this.live=live; this.host=host; }

    @GetMapping("/telemetry") @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Map<String,Object> getTelemetry(){
        Map<String,Object> data = live.latest();
        if (data.size() <= 1) return host.snapshot();
        // Keep backwards-compatible field names while exposing truthful raw values.
        Map<String,Object> out = new LinkedHashMap<>(data);
        out.put("cpuCount", data.get("cpuUsage") == null ? "Data unavailable" : data.get("cpuUsage") + "%");
        out.put("memoryPoolInfo", data.get("memoryUsage") == null ? "Data unavailable" : data.get("memoryUsage") + "%");
        out.put("networkIoRate", data.get("networkReceived") == null ? "Data unavailable" : data.get("networkReceived"));
        out.put("activeInstances", data.get("processCount") == null ? "Data unavailable" : data.get("processCount"));
        out.put("vaultHsmStatus", "Integration not configured");
        return out;
    }

    @GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE) @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public SseEmitter stream(){ return live.subscribe(); }

    @GetMapping("/asset") @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public ResponseEntity<Asset> localAsset(){ return live.getLocalAsset().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build()); }
}
