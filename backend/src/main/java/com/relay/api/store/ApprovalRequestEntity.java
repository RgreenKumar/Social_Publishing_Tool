package com.relay.api.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequestEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String adminId;

    @Column(nullable = false, length = 36)
    private String memberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String platforms;

    private String mediaName;

    /** Stored as BYTEA — avoid @Lob/OID which breaks on PostgreSQL ("Unable to access lob stream"). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BYTEA")
    private byte[] imageBytes;

    private String imageFilename;

    private String imageContentType;

    private Instant scheduledAt;

    @Column(nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Column(columnDefinition = "TEXT")
    private String adminNote;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    protected ApprovalRequestEntity() {}

    public ApprovalRequestEntity(
        String id,
        String adminId,
        String memberId,
        String content,
        String platforms,
        String mediaName,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType,
        Instant scheduledAt
    ) {
        this.id = id;
        this.adminId = adminId;
        this.memberId = memberId;
        this.content = content;
        this.platforms = platforms;
        this.mediaName = mediaName;
        this.imageBytes = imageBytes;
        this.imageFilename = imageFilename;
        this.imageContentType = imageContentType;
        this.scheduledAt = scheduledAt;
        this.status = STATUS_PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getAdminId() {
        return adminId;
    }

    public String getMemberId() {
        return memberId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
