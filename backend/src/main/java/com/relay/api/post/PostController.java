package com.relay.api.post;

import com.relay.api.auth.AuthController;
import com.relay.api.store.InMemoryStore;
import com.relay.api.store.InMemoryStore.Post;
import com.relay.api.store.InMemoryStore.User;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final AuthController authController;
    private final InMemoryStore store;
    private final PostPublishService postPublishService;
    private final ScheduledPostService scheduledPostService;

    public PostController(
        AuthController authController,
        InMemoryStore store,
        PostPublishService postPublishService,
        ScheduledPostService scheduledPostService
    ) {
        this.authController = authController;
        this.store = store;
        this.postPublishService = postPublishService;
        this.scheduledPostService = scheduledPostService;
    }

    public record PublishRequest(String content, String mediaName, List<String> platforms, String scheduledAt) {}
    public record ScheduledPublishResponse(
        String id,
        String status,
        String scheduledAt,
        String message,
        List<String> platforms
    ) {}

    @GetMapping
    public List<Post> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authController.requireUser(authorization);
        // Shared workspace history under admin
        return store.postsFor(user.workspaceOwnerId());
    }

    @GetMapping("/scheduled")
    public List<ScheduledPostService.ScheduledPublishSummary> listScheduled(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = authController.requireUser(authorization);
        return scheduledPostService.listForUser(user.workspaceOwnerId());
    }

    @GetMapping("/scheduled/{id}")
    public ScheduledPostService.ScheduledPublishSummary getScheduled(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id
    ) {
        User user = authController.requireUser(authorization);
        try {
            return scheduledPostService.getForUser(user.workspaceOwnerId(), id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object publishJson(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody PublishRequest body
    ) {
        return publish(
            authorization,
            body.content(),
            body.platforms(),
            body.mediaName(),
            body.scheduledAt(),
            null,
            null,
            null
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object publishMultipart(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "content", required = false) String content,
        @RequestParam("platforms") String platformsCsv,
        @RequestParam(value = "scheduledAt", required = false) String scheduledAt,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        List<String> platforms = Arrays.stream(platformsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        byte[] imageBytes = null;
        String imageFilename = null;
        String imageContentType = null;
        if (image != null && !image.isEmpty()) {
            try {
                imageBytes = image.getBytes();
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded image");
            }
            imageFilename = image.getOriginalFilename();
            imageContentType = image.getContentType();
        }

        return publish(
            authorization,
            content,
            platforms,
            imageFilename,
            scheduledAt,
            imageBytes,
            imageFilename,
            imageContentType
        );
    }

    private Object publish(
        String authorization,
        String content,
        List<String> platforms,
        String mediaName,
        String scheduledAt,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType
    ) {
        User user = authController.requireUser(authorization);
        if (user.isMember()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Team members must submit posts for admin approval"
            );
        }

        boolean hasImage = imageBytes != null && imageBytes.length > 0;
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty() && !hasImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content or image is required");
        }
        if (platforms == null || platforms.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "select at least one platform");
        }

        if (scheduledAt != null && !scheduledAt.isBlank()) {
            Instant scheduledInstant;
            try {
                scheduledInstant = Instant.parse(scheduledAt);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt must be a valid ISO datetime");
            }
            if (!scheduledInstant.isAfter(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt must be in the future");
            }
            ScheduledPostService.ScheduledPublish job = scheduledPostService.schedule(
                user.id(),
                trimmed,
                platforms,
                mediaName,
                imageBytes,
                imageFilename,
                imageContentType,
                scheduledInstant
            );
            return new ScheduledPublishResponse(
                job.id(),
                job.status(),
                job.scheduledAt(),
                "Post scheduled. It will auto-publish at the selected time.",
                platforms
            );
        }

        return postPublishService.publishNow(
            user.id(),
            trimmed,
            platforms,
            mediaName,
            imageBytes,
            imageFilename,
            imageContentType
        );
    }
}
