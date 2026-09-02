package com.santhosh.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset")
public class Asset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String assetName;
    @Column(unique = true)
    private String ipAddress;
  private String assetType;
  private String status;

  private int cpuUsage;
  private int memoryUsage;
  private int diskUsage;
  private int networkUsage;

  private double uptime;

  private String location;

  @Column(unique = true)
  private String machineId;

  @Column(name = "owner_username")
  private String ownerUsername;
  private String hostname;
  private String operatingSystem;
  private String osVersion;
  private String architecture;
  private String processor;
  private Integer cpuCores;
  private Integer logicalProcessors;
  private Long lastSeenEpoch;

  public Asset() {
  }

  public Asset(Long id, String assetName, String ipAddress, String assetType, String status,
               int cpuUsage, int memoryUsage, int diskUsage,
               int networkUsage, double uptime, String location) {
    this.id = id;
    this.assetName = assetName;
    this.ipAddress = ipAddress;
    this.assetType = assetType;
    this.status = status;
    this.cpuUsage = cpuUsage;
    this.memoryUsage = memoryUsage;
    this.diskUsage = diskUsage;
    this.networkUsage = networkUsage;
    this.uptime = uptime;
    this.location = location;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getAssetName() {
    return assetName;
  }

  public void setAssetName(String assetName) {
    this.assetName = assetName;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getAssetType() {
    return assetType;
  }

  public void setAssetType(String assetType) {
    this.assetType = assetType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getCpuUsage() {
    return cpuUsage;
  }

  public void setCpuUsage(int cpuUsage) {
    this.cpuUsage = cpuUsage;
  }

  public int getMemoryUsage() {
    return memoryUsage;
  }

  public void setMemoryUsage(int memoryUsage) {
    this.memoryUsage = memoryUsage;
  }

  public int getDiskUsage() {
    return diskUsage;
  }

  public void setDiskUsage(int diskUsage) {
    this.diskUsage = diskUsage;
  }

  public int getNetworkUsage() {
    return networkUsage;
  }

  public void setNetworkUsage(int networkUsage) {
    this.networkUsage = networkUsage;
  }

  public double getUptime() {
    return uptime;
  }

  public void setUptime(double uptime) {
    this.uptime = uptime;
  }

  public String getOwnerUsername() { return ownerUsername; }
  public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

  public String getMachineId() { return machineId; }
  public void setMachineId(String machineId) { this.machineId = machineId; }
  public String getHostname() { return hostname; }
  public void setHostname(String hostname) { this.hostname = hostname; }
  public String getOperatingSystem() { return operatingSystem; }
  public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
  public String getOsVersion() { return osVersion; }
  public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
  public String getArchitecture() { return architecture; }
  public void setArchitecture(String architecture) { this.architecture = architecture; }
  public String getProcessor() { return processor; }
  public void setProcessor(String processor) { this.processor = processor; }
  public Integer getCpuCores() { return cpuCores; }
  public void setCpuCores(Integer cpuCores) { this.cpuCores = cpuCores; }
  public Integer getLogicalProcessors() { return logicalProcessors; }
  public void setLogicalProcessors(Integer logicalProcessors) { this.logicalProcessors = logicalProcessors; }
  public Long getLastSeenEpoch() { return lastSeenEpoch; }
  public void setLastSeenEpoch(Long lastSeenEpoch) { this.lastSeenEpoch = lastSeenEpoch; }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }
}
