package com.santhosh.dashboard.agent;

import com.santhosh.dashboard.model.Agent;
import com.santhosh.dashboard.model.Asset;
import com.santhosh.dashboard.model.SystemTelemetry;
import com.santhosh.dashboard.repository.AgentRepository;
import com.santhosh.dashboard.repository.AssetRepository;
import com.santhosh.dashboard.repository.SystemTelemetryRepository;
import com.santhosh.dashboard.service.SecurityEventService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AgentTelemetryService {
    private final AgentRepository agents;
    private final AssetRepository assets;
    private final SystemTelemetryRepository telemetry;
    private final SecurityEventService securityEvents;
    private final Map<Long, Map<String,Object>> latest = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public AgentTelemetryService(AgentRepository agents, AssetRepository assets, SystemTelemetryRepository telemetry, SecurityEventService securityEvents) {
        this.agents=agents; this.assets=assets; this.telemetry=telemetry; this.securityEvents=securityEvents;
    }

    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException("Hash unavailable", e); }
    }

    @Transactional
    public Map<String,Object> ingest(Agent agent, Map<String,Object> input) {
        String machineId = str(input.get("machineId"));
        if (machineId.isBlank()) throw new IllegalArgumentException("machineId is required");
        Asset asset = assets.findByMachineId(machineId).orElseGet(Asset::new);
        if (asset.getMachineId() != null && asset.getOwnerUsername() != null && !agent.getOwnerUsername().equals(asset.getOwnerUsername()))
            throw new SecurityException("Device belongs to another account");
        asset.setMachineId(machineId);
        asset.setOwnerUsername(agent.getOwnerUsername());
        asset.setAssetType("ENDPOINT");
        asset.setAssetName(defaulted(str(input.get("hostname")), agent.getName()));
        asset.setHostname(defaulted(str(input.get("hostname")), "Unknown host"));
        // Do not enforce global IP uniqueness for endpoint agents: private IPs repeat across different customer networks.
        asset.setIpAddress(null);
        asset.setOperatingSystem(str(input.get("os")));
        asset.setOsVersion(str(input.get("osVersion")));
        asset.setArchitecture(str(input.get("architecture")));
        asset.setProcessor(str(input.get("processor")));
        asset.setCpuCores(integer(input.get("cpuCores")));
        asset.setLogicalProcessors(integer(input.get("logicalProcessors")));
        asset.setCpuUsage(integer(input.get("cpuUsage")));
        asset.setMemoryUsage(integer(input.get("memoryUsage")));
        asset.setDiskUsage(integer(input.get("diskUsage")));
        asset.setNetworkUsage(0);
        asset.setUptime(number(input.get("uptime")) / 3600.0);
        asset.setStatus("ONLINE");
        asset.setLastSeenEpoch(System.currentTimeMillis());
        asset = assets.save(asset);

        agent.setAssetId(asset.getId()); agent.setLastSeen(LocalDateTime.now()); agents.save(agent);
        Map<String,Object> snapshot = new LinkedHashMap<>(input);
        snapshot.put("assetId", asset.getId()); snapshot.put("agentId", agent.getId());
        snapshot.put("ownerUsername", agent.getOwnerUsername()); snapshot.put("timestamp", LocalDateTime.now().toString());
        latest.put(asset.getId(), snapshot);
        telemetry.save(new SystemTelemetry(asset.getId(), LocalDateTime.now(), number(input.get("cpuUsage")), number(input.get("memoryUsage")), number(input.get("diskUsage")), longValue(input.get("networkReceived")), longValue(input.get("networkSent")), integer(input.get("processCount")), longValue(input.get("uptime"))));
        Object eventList=input.get("securityEvents");
        if(eventList instanceof List<?> list){
            List<Map<String,Object>> rows=new ArrayList<>();
            for(Object item:list) if(item instanceof Map<?,?> map){Map<String,Object> row=new LinkedHashMap<>(); map.forEach((k,v)->row.put(String.valueOf(k),v)); rows.add(row);}
            securityEvents.ingestAgentEvents(asset, agent.getOwnerUsername(), rows);
        }
        emit(asset.getId(), snapshot);
        return Map.of("accepted", true, "assetId", asset.getId(), "timestamp", snapshot.get("timestamp"));
    }

    public Agent create(Agent agent){ return agents.save(agent); }

    public Optional<Agent> authenticate(String token) { return agents.findByTokenHashAndActiveTrue(sha256(token)); }
    public List<Agent> listAgents(String username){ return agents.findByOwnerUsernameOrderByCreatedAtDesc(username); }
    public Optional<Agent> findOwned(Long id, String username){ return agents.findByIdAndOwnerUsername(id, username); }
    public void revoke(Agent agent){ agent.setActive(false); agents.save(agent); }
    public Map<String,Object> latest(Long assetId, String username){
        return assets.findById(assetId).filter(a -> username.equals(a.getOwnerUsername())).map(a -> latest.getOrDefault(assetId, Map.of("status","WAITING FOR AGENT TELEMETRY","assetId",assetId))).orElseThrow(() -> new SecurityException("Device not found"));
    }
    public SseEmitter subscribe(Long assetId, String username){
        assets.findById(assetId).filter(a -> username.equals(a.getOwnerUsername())).orElseThrow(() -> new SecurityException("Device not found"));
        SseEmitter emitter=new SseEmitter(0L); emitters.computeIfAbsent(assetId,k->new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(assetId, emitter)); emitter.onTimeout(() -> remove(assetId, emitter)); emitter.onError(e -> remove(assetId, emitter));
        try { emitter.send(SseEmitter.event().name("telemetry").data(latest.getOrDefault(assetId, Map.of("status","WAITING FOR AGENT TELEMETRY","assetId",assetId)))); } catch(Exception e){remove(assetId,emitter);}
        return emitter;
    }
    private void emit(Long assetId, Map<String,Object> snapshot){
        for(SseEmitter e: emitters.getOrDefault(assetId,new CopyOnWriteArrayList<>())) try{e.send(SseEmitter.event().name("telemetry").data(snapshot));}catch(Exception ex){e.complete();remove(assetId,e);}
    }
    private void remove(Long id,SseEmitter e){var list=emitters.get(id);if(list!=null)list.remove(e);}
    private static String str(Object v){return v==null?"":String.valueOf(v);}
    private static String defaulted(String v,String d){return v.isBlank()?d:v;}
    private static String nullIfBlank(String v){return v.isBlank()?null:v;}
    private static int integer(Object v){return v instanceof Number n?(int)Math.round(n.doubleValue()):0;}
    private static long longValue(Object v){return v instanceof Number n?n.longValue():0L;}
    private static double number(Object v){return v instanceof Number n?n.doubleValue():0.0;}
}
