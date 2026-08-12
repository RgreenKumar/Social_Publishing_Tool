package com.relay.api.store;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
public class PostEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String mediaName;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PostPlatformResultEntity> platforms = new ArrayList<>();

    protected PostEntity() {}

    public PostEntity(String id, String userId, String content, String mediaName, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.mediaName = mediaName;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public String getMediaName() {
        return mediaName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PostPlatformResultEntity> getPlatforms() {
        return platforms;
    }

    public void addPlatformResult(String platform, String status, String message) {
        platforms.add(new PostPlatformResultEntity(this, platform, status, message));
    }
}
