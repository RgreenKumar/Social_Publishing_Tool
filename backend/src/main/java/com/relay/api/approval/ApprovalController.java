package com.relay.api.approval;

import com.relay.api.auth.AuthController;
import com.relay.api.post.PostPublishService;
import com.relay.api.post.ScheduledPostService;
import com.relay.api.store.ApprovalRequestEntity;
import com.relay.api.store.ApprovalRequestRepository;
import com.relay.api.store.InMemoryStore;
import com.relay.api.store.InMemoryStore.User;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final AuthController authController;
    private final InMemoryStore store;
    private final ApprovalRequestRepository approvals;
    private final PostPublishService postPublishService;
    private final ScheduledPostService scheduledPostService;

    public ApprovalController(
        AuthController authController,
        InMemoryStore store,
        ApprovalRequestRepository approvals,
        PostPublishService postPublishService,
        ScheduledPostService scheduledPostService
    ) {
        this.authController = authController;
        this.store = store;
        this.approvals = approvals;
        this.postPublishService = postPublishService;
        this.scheduledPostService = scheduledPostService;
    }

    public record RejectRequest(String note) {}

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> submit(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "content", required = false) String content,
        @RequestParam("platforms") String platformsCsv,
        @RequestParam(value = "scheduledAt", required = false) String scheduledAt,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        User user = authController.requireUser(authorization);
        if (!user.isMember()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only team members submit posts for approval"
            );
        }
        if (user.adminId() == null || user.adminId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member is not linked to an admin");
        }

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

        boolean hasImage = imageBytes != null && imageBytes.length > 0;
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty() && !hasImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content or image is required");
        }
        if (platforms.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "select at least one platform");
        }

        Instant scheduledInstant = null;
        if (scheduledAt != null && !scheduledAt.isBlank()) {
            try {
                scheduledInstant = Instant.parse(scheduledAt);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt must be a valid ISO datetime");
            }
            if (!scheduledInstant.isAfter(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt must be in the future");
            }
        }

        ApprovalRequestEntity entity = new ApprovalRequestEntity(
            UUID.randomUUID().toString(),
            user.adminId(),
            user.id(),
            trimmed.isEmpty() ? "(image)" : trimmed,
            String.join(",", platforms),
            imageFilename,
            imageBytes,
            imageFilename,
            imageContentType,
            scheduledInstant
        );
        approvals.save(entity);
        return toView(entity);
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = authController.requireUser(authorization);
        if (!user.isMember()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members only");
        }
        return approvals.findByMemberIdOrderByCreatedAtDesc(user.id()).stream()
            .map(this::toView)
            .toList();
    }

    @GetMapping("/pending")
    public List<Map<String, Object>> pending(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User admin = authController.requireAdmin(authorization);
        return approvals
            .findByAdminIdAndStatusOrderByCreatedAtDesc(admin.id(), ApprovalRequestEntity.STATUS_PENDING)
            .stream()
            .map(this::toView)
            .toList();
    }

    @GetMapping
    public List<Map<String, Object>> allForAdmin(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User admin = authController.requireAdmin(authorization);
        return approvals.findByAdminIdOrderByCreatedAtDesc(admin.id()).stream()
            .map(this::toView)
            .toList();
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public Map<String, Object> approve(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id
    ) {
        User admin = authController.requireAdmin(authorization);
        ApprovalRequestEntity entity = approvals.findById(id)
            .filter(e -> e.getAdminId().equals(admin.id()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));

        if (!ApprovalRequestEntity.STATUS_PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is already " + entity.getStatus());
        }

        List<String> platforms = Arrays.stream(entity.getPlatforms().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        try {
            if (entity.getScheduledAt() != null) {
                scheduledPostService.schedule(
                    admin.id(),
                    entity.getContent(),
                    platforms,
                    entity.getMediaName(),
                    entity.getImageBytes(),
                    entity.getImageFilename(),
                    entity.getImageContentType(),
                    entity.getScheduledAt()
                );
            } else {
                postPublishService.publishNow(
                    admin.id(),
                    entity.getContent(),
                    platforms,
                    entity.getMediaName(),
                    entity.getImageBytes(),
                    entity.getImageFilename(),
                    entity.getImageContentType()
                );
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                ex.getMessage() != null ? ex.getMessage() : "Publish failed"
            );
        }

        entity.setStatus(ApprovalRequestEntity.STATUS_APPROVED);
        entity.setResolvedAt(Instant.now());
        approvals.save(entity);
        return toView(entity);
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id,
        @RequestBody(required = false) RejectRequest body
    ) {
        User admin = authController.requireAdmin(authorization);
        ApprovalRequestEntity entity = approvals.findById(id)
            .filter(e -> e.getAdminId().equals(admin.id()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));

        if (!ApprovalRequestEntity.STATUS_PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is already " + entity.getStatus());
        }

        entity.setStatus(ApprovalRequestEntity.STATUS_REJECTED);
        entity.setAdminNote(body != null ? body.note() : null);
        entity.setResolvedAt(Instant.now());
        approvals.save(entity);
        return toView(entity);
    }

    private Map<String, Object> toView(ApprovalRequestEntity entity) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", entity.getId());
        view.put("adminId", entity.getAdminId());
        view.put("memberId", entity.getMemberId());
        view.put("content", entity.getContent());
        view.put(
            "platforms",
            Arrays.stream(entity.getPlatforms().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList()
        );
        view.put("mediaName", entity.getMediaName());
        byte[] bytes = entity.getImageBytes();
        view.put(
            "hasImage",
            (bytes != null && bytes.length > 0)
                || (entity.getImageFilename() != null && !entity.getImageFilename().isBlank())
        );
        view.put(
            "scheduledAt",
            entity.getScheduledAt() == null ? null : entity.getScheduledAt().toString()
        );
        view.put("status", entity.getStatus());
        view.put("adminNote", entity.getAdminNote());
        view.put("createdAt", entity.getCreatedAt().toString());
        view.put(
            "resolvedAt",
            entity.getResolvedAt() == null ? null : entity.getResolvedAt().toString()
        );

        store.findById(entity.getMemberId()).ifPresent(member -> {
            view.put("memberName", member.name());
            view.put("memberUsername", member.username());
        });
        return view;
    }
}
