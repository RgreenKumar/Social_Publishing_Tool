package com.relay.api.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 16)
    private String role = ROLE_ADMIN;

    /** For team members: the admin who created them. Null for admins. */
    @Column(length = 36)
    private String adminId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected UserEntity() {}

    public UserEntity(
        String id,
        String name,
        String email,
        String username,
        String password,
        String role,
        String adminId
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.username = username;
        this.password = password;
        this.role = role;
        this.adminId = adminId;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getAdminId() {
        return adminId;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(role);
    }

    public boolean isMember() {
        return ROLE_MEMBER.equalsIgnoreCase(role);
    }

    /** Workspace owner id: admin's own id, or member's adminId. */
    public String workspaceOwnerId() {
        if (isMember() && adminId != null && !adminId.isBlank()) {
            return adminId;
        }
        return id;
    }
}
