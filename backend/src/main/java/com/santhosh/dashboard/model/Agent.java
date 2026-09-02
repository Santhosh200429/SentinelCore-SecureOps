package com.santhosh.dashboard.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_agents", indexes = {
    @Index(name = "idx_agent_owner", columnList = "owner_username"),
    @Index(name = "idx_agent_token_hash", columnList = "token_hash", unique = true)
})
public class Agent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="owner_username", nullable=false) private String ownerUsername;
    @Column(nullable=false) private String name;
    @Column(name="token_hash", nullable=false, unique=true, length=64) private String tokenHash;
    @Column(nullable=false) private boolean active = true;
    @Column(nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastSeen;
    private Long assetId;

    public Agent() {}
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public String getOwnerUsername(){return ownerUsername;} public void setOwnerUsername(String v){ownerUsername=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getLastSeen(){return lastSeen;} public void setLastSeen(LocalDateTime v){lastSeen=v;}
    public Long getAssetId(){return assetId;} public void setAssetId(Long v){assetId=v;}
}
