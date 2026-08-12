package com.relay.api.post;

import com.relay.api.store.ScheduledPostEntity;
import com.relay.api.store.ScheduledPostRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledPostService {

    public record ScheduledPublish(
        String id,
        String userId,
        String content,
        List<String> platforms,
        String mediaName,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType,
        String scheduledAt,
        String createdAt,
        String status
    ) {}

    public record ScheduledPublishSummary(
        String id,
        String content,
        List<String> platforms,
        String scheduledAt,
        String status
    ) {}

    private final PostPublishService postPublishService;
    private final ScheduledPostRepository scheduledPosts;

    public ScheduledPostService(
        PostPublishService postPublishService,
        ScheduledPostRepository scheduledPosts
    ) {
        this.postPublishService = postPublishService;
        this.scheduledPosts = scheduledPosts;
    }

    @Transactional
    public ScheduledPublish schedule(
        String userId,
        String content,
        List<String> platforms,
        String mediaName,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType,
        Instant scheduledAt
    ) {
        ScheduledPostEntity entity = new ScheduledPostEntity(
            UUID.randomUUID().toString(),
            userId,
            content,
            String.join(",", platforms),
            mediaName,
            imageBytes,
            imageFilename,
            imageContentType,
            scheduledAt
        );
        scheduledPosts.save(entity);
        return toRecord(entity);
    }

    public List<ScheduledPublishSummary> listForUser(String userId) {
        return scheduledPosts.findByUserIdOrderByScheduledAtDesc(userId).stream()
            .map(this::toSummary)
            .toList();
    }

    public ScheduledPublishSummary getForUser(String userId, String id) {
        ScheduledPostEntity entity = scheduledPosts.findById(id)
            .filter(e -> e.getUserId().equals(userId))
            .orElseThrow(() -> new IllegalArgumentException("Scheduled post not found"));
        return toSummary(entity);
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void processDue() {
        List<ScheduledPostEntity> due = scheduledPosts
            .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc("scheduled", Instant.now());

        for (ScheduledPostEntity item : due) {
            item.setStatus("processing");
            scheduledPosts.save(item);

            List<String> platforms = Arrays.stream(item.getPlatforms().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

            try {
                postPublishService.publishNow(
                    item.getUserId(),
                    item.getContent(),
                    platforms,
                    item.getMediaName(),
                    item.getImageBytes(),
                    item.getImageFilename(),
                    item.getImageContentType()
                );
                item.setStatus("done");
            } catch (Exception ex) {
                item.setStatus("failed");
            }
            scheduledPosts.save(item);
        }
    }

    private ScheduledPublish toRecord(ScheduledPostEntity entity) {
        List<String> platforms = Arrays.stream(entity.getPlatforms().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        return new ScheduledPublish(
            entity.getId(),
            entity.getUserId(),
            entity.getContent(),
            platforms,
            entity.getMediaName(),
            entity.getImageBytes(),
            entity.getImageFilename(),
            entity.getImageContentType(),
            entity.getScheduledAt().toString(),
            entity.getCreatedAt().toString(),
            entity.getStatus()
        );
    }

    private ScheduledPublishSummary toSummary(ScheduledPostEntity entity) {
        List<String> platforms = Arrays.stream(entity.getPlatforms().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        return new ScheduledPublishSummary(
            entity.getId(),
            entity.getContent(),
            platforms,
            entity.getScheduledAt().toString(),
            entity.getStatus()
        );
    }
}
