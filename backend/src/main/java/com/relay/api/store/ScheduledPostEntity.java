package com.relay.api.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scheduled_posts")
public class ScheduledPostEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Comma-separated platforms */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String platforms;

    private String mediaName;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BYTEA")
    private byte[] imageBytes;

    private String imageFilename;

    private String imageContentType;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, length = 32)
    private String status = "scheduled";

    protected ScheduledPostEntity() {}

    public ScheduledPostEntity(
        String id,
        String userId,
        String content,
        String platforms,
        String mediaName,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType,
        Instant scheduledAt
    ) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.platforms = platforms;
        this.mediaName = mediaName;
        this.imageBytes = imageBytes;
        this.imageFilename = imageFilename;
        this.imageContentType = imageContentType;
        this.scheduledAt = scheduledAt;
        this.createdAt = Instant.now();
        this.status = "scheduled";
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

    public String getPlatforms() {
        return platforms;
    }

    public String getMediaName() {
        return mediaName;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
