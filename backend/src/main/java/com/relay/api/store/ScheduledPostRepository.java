package com.relay.api.store;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledPostRepository extends JpaRepository<ScheduledPostEntity, String> {
    List<ScheduledPostEntity> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
        String status,
        Instant scheduledAt
    );

    List<ScheduledPostEntity> findByUserIdOrderByScheduledAtDesc(String userId);
}
