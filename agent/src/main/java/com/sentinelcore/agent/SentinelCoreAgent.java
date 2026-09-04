package com.sentinelcore.agent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

/**
 * Lightweight SentinelCore endpoint collector.
 *
 * Collects:
 * - CPU usage
 * - Memory usage
 * - Disk usage
 * - Network traffic
 * - Process count
 * - OS information
 * - Hardware information
 * - Windows Security Events
 *
 * Authentication:
 * - Uses a revocable SentinelCore device token.
 * - Never stores or sends the user's SentinelCore password.
 */
public class SentinelCoreAgent {

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final SystemInfo si = new SystemInfo();

    private final HardwareAbstractionLayer hal = si.getHardware();

    private final CentralProcessor cpu = hal.getProcessor();

    /*
     * Environment variables:
     *
     * SENTINELCORE_URL (optional)
     * SENTINELCORE_AGENT_TOKEN (required)
     * SENTINELCORE_INTERVAL_SECONDS (optional)
     *
     * If SENTINELCORE_URL is not configured,
     * the agent automatically connects to the
     * SentinelCore production server.
     */

    /*
     * Production SentinelCore backend.
     *
     * Users do not need to configure SENTINELCORE_URL
     * unless they intentionally want to connect the agent
     * to another SentinelCore server.
     */
    private static final String DEFAULT_SERVER_URL =
            "https://sentinelcore-secureops-o5wr.onrender.com";

    private final String baseUrl = env(
            "SENTINELCORE_URL",
            DEFAULT_SERVER_URL
    ).trim().replaceAll("/$", "");

    private final String token = env(
            "SENTINELCORE_AGENT_TOKEN",
            ""
    ).trim();

    private final int interval = Integer.parseInt(
            env(
                    "SENTINELCORE_INTERVAL_SECONDS",
                    "5"
            ).trim()
    );

    private long[] previousTicks;

    private final String machineId = loadMachineId();

    public static void main(String[] args) throws Exception {
        new SentinelCoreAgent().run();
    }

    /**
     * Main agent loop.
     */
    private void run() throws Exception {

        if (token.isBlank()) {
            throw new IllegalStateException(
                    "SENTINELCORE_AGENT_TOKEN is not configured"
            );
        }

        System.out.println(
                "SentinelCore Agent started. Server="
                        + baseUrl
                        + " interval="
                        + interval
                        + "s"
        );

        while (true) {

            try {

                send(snapshot());

            } catch (Exception e) {

                System.err.println(
                        "Telemetry send failed: "
                                + e.getMessage()
                );
            }

            Thread.sleep(
                    Math.max(1, interval) * 1000L
            );
        }
    }

