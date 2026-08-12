package com.relay.api.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auth_tokens")
public class AuthTokenEntity {

    @Id
    @Column(length = 64)
    private String token;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuthTokenEntity() {}

    public AuthTokenEntity(String token, String userId) {
        this.token = token;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public String getToken() {
        return token;
    }

    public String getUserId() {
        return userId;
    }
}
