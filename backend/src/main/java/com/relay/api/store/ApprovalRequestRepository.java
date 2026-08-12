package com.relay.api.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, String> {
    List<ApprovalRequestEntity> findByAdminIdAndStatusOrderByCreatedAtDesc(String adminId, String status);
    List<ApprovalRequestEntity> findByMemberIdOrderByCreatedAtDesc(String memberId);
    List<ApprovalRequestEntity> findByAdminIdOrderByCreatedAtDesc(String adminId);
    long countByMemberIdAndStatus(String memberId, String status);
}