    /**
     * Collect endpoint telemetry.
     */
    private Map<String, Object> snapshot() {

        GlobalMemory mem = hal.getMemory();

        /*
         * CPU
         */
        long[] ticks = cpu.getSystemCpuLoadTicks();

        double cpuPct = previousTicks == null
                ? 0
                : cpu.getSystemCpuLoadBetweenTicks(
                        previousTicks
                ) * 100.0;

        previousTicks = ticks;

        /*
         * Disk
         */
        long totalDisk = 0;
        long freeDisk = 0;

        for (
                OSFileStore fs :
                si.getOperatingSystem()
                        .getFileSystem()
                        .getFileStores()
        ) {

            totalDisk += Math.max(
                    0,
                    fs.getTotalSpace()
            );

            freeDisk += Math.max(
                    0,
                    fs.getUsableSpace()
            );
        }

        /*
         * Network
         */
        long rx = 0;
        long tx = 0;

        for (NetworkIF n : hal.getNetworkIFs()) {

            n.updateAttributes();

            rx += Math.max(
                    0,
                    n.getBytesRecv()
            );

            tx += Math.max(
                    0,
                    n.getBytesSent()
            );
        }

        OperatingSystem os =
                si.getOperatingSystem();

        Map<String, Object> m =
                new LinkedHashMap<>();

        /*
         * Endpoint identity
         */
        m.put(
                "machineId",
                machineId
        );

        m.put(
                "hostname",
                os.getNetworkParams()
                        .getHostName()
        );

        m.put(
                "ipAddress",
                localIp()
        );

        /*
         * Operating system
         */
        m.put(
                "os",
                os.getFamily()
        );

        m.put(
                "osVersion",
                os.getVersionInfo()
                        .toString()
        );

        m.put(
                "architecture",
                System.getProperty("os.arch")
        );

        /*
         * Hardware
         */
        m.put(
                "processor",
                cpu.getProcessorIdentifier()
                        .getName()
        );

        m.put(
                "cpuCores",
                cpu.getPhysicalProcessorCount()
        );

        m.put(
                "logicalProcessors",
                cpu.getLogicalProcessorCount()
        );

        /*
         * Resource usage
         */
        m.put(
                "cpuUsage",
                round(cpuPct)
        );

        m.put(
                "memoryUsage",
                round(
                        (
                                1.0
                                        - (double) mem.getAvailable()
                                        / (double) mem.getTotal()
                        ) * 100
                )
        );

        m.put(
                "diskUsage",
                totalDisk == 0
                        ? 0
                        : round(
                                (
                                        1.0
                                                - (double) freeDisk
                                                / (double) totalDisk
                                ) * 100
                        )
        );

        /*
         * Network
         */
        m.put(
                "networkReceived",
                rx
        );

        m.put(
                "networkSent",
                tx
        );

        /*
         * Processes / uptime
         */
        m.put(
                "processCount",
                os.getProcessCount()
        );

        m.put(
                "uptime",
                os.getSystemUptime()
        );

        /*
         * Telemetry timestamp
         */
        m.put(
                "timestamp",
                Instant.now().toString()
        );

        /*
         * Windows Security Events
         */
        m.put(
                "securityEvents",
                windowsSecurityEvents()
        );

        return m;
    }

    /**
     * Collect selected Windows Security events.
     *
     * Event IDs:
     *
     * 4624 = Successful logon
     * 4625 = Failed logon
     * 4688 = Process creation
     */
    private List<Map<String, Object>> windowsSecurityEvents() {

        List<Map<String, Object>> rows =
                new ArrayList<>();

        /*
         * Only run on Windows.
         */
        if (!System.getProperty(
                "os.name",
                ""
        ).toLowerCase().contains("win")) {

            return rows;
        }

        try {

            String query =
                    "*[System[(EventID=4625 or EventID=4624 or EventID=4688)]]";

            Process p = new ProcessBuilder(
                    "wevtutil",
                    "qe",
                    "Security",
                    "/q:" + query,
                    "/c:20",
                    "/rd:true",
                    "/f:xml"
            )
                    .redirectErrorStream(true)
                    .start();

            String out = new String(
                    p.getInputStream()
                            .readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
            );

            int exitCode = p.waitFor();

            /*
             * If Windows denied access or wevtutil failed,
             * report it instead of silently hiding the problem.
             */
            if (exitCode != 0) {

                System.err.println(
                        "Windows Security event collection failed. "
                                + "wevtutil exit code="
                                + exitCode
                );

                return rows;
            }

            /*
             * IMPORTANT:
             *
             * Windows returns:
             *
             * <Event xmlns='...'>
             *
             * rather than simply:
             *
             * <Event>
             *
             * Therefore the regex accepts attributes after <Event.
             */
            java.util.regex.Matcher block =
                    java.util.regex.Pattern
                            .compile(
                                    "<Event\\b[^>]*>(.*?)</Event>",
                                    java.util.regex.Pattern.DOTALL
                            )
                            .matcher(out);

            while (
                    block.find()
                            && rows.size() < 20
            ) {

                String x = block.group(1);

                /*
                 * Event ID
                 */
                String id = tag(
                        x,
                        "EventID"
                );

                /*
                 * Windows Event Record ID
                 */
                String record = tag(
                        x,
                        "EventRecordID"
                );

                if (id.isBlank()) {
                    continue;
                }

                Map<String, Object> e =
                        new LinkedHashMap<>();

                /*
                 * Event identity
                 */
                e.put(
                        "eventId",
                        id
                );

                /*
                 * Fingerprint prevents duplicate events
                 * from being inserted repeatedly.
                 */
                e.put(
                        "fingerprint",
                        machineId + "-" + record
                );

                /*
                 * Currently uses collection time.
                 */
                e.put(
                        "timestamp",
                        Instant.now().toString()
                );

                /*
                 * Category
                 */
                e.put(
                        "category",
                        "AUTHENTICATION"
                );

                /*
                 * Severity + message
                 */
                String sev = "INFO";

                String msg =
                        "Windows security event " + id;

                if ("4625".equals(id)) {

                    /*
                     * Failed Windows logon
                     */
                    sev = "HIGH";

                    msg =
                            "Failed Windows logon detected";

                } else if ("4688".equals(id)) {

                    /*
                     * Windows process creation
                     */
                    sev = "MEDIUM";

                    msg =
                            "Windows process creation event detected";

                } else if ("4624".equals(id)) {

                    /*
                     * Successful Windows logon
                     */
                    sev = "INFO";

                    msg =
                            "Successful Windows logon detected";
                }

                e.put(
                        "severity",
                        sev
                );

                e.put(
                        "message",
                        msg
                );

                /*
                 * Additional event metadata
                 */
                e.put(
                        "metadata",
                        "EventRecordID=" + record
                );

                rows.add(e);
            }

            System.out.println(
                    "Windows security events collected: "
                            + rows.size()
            );

        } catch (Exception e) {

            /*
             * Don't kill the entire endpoint agent
             * if Windows event collection fails.
             */
            System.err.println(
                    "Windows Security event collection error: "
                            + e.getMessage()
            );
        }

        return rows;
    }

