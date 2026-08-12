package com.relay.api.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, String> {
    List<PostEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
