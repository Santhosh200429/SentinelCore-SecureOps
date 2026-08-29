package com.santhosh.dashboard.service;

import com.santhosh.dashboard.model.Alert;
import com.santhosh.dashboard.model.Asset;
import com.santhosh.dashboard.repository.AlertRepository;
import com.santhosh.dashboard.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RealtimeAlertService {
    private final LiveTelemetryService telemetry;
    private final IncidentRepository incidents;
    private final AlertRepository alerts;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final int cpuThreshold, memoryThreshold, diskThreshold, offlineTimeout;
    private final boolean autoCreateIncidents;
    public RealtimeAlertService(LiveTelemetryService telemetry, AlertRepository alerts, IncidentRepository incidents,
      @Value("${alert.cpu.threshold:90}") int cpuThreshold,
      @Value("${alert.memory.threshold:90}") int memoryThreshold,
      @Value("${alert.disk.threshold:90}") int diskThreshold,
      @Value("${asset.offline-timeout-seconds:60}") int offlineTimeout, @Value("${alerts.auto-create-incidents:false}") boolean autoCreateIncidents){this.telemetry=telemetry;this.alerts=alerts;this.incidents=incidents;this.cpuThreshold=cpuThreshold;this.memoryThreshold=memoryThreshold;this.diskThreshold=diskThreshold;this.offlineTimeout=offlineTimeout;this.autoCreateIncidents=autoCreateIncidents;}

    @Scheduled(fixedDelayString="${telemetry.interval.seconds:5}000", initialDelay=5000)
    @Transactional public void evaluate(){ telemetry.getLocalAsset().ifPresent(asset -> { evaluateMetric(asset,"CPU",asset.getCpuUsage(),cpuThreshold); evaluateMetric(asset,"Memory",asset.getMemoryUsage(),memoryThreshold); evaluateMetric(asset,"Disk",asset.getDiskUsage(),diskThreshold); }); }
    private void evaluateMetric(Asset asset,String metric,int value,int threshold){ if(value < threshold)return; String severity=value>=Math.min(95,threshold+5)?"Critical":"Warning"; String title=metric+" utilization exceeded "+value+"%"; String source=asset.getAssetName(); LocalDateTime since=LocalDateTime.now().minusMinutes(2); if(!alerts.existsRecentAlert(title,source,since)){ Alert a=alerts.save(new Alert(title,severity,source,LocalDateTime.now())); if(autoCreateIncidents && "Critical".equals(severity)) createIncident(a); broadcast(a); } }

    private void createIncident(Alert alert){
        if (incidents.existsByTitleAndStatusIn(alert.getTitle(), List.of("Open", "Investigating"))) return;
        var incident = new com.santhosh.dashboard.model.Incident();
        incident.setIncidentId("AUTO-" + UUID.randomUUID());
        incident.setTitle(alert.getTitle()); incident.setDescription("Automatically created from genuine infrastructure alert: " + alert.getSource());
        incident.setSeverity(alert.getSeverity()); incident.setStatus("Open"); incident.setAssignedTeam("Security Team"); incident.setSlaHours(2); incident.setCreatedAt(LocalDateTime.now());
        incidents.save(incident);
    }
    public SseEmitter subscribe(){ SseEmitter e=new SseEmitter(0L); emitters.add(e); e.onCompletion(()->emitters.remove(e));e.onTimeout(()->emitters.remove(e));e.onError(x->emitters.remove(e));return e; }
    private void broadcast(Alert a){ for(SseEmitter e:emitters)try{e.send(SseEmitter.event().name("alert").data(Map.of("id",a.getId(),"title",a.getTitle(),"severity",a.getSeverity(),"source",a.getSource(),"timestamp",a.getTimestamp())));}catch(Exception x){e.complete();emitters.remove(e);} }
}