    /**
     * Extract a simple XML tag value.
     */
    private static String tag(
            String xml,
            String name
    ) {

        java.util.regex.Matcher m =
                java.util.regex.Pattern
                        .compile(
                                "<"
                                        + name
                                        + "[^>]*>(.*?)</"
                                        + name
                                        + ">",
                                java.util.regex.Pattern.DOTALL
                        )
                        .matcher(xml);

        return m.find()
                ? m.group(1).trim()
                : "";
    }

    /**
     * Send telemetry to SentinelCore backend.
     */
    private void send(
            Map<String, Object> payload
    ) throws Exception {

        String json =
                mapper.writeValueAsString(payload);

        HttpRequest req =
                HttpRequest.newBuilder(
                        URI.create(
                                baseUrl
                                        + "/api/agent/telemetry"
                        )
                )
                        .timeout(
                                Duration.ofSeconds(15)
                        )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(json)
                        )
                        .build();

        HttpResponse<String> res =
                http.send(
                        req,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (
                res.statusCode() < 200
                        || res.statusCode() >= 300
        ) {

            throw new IOException(
                    "HTTP "
                            + res.statusCode()
                            + " "
                            + res.body()
            );
        }

        System.out.println(
                "Telemetry accepted: "
                        + res.body()
        );
    }

    /**
     * Load or create persistent machine ID.
     */
    private String loadMachineId() {

        try {

            Path p =
                    Paths.get(
                            System.getProperty(
                                    "user.home"
                            ),
                            ".sentinelcore",
                            "agent-machine-id"
                    );

            if (Files.exists(p)) {

                return Files.readString(
                        p
                ).trim();
            }

            String id =
                    UUID.randomUUID().toString();

            Files.createDirectories(
                    p.getParent()
            );

            Files.writeString(
                    p,
                    id
            );

            return id;

        } catch (Exception e) {

            return "volatile-"
                    + UUID.randomUUID();
        }
    }

    /**
     * Get local IP address.
     */
    private String localIp() {

        try {

            return InetAddress
                    .getLocalHost()
                    .getHostAddress();

        } catch (Exception e) {

            return "";
        }
    }

    /**
     * Round percentage to 0-100.
     */
    private static int round(
            double v
    ) {

        return (int) Math.max(
                0,
                Math.min(
                        100,
                        Math.round(v)
                )
        );
    }

    /**
     * Read environment variable.
     */
    private static String env(
            String k,
            String d
    ) {

        String v =
                System.getenv(k);

        return v == null || v.isBlank()
                ? d
                : v;
    }
}