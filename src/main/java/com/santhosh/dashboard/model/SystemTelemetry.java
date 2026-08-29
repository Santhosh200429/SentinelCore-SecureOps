package com.santhosh.dashboard.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_telemetry", indexes = @Index(name = "idx_telemetry_asset_time", columnList = "asset_id,timestamp"))
public class SystemTelemetry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="asset_id", nullable=false) private Long assetId;
    @Column(nullable=false) private LocalDateTime timestamp;
    private double cpuUsage; private double memoryUsage; private double diskUsage;
    private long networkReceived; private long networkSent; private int processCount; private long uptime;
    public SystemTelemetry() {}
    public SystemTelemetry(Long assetId, LocalDateTime timestamp, double cpuUsage, double memoryUsage, double diskUsage, long networkReceived, long networkSent, int processCount, long uptime) {
        this.assetId=assetId; this.timestamp=timestamp; this.cpuUsage=cpuUsage; this.memoryUsage=memoryUsage; this.diskUsage=diskUsage; this.networkReceived=networkReceived; this.networkSent=networkSent; this.processCount=processCount; this.uptime=uptime;
    }
    public Long getId(){return id;} public Long getAssetId(){return assetId;} public LocalDateTime getTimestamp(){return timestamp;}
    public double getCpuUsage(){return cpuUsage;} public double getMemoryUsage(){return memoryUsage;} public double getDiskUsage(){return diskUsage;}
    public long getNetworkReceived(){return networkReceived;} public long getNetworkSent(){return networkSent;} public int getProcessCount(){return processCount;} public long getUptime(){return uptime;}
}
