package com.santhosh.dashboard.service;

import com.santhosh.dashboard.model.Alert;
import com.santhosh.dashboard.model.Asset;
import com.santhosh.dashboard.model.Incident;
import com.santhosh.dashboard.model.SecurityEvent;
import com.santhosh.dashboard.repository.AlertRepository;
import com.santhosh.dashboard.repository.IncidentRepository;
import com.santhosh.dashboard.repository.SecurityEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SecurityEventService {
    private final SecurityEventRepository events;
    private final AlertRepository alerts;
    private final IncidentRepository incidents;
    private final boolean autoCreateIncidents;

    public SecurityEventService(SecurityEventRepository events, AlertRepository alerts, IncidentRepository incidents,
            @Value("${alerts.auto-create-incidents:false}") boolean autoCreateIncidents) {
        this.events=events; this.alerts=alerts; this.incidents=incidents; this.autoCreateIncidents=autoCreateIncidents;
    }

    @Transactional
    public int ingestAgentEvents(Asset asset, String owner, List<Map<String,Object>> incoming) {
        if (incoming==null) return 0;
        int imported=0;
        for (Map<String,Object> raw: incoming) {
            String eventId=str(raw.get("eventId"));
            String fingerprint=str(raw.get("fingerprint"));
            if (fingerprint.isBlank()) fingerprint=hash(owner+"|"+asset.getId()+"|"+eventId+"|"+str(raw.get("timestamp"))+"|"+str(raw.get("message")));
            if (events.existsByFingerprint(fingerprint)) continue;
            SecurityEvent e=new SecurityEvent();
            e.setAssetId(asset.getId()); e.setOwnerUsername(owner); e.setEventId(eventId.isBlank()?"UNKNOWN":eventId);
            e.setTimestamp(parseTime(str(raw.get("timestamp")))); e.setSource("SentinelCore Agent / Windows Security");
            e.setHostname(asset.getHostname()); e.setSeverity(defaulted(str(raw.get("severity")), "INFO"));
            e.setMessage(defaulted(str(raw.get("message")), "Windows security event"));
            e.setCategory(defaulted(str(raw.get("category")), "SECURITY")); e.setMetadata(str(raw.get("metadata")));
            e.setUsername(str(raw.get("username"))); e.setRemoteIp(str(raw.get("remoteIp"))); e.setFingerprint(fingerprint);
            events.save(e); imported++;
        }
        correlateAuthentication(asset, owner);
        return imported;
    }

    private void correlateAuthentication(Asset asset, String owner) {
        LocalDateTime since=LocalDateTime.now().minusMinutes(5);
        long failures=events.countByOwnerUsernameAndAssetIdAndEventIdAndTimestampAfter(owner, asset.getId(), "4625", since);
        if (failures < 5) return;
        String title="Possible brute-force authentication attack";
        String source=asset.getAssetName();
        if (alerts.existsRecentAlert(title, source, LocalDateTime.now().minusMinutes(5))) return;
        Alert alert=alerts.save(new Alert(title,"High",source,LocalDateTime.now()));
        if(autoCreateIncidents && !incidents.existsByTitleAndStatusIn(title,List.of("Open","Investigating"))) {
            Incident i=new Incident(); i.setIncidentId("AUTO-"+UUID.randomUUID()); i.setTitle(title);
            i.setDescription("Correlation rule detected "+failures+" Windows failed-logon events (4625) within five minutes on "+source+".");
            i.setSeverity("High"); i.setStatus("Open"); i.setAssignedTeam("Security Team"); i.setSlaHours(4); i.setCreatedAt(LocalDateTime.now()); incidents.save(i);
        }
    }

    public List<SecurityEvent> recent(String owner){return events.findTop100ByOwnerUsernameOrderByTimestampDesc(owner);}
    public List<SecurityEvent> allForAdmin(){return events.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"timestamp"));}
    private static String str(Object v){return v==null?"":String.valueOf(v);}
    private static String defaulted(String v,String d){return v.isBlank()?d:v;}
    private static LocalDateTime parseTime(String v){try{return LocalDateTime.parse(v);}catch(Exception e){return LocalDateTime.now();}}
    private static String hash(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte z:b)x.append(String.format("%02x",z));return x.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
