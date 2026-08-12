package com.relay.api.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_platform_results")
public class PostPlatformResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @Column(nullable = false, length = 32)
    private String platform;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String message;

    protected PostPlatformResultEntity() {}

    public PostPlatformResultEntity(PostEntity post, String platform, String status, String message) {
        this.post = post;
        this.platform = platform;
        this.status = status;
        this.message = message;
    }

    public String getPlatform() {
        return platform;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
