package com.relay.api.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InMemoryStore {

    public record User(
        String id,
        String name,
        String email,
        String username,
        String password,
        String role,
        String adminId
    ) {
        public boolean isAdmin() {
            return UserEntity.ROLE_ADMIN.equalsIgnoreCase(role);
        }

        public boolean isMember() {
            return UserEntity.ROLE_MEMBER.equalsIgnoreCase(role);
        }

        public String workspaceOwnerId() {
            if (isMember() && adminId != null && !adminId.isBlank()) {
                return adminId;
            }
            return id;
        }
    }

    public record SocialAccount(
        String id,
        String userId,
        String platform,
        String displayName,
        boolean connected,
        String connectedAt
    ) {}

    public record PlatformResult(String platform, String status, String message) {}

    public record Post(
        String id,
        String userId,
        String content,
        String mediaName,
        String createdAt,
        List<PlatformResult> platforms
    ) {}

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final SocialAccountRepository accounts;
    private final PostRepository posts;

    public InMemoryStore(
        UserRepository users,
        AuthTokenRepository tokens,
        SocialAccountRepository accounts,
        PostRepository posts
    ) {
        this.users = users;
        this.tokens = tokens;
        this.accounts = accounts;
        this.posts = posts;
    }

    @Transactional
    public User signup(String name, String email, String password) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        UserEntity entity = new UserEntity(
            UUID.randomUUID().toString(),
            name,
            email.toLowerCase(),
            null,
            password,
            UserEntity.ROLE_ADMIN,
            null
        );
        users.save(entity);
        seedAccounts(entity.getId());
        return toUser(entity);
    }

    @Transactional
    public User createTeamMember(String adminId, String name, String username, String password) {
        String normalized = username.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (users.existsByUsernameIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Username already taken");
        }
        String syntheticEmail = normalized + "@team.local";
        if (users.existsByEmailIgnoreCase(syntheticEmail)) {
            throw new IllegalArgumentException("Username already taken");
        }
        UserEntity entity = new UserEntity(
            UUID.randomUUID().toString(),
            name.trim(),
            syntheticEmail,
            normalized,
            password,
            UserEntity.ROLE_MEMBER,
            adminId
        );
        users.save(entity);
        return toUser(entity);
    }

    public List<User> membersForAdmin(String adminId) {
        return users.findByAdminIdOrderByCreatedAtDesc(adminId).stream()
            .map(this::toUser)
            .toList();
    }

    @Transactional
    public void deleteMember(String adminId, String memberId) {
        UserEntity entity = users.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (!adminId.equals(entity.getAdminId())) {
            throw new IllegalArgumentException("Member not found");
        }
        users.delete(entity);
    }

    public Optional<User> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email).map(this::toUser);
    }

    public Optional<User> findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username).map(this::toUser);
    }

    public Optional<User> findByLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String trimmed = login.trim();
        Optional<User> byEmail = findByEmail(trimmed);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return findByUsername(trimmed);
    }

    public Optional<User> findById(String id) {
        return users.findById(id).map(this::toUser);
    }

    @Transactional
    public String issueToken(User user) {
        String token = UUID.randomUUID().toString();
        tokens.save(new AuthTokenEntity(token, user.id()));
        return token;
    }

    public Optional<User> userFromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findById(token)
            .flatMap(t -> users.findById(t.getUserId()))
            .map(this::toUser);
    }

    @Transactional
    public void revokeToken(String token) {
        tokens.deleteById(token);
    }

    @Transactional
    public List<SocialAccount> accountsFor(String userId) {
        ensureSeededPlatforms(userId);
        return accounts.findByUserIdOrderByPlatformAsc(userId).stream()
            .map(this::toAccount)
            .toList();
    }

    @Transactional
    public SocialAccount connect(String userId, String platform, String displayName) {
        ensureSeededPlatforms(userId);
        SocialAccountEntity entity = accounts.findByUserIdAndPlatformIgnoreCase(userId, platform)
            .orElseGet(() -> new SocialAccountEntity(
                UUID.randomUUID().toString(),
                userId,
                platform.toLowerCase(),
                defaultDisplay(platform),
                false,
                null
            ));
        entity.setDisplayName(
            displayName == null || displayName.isBlank() ? defaultDisplay(platform) : displayName
        );
        entity.setConnected(true);
        entity.setConnectedAt(Instant.now());
        return toAccount(accounts.save(entity));
    }

    @Transactional
    public SocialAccount disconnect(String userId, String accountId) {
        SocialAccountEntity entity = accounts.findById(accountId)
            .filter(a -> a.getUserId().equals(userId))
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        entity.setConnected(false);
        entity.setConnectedAt(null);
        return toAccount(accounts.save(entity));
    }

    @Transactional
    public void savePost(Post post) {
        PostEntity entity = new PostEntity(
            post.id(),
            post.userId(),
            post.content(),
            post.mediaName(),
            Instant.parse(post.createdAt())
        );
        if (post.platforms() != null) {
            for (PlatformResult result : post.platforms()) {
                entity.addPlatformResult(result.platform(), result.status(), result.message());
            }
        }
        posts.save(entity);
    }

    public List<Post> postsFor(String userId) {
        return posts.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toPost)
            .toList();
    }

    private void seedAccounts(String userId) {
        for (String platform : List.of("facebook", "instagram", "threads", "linkedin")) {
            accounts.save(new SocialAccountEntity(
                UUID.randomUUID().toString(),
                userId,
                platform,
                defaultDisplay(platform),
                false,
                null
            ));
        }
    }

    private void ensureSeededPlatforms(String userId) {
        for (String platform : List.of("facebook", "instagram", "threads", "linkedin")) {
            if (accounts.findByUserIdAndPlatformIgnoreCase(userId, platform).isEmpty()) {
                accounts.save(new SocialAccountEntity(
                    UUID.randomUUID().toString(),
                    userId,
                    platform,
                    defaultDisplay(platform),
                    false,
                    null
                ));
            }
        }
    }

    private static String defaultDisplay(String platform) {
        return switch (platform.toLowerCase()) {
            case "facebook" -> "My Facebook Page";
            case "instagram" -> "@instagram";
            case "threads" -> "@my.threads";
            case "linkedin" -> "My LinkedIn";
            default -> platform;
        };
    }

    private User toUser(UserEntity entity) {
        String role = entity.getRole();
        if (role == null || role.isBlank()) {
            role = UserEntity.ROLE_ADMIN;
        }
        return new User(
            entity.getId(),
            entity.getName(),
            entity.getEmail(),
            entity.getUsername(),
            entity.getPassword(),
            role,
            entity.getAdminId()
        );
    }

    private SocialAccount toAccount(SocialAccountEntity entity) {
        return new SocialAccount(
            entity.getId(),
            entity.getUserId(),
            entity.getPlatform(),
            entity.getDisplayName(),
            entity.isConnected(),
            entity.getConnectedAt() == null ? null : entity.getConnectedAt().toString()
        );
    }

    private Post toPost(PostEntity entity) {
        List<PlatformResult> results = new ArrayList<>();
        for (PostPlatformResultEntity r : entity.getPlatforms()) {
            results.add(new PlatformResult(r.getPlatform(), r.getStatus(), r.getMessage()));
        }
        return new Post(
            entity.getId(),
            entity.getUserId(),
            entity.getContent(),
            entity.getMediaName(),
            entity.getCreatedAt().toString(),
            results
        );
    }
}
