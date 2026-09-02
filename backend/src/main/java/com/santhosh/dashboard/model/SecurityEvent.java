package com.santhosh.dashboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "security_events",
    indexes = {
        @Index(
            name = "idx_security_event_timestamp",
            columnList = "timestamp"
        ),
        @Index(
            name = "idx_security_event_owner",
            columnList = "owner_username"
        ),
        @Index(
            name = "idx_security_event_asset",
            columnList = "asset_id"
        )
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_security_event_fingerprint",
            columnNames = "fingerprint"
        )
    }
)
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    private String source;

    private String eventId;

    private String hostname;

    private String severity;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    private Long assetId;

    private String ownerUsername;

    private String username;

    private String remoteIp;

    @Column(length = 128)
    private String fingerprint;


    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    /**
     * Required by JPA/Hibernate.
     */
    public SecurityEvent() {
    }

    /**
     * Backward-compatible constructor used by
     * WindowsEventLogService.
     */
    public SecurityEvent(
            LocalDateTime timestamp,
            String source,
            String eventId,
            String hostname,
            String severity,
            String message,
            String category,
            String metadata) {

        this.timestamp = timestamp;
        this.source = source;
        this.eventId = eventId;
        this.hostname = hostname;
        this.severity = severity;
        this.message = message;
        this.category = category;
        this.metadata = metadata;
    }


    // =========================================================
    // ID
    // =========================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    // =========================================================
    // TIMESTAMP
    // =========================================================

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }


    // =========================================================
    // SOURCE
    // =========================================================

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }


    // =========================================================
    // EVENT ID
    // =========================================================

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }


    // =========================================================
    // HOSTNAME
    // =========================================================

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }


    // =========================================================
    // SEVERITY
    // =========================================================

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }


    // =========================================================
    // MESSAGE
    // =========================================================

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }


    // =========================================================
    // CATEGORY
    // =========================================================

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }


    // =========================================================
    // METADATA
    // =========================================================

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }


    // =========================================================
    // ASSET ID
    // =========================================================

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }


    // =========================================================
    // OWNER USERNAME
    // =========================================================

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }


    // =========================================================
    // USERNAME
    // =========================================================

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }


    // =========================================================
    // REMOTE IP
    // =========================================================

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }


    // =========================================================
    // FINGERPRINT
    // =========================================================

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
}