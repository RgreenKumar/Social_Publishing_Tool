package com.relay.api.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "linkedin_credentials")
public class LinkedInCredentialEntity {

    @Id
    @Column(length = 36)
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(nullable = false)
    private String personUrn;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected LinkedInCredentialEntity() {}

    public LinkedInCredentialEntity(String userId, String accessToken, String personUrn, String displayName) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.personUrn = personUrn;
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void update(String accessToken, String personUrn, String displayName) {
        this.accessToken = accessToken;
        this.personUrn = personUrn;
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getPersonUrn() {
        return personUrn;
    }

    public String getDisplayName() {
        return displayName;
    }
}
