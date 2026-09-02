package com.santhosh.dashboard.service;

import com.santhosh.dashboard.model.Asset;
import com.santhosh.dashboard.model.SystemTelemetry;
import com.santhosh.dashboard.repository.AssetRepository;
import com.santhosh.dashboard.repository.SystemTelemetryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class LiveTelemetryService {
    private final WindowsTelemetryService host;
    private final AssetRepository assetRepository;
    private final SystemTelemetryRepository telemetryRepository;
    private final JdbcTemplate jdbc;
    private final List<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private Map<String,Object> latest = Map.of("status", "WAITING FOR TELEMETRY");
    private Map<String,Object> previousPersisted = null;
    private String machineId;
    private Asset localAsset;
    private final int intervalSeconds;
    private final int offlineTimeoutSeconds;
    private final String dataMode;

    public LiveTelemetryService(WindowsTelemetryService host, AssetRepository assetRepository, SystemTelemetryRepository telemetryRepository, JdbcTemplate jdbc,
                                @Value("${telemetry.interval.seconds:5}") int intervalSeconds,
                                @Value("${asset.offline-timeout-seconds:60}") int offlineTimeoutSeconds,
                                @Value("${app.data.mode:live}") String dataMode) {
        this.host=host; this.assetRepository=assetRepository; this.telemetryRepository=telemetryRepository; this.jdbc=jdbc;
        this.intervalSeconds=intervalSeconds; this.offlineTimeoutSeconds=offlineTimeoutSeconds; this.dataMode=dataMode;
    }

    @Scheduled(fixedDelayString = "${telemetry.interval.seconds:5}000", initialDelay = 2000)
    @Transactional
    public void collect() {
        // Demo mode still needs live host telemetry for the Infrastructure page,
        // but must not persist it into the seeded demo asset data.
        boolean demoMode = "demo".equalsIgnoreCase(dataMode);

        Map<String,Object> snapshot;
        try { snapshot = new LinkedHashMap<>(host.snapshot()); }
        catch (Exception e) { latest = Map.of("status", "Data unavailable", "error", "Telemetry collection failed", "timestamp", Instant.now().toString()); return; }
        latest = snapshot;
        if (!demoMode) {
            ensureAsset(snapshot);
            persistIfMeaningfullyChanged(snapshot);
        }
        emit(snapshot);
    }

    public Map<String,Object> latest() { return latest; }
    public List<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> emitters(){ return emitters; }
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe() {
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter)); emitter.onTimeout(() -> emitters.remove(emitter)); emitter.onError(e -> emitters.remove(emitter));
        try { emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("telemetry").data(latest)); } catch (Exception e) { emitters.remove(emitter); }
        return emitter;
    }
    public Optional<Asset> getLocalAsset(){ return Optional.ofNullable(localAsset); }

    private void emit(Map<String,Object> snapshot) {
        for (var emitter : emitters) try { emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("telemetry").data(snapshot)); }
        catch (Exception e) { emitter.complete(); emitters.remove(emitter); }
    }

    private void ensureAsset(Map<String,Object> s) {
        if (machineId == null) machineId = loadMachineId();
        localAsset = assetRepository.findByMachineId(machineId).orElseGet(() -> {
            Asset a = new Asset(); a.setMachineId(machineId); a.setAssetType("SERVER"); return a;
        });
        localAsset.setAssetName(String.valueOf(s.getOrDefault("hostname", "Windows Host")));
        localAsset.setHostname(String.valueOf(s.getOrDefault("hostname", "Data unavailable")));
        localAsset.setIpAddress(resolveIp());
        localAsset.setOperatingSystem(String.valueOf(s.getOrDefault("os", "Data unavailable")));
        localAsset.setOsVersion(String.valueOf(s.getOrDefault("osVersion", "Data unavailable")));
        localAsset.setArchitecture(String.valueOf(s.getOrDefault("architecture", "Data unavailable")));
        localAsset.setProcessor(String.valueOf(s.getOrDefault("processor", "Data unavailable")));
        localAsset.setCpuCores(toInt(s.get("cpuCores"))); localAsset.setLogicalProcessors(toInt(s.get("logicalProcessors")));
        localAsset.setCpuUsage(toInt(s.get("cpuUsage"))); localAsset.setMemoryUsage(toInt(s.get("memoryUsage"))); localAsset.setDiskUsage(toInt(s.get("diskUsage")));
        localAsset.setNetworkUsage(0); localAsset.setUptime(toDouble(s.get("uptime")) / 3600.0);
        if (!"MAINTENANCE".equalsIgnoreCase(localAsset.getStatus())) localAsset.setStatus("ONLINE");
        localAsset.setLastSeenEpoch(System.currentTimeMillis());
        localAsset = assetRepository.save(localAsset);
    }

    private void persistIfMeaningfullyChanged(Map<String,Object> s) {
        if (localAsset == null || localAsset.getId() == null) return;
        boolean changed = previousPersisted == null ||
                changed(s, "cpuUsage", 1.0) || changed(s, "memoryUsage", 1.0) || changed(s, "diskUsage", 1.0) ||
                changed(s, "networkReceived", 1024 * 1024.0) || changed(s, "networkSent", 1024 * 1024.0) || changed(s, "processCount", 1.0);
        if (!changed) return;
        telemetryRepository.save(new SystemTelemetry(localAsset.getId(), LocalDateTime.now(), toDouble(s.get("cpuUsage")), toDouble(s.get("memoryUsage")), toDouble(s.get("diskUsage")), toLong(s.get("networkReceived")), toLong(s.get("networkSent")), toInt(s.get("processCount")), toLong(s.get("uptime"))));
        previousPersisted = new HashMap<>(s);
    }
    private boolean changed(Map<String,Object> s,String k,double threshold){ return previousPersisted==null || Math.abs(toDouble(s.get(k))-toDouble(previousPersisted.get(k))) >= threshold; }

    private String loadMachineId(){
        try { java.nio.file.Path p=java.nio.file.Paths.get(System.getProperty("user.home"), ".sentinelcore", "machine-id");
            if (java.nio.file.Files.exists(p)) return java.nio.file.Files.readString(p).trim();
            String id=UUID.randomUUID().toString(); java.nio.file.Files.createDirectories(p.getParent()); java.nio.file.Files.writeString(p,id); return id;
        } catch(Exception e){ return "volatile-"+UUID.randomUUID(); }
    }
    private String resolveIp(){ try { return InetAddress.getLocalHost().getHostAddress(); } catch(Exception e){ return null; } }
    private int toInt(Object v){ return v instanceof Number n ? (int)Math.round(n.doubleValue()) : 0; }
    private long toLong(Object v){ return v instanceof Number n ? n.longValue() : 0L; }
    private double toDouble(Object v){ return v instanceof Number n ? n.doubleValue() : 0.0; }
}
