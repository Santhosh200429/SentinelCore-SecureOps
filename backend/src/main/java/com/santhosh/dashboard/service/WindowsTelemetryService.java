package com.santhosh.dashboard.service;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;

@Service
public class WindowsTelemetryService {
    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final CentralProcessor processor = hardware.getProcessor();
    private long[] previousCpuTicks = processor.getSystemCpuLoadTicks();
    private final JdbcTemplate jdbc;

    public WindowsTelemetryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public synchronized Map<String, Object> snapshot() {
        double cpu = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
        previousCpuTicks = processor.getSystemCpuLoadTicks();
        cpu = round(Math.max(0, Math.min(100, cpu)), 2);

        GlobalMemory memory = hardware.getMemory();
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        long usedMemory = Math.max(0, totalMemory - availableMemory);
        double memoryUsage = totalMemory == 0 ? Double.NaN : round((usedMemory * 100.0) / totalMemory, 2);

        long totalDisk = 0, freeDisk = 0;
        for (OSFileStore store : systemInfo.getOperatingSystem().getFileSystem().getFileStores()) {
            totalDisk += Math.max(0, store.getTotalSpace());
            freeDisk += Math.max(0, store.getUsableSpace());
        }
        long usedDisk = Math.max(0, totalDisk - freeDisk);
        double diskUsage = totalDisk == 0 ? Double.NaN : round((usedDisk * 100.0) / totalDisk, 2);

        long rx = 0, tx = 0;
        List<Map<String,Object>> interfaces = new ArrayList<>();
        for (NetworkIF nif : hardware.getNetworkIFs()) {
            if (!nif.updateAttributes()) continue;
            rx += Math.max(0, nif.getBytesRecv());
            tx += Math.max(0, nif.getBytesSent());
            Map<String,Object> iface = new LinkedHashMap<>();
            iface.put("name", nif.getName());
            iface.put("displayName", nif.getDisplayName());
            iface.put("status", nif.getIfOperStatus().name());
            iface.put("bytesReceived", nif.getBytesRecv());
            iface.put("bytesSent", nif.getBytesSent());
            iface.put("packetsReceived", nif.getPacketsRecv());
            iface.put("packetsSent", nif.getPacketsSent());
            iface.put("inErrors", nif.getInErrors());
            iface.put("outErrors", nif.getOutErrors());
            iface.put("ipv4", Arrays.asList(nif.getIPv4addr()));
            iface.put("ipv6", Arrays.asList(nif.getIPv6addr()));
            interfaces.add(iface);
        }

        OperatingSystem os = systemInfo.getOperatingSystem();
        List<OSProcess> processes = os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, 10);
        List<Map<String,Object>> topProcesses = processes.stream().map(p -> Map.<String,Object>of(
                "pid", p.getProcessID(), "name", p.getName(),
                "cpuUsage", round(p.getProcessCpuLoadCumulative() * 100.0, 2),
                "memoryBytes", p.getResidentSetSize(), "executable", Optional.ofNullable(p.getPath()).orElse("Data unavailable")
        )).toList();

        String hostname = "Data unavailable";
        try { hostname = InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) { }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("hostname", hostname);
        result.put("os", os.getFamily());
        result.put("osVersion", os.getVersionInfo().toString());
        result.put("architecture", System.getProperty("os.arch", "Data unavailable"));
        result.put("processor", processor.getProcessorIdentifier().getName());
        result.put("cpuUsage", Double.isFinite(cpu) ? cpu : "Data unavailable");
        double[] loadAverages = processor.getSystemLoadAverage(1);
        double loadAverage = loadAverages.length > 0 ? loadAverages[0] : Double.NaN;
        result.put("cpuLoad", Double.isFinite(loadAverage) ? round(loadAverage, 2) : "Data unavailable");
        result.put("cpuCores", processor.getPhysicalProcessorCount());
        result.put("logicalProcessors", processor.getLogicalProcessorCount());
        result.put("memoryTotal", totalMemory);
        result.put("memoryUsed", usedMemory);
        result.put("memoryAvailable", availableMemory);
        result.put("memoryUsage", Double.isFinite(memoryUsage) ? memoryUsage : "Data unavailable");
        result.put("diskTotal", totalDisk);
        result.put("diskUsed", usedDisk);
        result.put("diskFree", freeDisk);
        result.put("diskUsage", Double.isFinite(diskUsage) ? diskUsage : "Data unavailable");
        result.put("networkReceived", rx);
        result.put("networkSent", tx);
        result.put("networkInterfaces", interfaces);
        result.put("processCount", os.getProcessCount());
        result.put("topProcesses", topProcesses);
        result.put("uptime", os.getSystemUptime());
        result.put("bootTime", os.getSystemBootTime());

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        result.put("jvmHeapUsed", heapUsed);
        result.put("jvmHeapMax", heapMax);
        result.put("jvmThreadCount", threadBean.getThreadCount());
        result.put("jvmUptime", ManagementFactory.getRuntimeMXBean().getUptime());
        var osBean = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        double processCpu = osBean == null ? Double.NaN : osBean.getProcessCpuLoad() * 100.0;
        result.put("jvmCpuUsage", Double.isFinite(processCpu) ? round(processCpu, 2) : "Data unavailable");
        long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(g -> Math.max(0, g.getCollectionCount())).sum();
        result.put("jvmGcCollections", gcCount);
        try {
            result.put("databaseSizeBytes", jdbc.queryForObject("select pg_database_size(current_database())", Long.class));
            result.put("dbActiveConnections", jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database()", Integer.class));
            result.put("dbMaxConnections", jdbc.queryForObject("select current_setting('max_connections')::int", Integer.class));
            result.put("dbHealth", "UP");
        } catch (Exception e) {
            result.put("databaseSizeBytes", "Data unavailable");
            result.put("dbActiveConnections", "Data unavailable");
            result.put("dbMaxConnections", "Data unavailable");
            result.put("dbHealth", "Data unavailable");
        }
        result.put("telemetrySource", "OSHI + JVM + PostgreSQL");
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    private static double round(double value, int scale) {
        if (!Double.isFinite(value)) return Double.NaN;
        double p = Math.pow(10, scale); return Math.round(value * p) / p;
    }
}
