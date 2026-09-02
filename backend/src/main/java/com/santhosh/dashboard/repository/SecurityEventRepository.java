package com.santhosh.dashboard.repository;
import com.santhosh.dashboard.model.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface SecurityEventRepository extends JpaRepository<SecurityEvent,Long>{
    boolean existsByFingerprint(String fingerprint);
    List<SecurityEvent> findTop100ByOwnerUsernameOrderByTimestampDesc(String ownerUsername);
    long countByOwnerUsernameAndAssetIdAndEventIdAndTimestampAfter(String ownerUsername, Long assetId, String eventId, LocalDateTime since);
}
