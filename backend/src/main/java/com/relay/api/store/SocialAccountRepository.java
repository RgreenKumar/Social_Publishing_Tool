package com.relay.api.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccountEntity, String> {
    List<SocialAccountEntity> findByUserIdOrderByPlatformAsc(String userId);
    Optional<SocialAccountEntity> findByUserIdAndPlatformIgnoreCase(String userId, String platform);
}
