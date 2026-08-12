package com.relay.api.team;

import com.relay.api.auth.AuthController;
import com.relay.api.store.ApprovalRequestEntity;
import com.relay.api.store.ApprovalRequestRepository;
import com.relay.api.store.InMemoryStore;
import com.relay.api.store.InMemoryStore.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    private final InMemoryStore store;
    private final AuthController authController;
    private final ApprovalRequestRepository approvals;

    public TeamController(
        InMemoryStore store,
        AuthController authController,
        ApprovalRequestRepository approvals
    ) {
        this.store = store;
        this.authController = authController;
        this.approvals = approvals;
    }

    public record CreateMemberRequest(
        @NotBlank String name,
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 6) String password
    ) {}

    @GetMapping("/members")
    public List<Map<String, Object>> list(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User admin = authController.requireAdmin(authorization);
        return store.membersForAdmin(admin.id()).stream()
            .map(this::memberView)
            .toList();
    }

    /** Per-member published counts for the Overview dashboard. */
    @GetMapping("/publish-stats")
    public List<Map<String, Object>> publishStats(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User admin = authController.requireAdmin(authorization);
        return store.membersForAdmin(admin.id()).stream()
            .map(this::memberView)
            .toList();
    }

    @PostMapping("/members")
    public Map<String, Object> create(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody CreateMemberRequest body
    ) {
        User admin = authController.requireAdmin(authorization);
        try {
            User member = store.createTeamMember(
                admin.id(),
                body.name(),
                body.username(),
                body.password()
            );
            return memberView(member);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @DeleteMapping("/members/{id}")
    public Map<String, Object> delete(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id
    ) {
        User admin = authController.requireAdmin(authorization);
        try {
            store.deleteMember(admin.id(), id);
            return Map.of("ok", true);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private Map<String, Object> memberView(User user) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", user.id());
        view.put("name", user.name());
        view.put("username", user.username());
        view.put("role", user.role());
        view.put(
            "publishedCount",
            approvals.countByMemberIdAndStatus(user.id(), ApprovalRequestEntity.STATUS_APPROVED)
        );
        return view;
    }
}
