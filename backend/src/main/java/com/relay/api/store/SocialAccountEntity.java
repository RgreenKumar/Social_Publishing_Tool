package com.relay.api.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints = @UniqueConstraint(columnNames = { "userId", "platform" })
)
public class SocialAccountEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 32)
    private String platform;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean connected;

    private Instant connectedAt;

    protected SocialAccountEntity() {}

    public SocialAccountEntity(
        String id,
        String userId,
        String platform,
        String displayName,
        boolean connected,
        Instant connectedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.platform = platform;
        this.displayName = displayName;
        this.connected = connected;
        this.connectedAt = connectedAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }
}
