package com.relay.api.post;

import com.relay.api.facebook.FacebookService;
import com.relay.api.instagram.InstagramService;
import com.relay.api.linkedin.LinkedInService;
import com.relay.api.store.InMemoryStore;
import com.relay.api.store.InMemoryStore.PlatformResult;
import com.relay.api.store.InMemoryStore.Post;
import com.relay.api.store.InMemoryStore.SocialAccount;
import com.relay.api.threads.ThreadsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PostPublishService {

    private final InMemoryStore store;
    private final LinkedInService linkedInService;
    private final FacebookService facebookService;
    private final InstagramService instagramService;
    private final ThreadsService threadsService;

    public PostPublishService(
        InMemoryStore store,
        LinkedInService linkedInService,
        FacebookService facebookService,
        InstagramService instagramService,
        ThreadsService threadsService
    ) {
        this.store = store;
        this.linkedInService = linkedInService;
        this.facebookService = facebookService;
        this.instagramService = instagramService;
        this.threadsService = threadsService;
    }

    public Post publishNow(
        String userId,
        String content,
        List<String> platforms,
        String mediaName,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType
    ) {
        boolean hasImage = imageBytes != null && imageBytes.length > 0;
        String trimmed = content == null ? "" : content.trim();

        List<SocialAccount> accounts = store.accountsFor(userId);
        List<PlatformResult> results = new ArrayList<>();

        for (String platform : platforms) {
            String key = platform.toLowerCase();
            boolean connected = accounts.stream()
                .anyMatch(a -> a.platform().equals(key) && a.connected());

            if (!connected) {
                results.add(new PlatformResult(key, "failed", "Account not connected"));
                continue;
            }

            if ("linkedin".equals(key)) {
                try {
                    linkedInService.publish(userId, trimmed, imageBytes, imageFilename, imageContentType);
                    results.add(new PlatformResult(
                        key,
                        "success",
                        hasImage ? "Published image to LinkedIn" : "Published to LinkedIn"
                    ));
                } catch (Exception ex) {
                    results.add(new PlatformResult(
                        key,
                        "failed",
                        ex.getMessage() != null ? ex.getMessage() : "LinkedIn publish failed"
                    ));
                }
                continue;
            }

            if ("facebook".equals(key)) {
                try {
                    facebookService.publish(trimmed, imageBytes, imageFilename, imageContentType);
                    results.add(new PlatformResult(
                        key,
                        "success",
                        hasImage ? "Published photo to Facebook Page" : "Published to Facebook Page"
                    ));
                } catch (Exception ex) {
                    results.add(new PlatformResult(
                        key,
                        "failed",
                        ex.getMessage() != null ? ex.getMessage() : "Facebook publish failed"
                    ));
                }
                continue;
            }

            if ("instagram".equals(key)) {
                try {
                    String message = instagramService.publish(
                        trimmed,
                        imageBytes,
                        imageFilename,
                        imageContentType
                    );
                    results.add(new PlatformResult(key, "success", message));
                } catch (Exception ex) {
                    results.add(new PlatformResult(
                        key,
                        "failed",
                        ex.getMessage() != null ? ex.getMessage() : "Instagram publish failed"
                    ));
                }
                continue;
            }

            if ("threads".equals(key)) {
                try {
                    String message = threadsService.publish(trimmed, imageBytes, imageFilename, imageContentType);
                    results.add(new PlatformResult(key, "success", message));
                } catch (Exception ex) {
                    results.add(new PlatformResult(
                        key,
                        "failed",
                        ex.getMessage() != null ? ex.getMessage() : "Threads publish failed"
                    ));
                }
                continue;
            }

            results.add(new PlatformResult(
                key,
                "success",
                hasImage ? "Published with image (simulated)" : "Published (simulated)"
            ));
        }

        Post post = new Post(
            UUID.randomUUID().toString(),
            userId,
            trimmed.isEmpty() ? "(image)" : trimmed,
            mediaName,
            Instant.now().toString(),
            results
        );
        store.savePost(post);
        return post;
    }
}
