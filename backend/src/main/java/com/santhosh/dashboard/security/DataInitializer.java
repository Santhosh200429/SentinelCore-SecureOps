package com.santhosh.dashboard.security;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.santhosh.dashboard.model.Alert;
import com.santhosh.dashboard.model.Asset;
import com.santhosh.dashboard.model.Incident;
import com.santhosh.dashboard.model.Permission;
import com.santhosh.dashboard.model.Role;
import com.santhosh.dashboard.model.User;
import com.santhosh.dashboard.model.Vulnerability;
import com.santhosh.dashboard.repository.AlertRepository;
import com.santhosh.dashboard.repository.AssetRepository;
import com.santhosh.dashboard.repository.IncidentRepository;
import com.santhosh.dashboard.repository.PermissionRepository;
import com.santhosh.dashboard.repository.RoleRepository;
import com.santhosh.dashboard.repository.UserRepository;
import com.santhosh.dashboard.repository.VulnerabilityRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final AssetRepository assetRepository;
    private final VulnerabilityRepository vulnerabilityRepository;

    @Value("${app.data.mode:live}")
    private String dataMode;

    @Value("${BOOTSTRAP_ADMIN_ENABLED:true}")
    private boolean bootstrapAdminEnabled;

    @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}")
    private String bootstrapAdminUsername;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:admin123}")
    private String bootstrapAdminPassword;

    @Value("${BOOTSTRAP_ADMIN_EMAIL:admin@sentinelcore.com}")
    private String bootstrapAdminEmail;

    public DataInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder,
            IncidentRepository incidentRepository,
            AlertRepository alertRepository,
            AssetRepository assetRepository,
            VulnerabilityRepository vulnerabilityRepository) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.assetRepository = assetRepository;
        this.vulnerabilityRepository = vulnerabilityRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {

        // ─────────────────────────────────────────────────────────────
        // 1. Seed permissions
        // ─────────────────────────────────────────────────────────────

        String[] allPerms = {
                "USER_MANAGE",
                "ROLE_ASSIGN",

                "ASSET_CREATE",
                "ASSET_EDIT",
                "ASSET_DELETE",
                "ASSET_VIEW",

                "INCIDENT_VIEW",
                "INCIDENT_CREATE",
                "INCIDENT_MANAGE",
                "INCIDENT_RESOLVE",
                "INCIDENT_DELETE",

                "SERVER_RESTART",
                "CLUSTER_SCALE",
                "CLOUD_MODIFY",

                "VULN_MANAGE",
                "COMPLIANCE_VIEW",
                "REPORT_EXPORT",
                "AUDIT_VIEW",

                "INTEGRATION_CONFIG",
                "SETTINGS_ACCESS"
        };

        // Cache permissions
        java.util.Map<String, Permission> permMap =
                permissionRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                Permission::getName,
                                p -> p,
                                (p1, p2) -> p1
                        ));

        for (String permissionName : allPerms) {

            if (!permMap.containsKey(permissionName)) {

                Permission newPermission =
                        permissionRepository.save(
                                new Permission(permissionName)
                        );

                permMap.put(permissionName, newPermission);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 2. Seed roles
        // ─────────────────────────────────────────────────────────────

        java.util.Map<String, Role> roleMap =
                roleRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                Role::getName,
                                r -> r,
                                (r1, r2) -> r1
                        ));

        // Super Admin
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_SUPER_ADMIN",
                allPerms
        );

        // Admin
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_ADMIN",

                "ASSET_CREATE",
                "ASSET_EDIT",
                "ASSET_DELETE",
                "ASSET_VIEW",

                "INCIDENT_VIEW",
                "INCIDENT_CREATE",
                "INCIDENT_MANAGE",
                "INCIDENT_RESOLVE",
                "INCIDENT_DELETE",

                "SERVER_RESTART",
                "CLUSTER_SCALE",
                "CLOUD_MODIFY",

                "VULN_MANAGE",
                "COMPLIANCE_VIEW",
                "REPORT_EXPORT",
                "AUDIT_VIEW",

                "USER_MANAGE",
                "ROLE_ASSIGN",
                "SETTINGS_ACCESS",
                "INTEGRATION_CONFIG"
        );

        // SOC Manager
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_SOC_MANAGER",

                "ASSET_VIEW",

                "INCIDENT_VIEW",
                "INCIDENT_CREATE",
                "INCIDENT_MANAGE",
                "INCIDENT_RESOLVE",

                "REPORT_EXPORT",
                "AUDIT_VIEW"
        );

        // Security Analyst
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_SECURITY_ANALYST",

                "ASSET_VIEW",

                "INCIDENT_VIEW",
                "INCIDENT_CREATE",
                "INCIDENT_MANAGE",

                "VULN_MANAGE",
                "REPORT_EXPORT"
        );

        // Incident Responder
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_INCIDENT_RESPONDER",

                "ASSET_VIEW",

                "INCIDENT_VIEW",
                "INCIDENT_MANAGE",
                "INCIDENT_RESOLVE",

                "AUDIT_VIEW"
        );

        // Infrastructure Engineer
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_INFRA_ENGINEER",

                "ASSET_VIEW",
                "ASSET_CREATE",
                "ASSET_EDIT",
                "ASSET_DELETE",

                "INCIDENT_VIEW",

                "SERVER_RESTART",
                "CLUSTER_SCALE",
                "CLOUD_MODIFY",

                "REPORT_EXPORT"
        );

        // DevSecOps
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_DEVSECOPS",

                "ASSET_VIEW",
                "ASSET_CREATE",
                "ASSET_EDIT",

                "INCIDENT_VIEW",
                "INCIDENT_CREATE",
                "INCIDENT_MANAGE",

                "VULN_MANAGE",

                "SERVER_RESTART",
                "CLUSTER_SCALE",

                "REPORT_EXPORT"
        );

        // Auditor
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_AUDITOR",

                "ASSET_VIEW",
                "INCIDENT_VIEW",

                "AUDIT_VIEW",
                "COMPLIANCE_VIEW",
                "REPORT_EXPORT"
        );

        // Viewer
        seedOrUpdateRole(
                roleMap,
                permMap,
                "ROLE_VIEWER",

                "ASSET_VIEW",
                "INCIDENT_VIEW"
        );

        // ─────────────────────────────────────────────────────────────
        // 3. Bootstrap Super Admin
        // ─────────────────────────────────────────────────────────────

        if (bootstrapAdminEnabled) {

    User admin = userRepository.findByUsername(bootstrapAdminUsername)
            .orElse(null);

    Role superAdminRole = roleMap.get("ROLE_SUPER_ADMIN");

    if (admin == null) {

        admin = new User(
                bootstrapAdminUsername,
                passwordEncoder.encode(bootstrapAdminPassword),
                bootstrapAdminEmail
        );

        admin.setFirstName("System");
        admin.setLastName("Administrator");
        admin.setOrganization("SentinelCore");

        if (superAdminRole != null) {
            admin.getRoles().add(superAdminRole);
        }

        userRepository.save(admin);

        System.out.println(
                "[SentinelCore] Bootstrap admin created: "
                        + bootstrapAdminUsername
        );

    } else {

        // Existing admin: reset password using BCrypt
        admin.setPassword(
                passwordEncoder.encode(bootstrapAdminPassword)
        );

        admin.setEmail(bootstrapAdminEmail);
        admin.setEnabled(true);
        admin.setLocked(false);

        if (superAdminRole != null && !admin.getRoles().contains(superAdminRole)) {
            admin.getRoles().add(superAdminRole);
        }

        userRepository.save(admin);

        System.out.println(
                "[SentinelCore] Bootstrap admin password updated: "
                        + bootstrapAdminUsername
        );
    }
}

        // ─────────────────────────────────────────────────────────────
        // 4. Bootstrap Demo Users (demo mode only)
        // ─────────────────────────────────────────────────────────────

        if (isDemoMode()) {
        Role viewerRole = roleMap.get("ROLE_VIEWER");

        // Demo User 1
        seedDemoUser(
                "mithra",
                "mithra123",
                "mithra@sentinelcore.local",
                "Mithra",
                "Demo",
                viewerRole
        );

        // Demo User 2
        seedDemoUser(
                "sandy",
                "santhosh123",
                "sandy@sentinelcore.local",
                "Sandy",
                "Demo",
                viewerRole
        );

        // Demo User 3
        seedDemoUser(
                "aniket",
                "aniket123",
                "aniket@sentinelcore.local",
                "Aniket",
                "Demo",
                viewerRole
        );

        // Demo User 4
        seedDemoUser(
                "raheema",
                "raheema123",
                "raheema@sentinelcore.local",
                "Raheema",
                "Demo",
                viewerRole
        );
        }

        // ─────────────────────────────────────────────────────────────
        // 5-8. Operational demo data (demo mode only)
        // ─────────────────────────────────────────────────────────────

        if (isDemoMode() && assetRepository.count() == 0) {

            assetRepository.save(new Asset(null, "WEB-SERVER-01", "192.168.1.10",
                    "Web Server", "Active", 42, 58, 51, 35, 99.2, "Chennai"));

            assetRepository.save(new Asset(null, "DB-SERVER-01", "192.168.1.20",
                    "Database Server", "Active", 61, 67, 72, 44, 98.7, "Bengaluru"));

            assetRepository.save(new Asset(null, "FIREWALL-01", "192.168.1.1",
                    "Firewall", "Active", 28, 39, 45, 52, 99.8, "Mumbai"));

            assetRepository.save(new Asset(null, "BACKUP-SERVER-01", "192.168.1.30",
                    "Backup Server", "Maintenance", 18, 31, 64, 22, 96.4, "Delhi"));

            assetRepository.save(new Asset(null, "ENDPOINT-01", "192.168.1.40",
                    "Endpoint", "Offline", 0, 0, 25, 0, 0.0, "Hyderabad"));

            System.out.println("[SentinelCore] Demo assets created: 5");
        }

        // ─────────────────────────────────────────────────────────────
        // 6. Seed Incidents
        // ─────────────────────────────────────────────────────────────

        if (isDemoMode() && incidentRepository.count() == 0) {

            Incident i1 = new Incident();

            i1.setIncidentId("INC-889");
            i1.setTitle("Failed Login Attempts");
            i1.setDescription(
                    "Multiple failed logins detected."
            );
            i1.setSeverity("Critical");
            i1.setStatus("Open");
            i1.setAssignedTeam("Security Team");
            i1.setAssignedTo("John");
            i1.setSlaHours(2);
            i1.setCreatedAt(LocalDateTime.now());

            Incident i2 = new Incident();

            i2.setIncidentId("INC-888");
            i2.setTitle("Kubernetes Cluster Alert");
            i2.setDescription(
                    "High CPU usage detected."
            );
            i2.setSeverity("High");
            i2.setStatus("Investigating");
            i2.setAssignedTeam("SOC Team");
            i2.setAssignedTo("Alice");
            i2.setSlaHours(4);
            i2.setCreatedAt(LocalDateTime.now());

            incidentRepository.save(i1);
            incidentRepository.save(i2);
        }

        // ─────────────────────────────────────────────────────────────
        // 7. Seed Alerts
        // ─────────────────────────────────────────────────────────────

        if (isDemoMode() && alertRepository.count() == 0) {

            alertRepository.save(
                    new Alert(
                            "DB-SRV-12 partition close to full",
                            "Warning",
                            "DB-SRV-12",
                            LocalDateTime.now().minusMinutes(15)
                    )
            );

            alertRepository.save(
                    new Alert(
                            "DDoS Attempt Blocked on Gateway",
                            "Critical",
                            "FW-GW-03",
                            LocalDateTime.now().minusMinutes(35)
                    )
            );

            alertRepository.save(
                    new Alert(
                            "Unusual outbound traffic on APP-SRV-47",
                            "Critical",
                            "APP-SRV-47",
                            LocalDateTime.now().minusMinutes(50)
                    )
            );
        }

        // ─────────────────────────────────────────────────────────────
        // 8. Seed Vulnerabilities
        // ─────────────────────────────────────────────────────────────

        if (isDemoMode() && vulnerabilityRepository.count() == 0) {

            vulnerabilityRepository.save(
                    new Vulnerability(
                            "CVE-2023-4863",
                            8.8,
                            92,
                            "14 Servers, 2 Clusters",
                            "Pending",
                            "Deploy Patch"
                    )
            );

            vulnerabilityRepository.save(
                    new Vulnerability(
                            "CVE-2023-5363",
                            6.5,
                            65,
                            "3 Firewalls",
                            "Scheduled",
                            "View Steps"
                    )
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper: Create Demo User
    // ─────────────────────────────────────────────────────────────────

    private void seedDemoUser(
            String username,
            String password,
            String email,
            String firstName,
            String lastName,
            Role role) {

        if (userRepository.findByUsername(username).isEmpty()) {

            User user = new User(
                    username,
                    passwordEncoder.encode(password),
                    email
            );

            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setOrganization("SentinelCore");

            if (role != null) {
                user.getRoles().add(role);
            }

            userRepository.save(user);

            System.out.println("[SentinelCore] Demo user created: " + username);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper: Seed / Update Role
    // ─────────────────────────────────────────────────────────────────

    private void seedOrUpdateRole(
            java.util.Map<String, Role> roleMap,
            java.util.Map<String, Permission> permMap,
            String roleName,
            String... perms) {

        Role role = roleMap.computeIfAbsent(
                roleName,
                k -> roleRepository.save(new Role(k))
        );

        Set<Permission> permissionSet =
                Arrays.stream(perms)
                        .map(permMap::get)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

        role.setPermissions(permissionSet);

        roleRepository.save(role);
    }
    private boolean isDemoMode() {
        return "demo".equalsIgnoreCase(dataMode);
    }

}