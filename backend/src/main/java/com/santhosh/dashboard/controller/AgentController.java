package com.santhosh.dashboard.controller;

import com.santhosh.dashboard.agent.AgentTelemetryService;
import com.santhosh.dashboard.model.Agent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.SecureRandom;
import java.util.*;

@RestController
public class AgentController {
    private final AgentTelemetryService service;
    private final SecureRandom random = new SecureRandom();
    public AgentController(AgentTelemetryService service){this.service=service;}

    @PostMapping("/api/agents/enroll")
    public ResponseEntity<?> enroll(@RequestBody Map<String,Object> body, Authentication auth){
        String name=String.valueOf(body.getOrDefault("name","My Device")).trim();
        if(name.isBlank()) name="My Device";
        String token=generateToken(); Agent a=new Agent(); a.setOwnerUsername(auth.getName()); a.setName(name); a.setTokenHash(AgentTelemetryService.sha256(token));
        // Save through a small local repository lookup by service is intentionally avoided; use repository via helper below.
        service.create(a);
        return ResponseEntity.ok(Map.of("agentId",a.getId(),"name",a.getName(),"token",token,"warning","Store this token securely. It is shown only once."));
    }

    @GetMapping("/api/agents")
    public List<Map<String,Object>> list(Authentication auth){
        List<Map<String,Object>> result = new ArrayList<>();
        service.listAgents(auth.getName()).forEach(a -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("name", a.getName());
            row.put("active", a.isActive());
            row.put("createdAt", a.getCreatedAt());
            row.put("lastSeen", a.getLastSeen() == null ? "" : a.getLastSeen());
            row.put("assetId", a.getAssetId() == null ? 0L : a.getAssetId());
            result.add(row);
        });
        return result;
    }

    @DeleteMapping("/api/agents/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, Authentication auth){
        return service.findOwned(id,auth.getName()).map(a->{service.revoke(a);return ResponseEntity.ok(Map.of("success",true));}).orElseGet(()->ResponseEntity.notFound().build());
    }

    @GetMapping("/api/agents/telemetry/{assetId}")
    public Map<String,Object> latest(@PathVariable Long assetId, Authentication auth){return service.latest(assetId,auth.getName());}

    @GetMapping(value="/api/agents/stream/{assetId}", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long assetId, Authentication auth){return service.subscribe(assetId,auth.getName());}

    @PostMapping("/api/agent/telemetry")
    public ResponseEntity<?> telemetry(@RequestHeader(value="Authorization",required=false) String authorization, @RequestBody Map<String,Object> payload){
        if(authorization==null || !authorization.startsWith("Bearer ")) return ResponseEntity.status(401).body(Map.of("error","Agent token required"));
        String token=authorization.substring(7).trim();
        if(token.isBlank()) return ResponseEntity.status(401).body(Map.of("error","Agent token required"));
        try{return service.authenticate(token).map(a->{try{return ResponseEntity.ok(service.ingest(a,payload));}catch(SecurityException e){return ResponseEntity.status(403).body(Map.of("error",e.getMessage()));}catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}}).orElseGet(()->ResponseEntity.status(401).body(Map.of("error","Invalid or revoked agent token")));}catch(Exception e){return ResponseEntity.status(500).body(Map.of("error","Telemetry processing failed"));}
    }

    private String generateToken(){byte[] b=new byte[32];random.nextBytes(b);StringBuilder s=new StringBuilder(64);for(byte x:b)s.append(String.format("%02x",x));return s.toString();}
}
