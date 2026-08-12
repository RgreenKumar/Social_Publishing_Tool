package com.relay.api.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, String> {}
