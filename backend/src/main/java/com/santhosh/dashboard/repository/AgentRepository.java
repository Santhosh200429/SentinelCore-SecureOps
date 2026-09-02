package com.santhosh.dashboard.repository;

import com.santhosh.dashboard.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    List<Agent> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
    Optional<Agent> findByTokenHashAndActiveTrue(String tokenHash);
    Optional<Agent> findByIdAndOwnerUsername(Long id, String ownerUsername);
}
