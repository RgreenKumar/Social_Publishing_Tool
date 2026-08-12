package com.relay.api.social;

import com.relay.api.auth.AuthController;
import com.relay.api.facebook.FacebookService;
import com.relay.api.instagram.InstagramService;
import com.relay.api.linkedin.LinkedInService;
import com.relay.api.store.InMemoryStore;
import com.relay.api.threads.ThreadsService;
import com.relay.api.store.InMemoryStore.SocialAccount;
import com.relay.api.store.InMemoryStore.User;
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
@RequestMapping("/api/social-accounts")
public class SocialAccountController {

    private final InMemoryStore store;
    private final AuthController authController;
    private final LinkedInService linkedInService;
    private final FacebookService facebookService;
    private final InstagramService instagramService;
    private final ThreadsService threadsService;

    public SocialAccountController(
        InMemoryStore store,
        AuthController authController,
        LinkedInService linkedInService,
        FacebookService facebookService,
        InstagramService instagramService,
        ThreadsService threadsService
    ) {
        this.store = store;
        this.authController = authController;
        this.linkedInService = linkedInService;
        this.facebookService = facebookService;
        this.instagramService = instagramService;
        this.threadsService = threadsService;
    }

    public record ConnectRequest(String platform, String displayName) {}

    @GetMapping
    public List<SocialAccount> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authController.requireUser(authorization);
        // Members see admin's shared brand accounts
        return store.accountsFor(user.workspaceOwnerId());
    }

    @PostMapping("/connect")
    public SocialAccount connect(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody ConnectRequest body
    ) {
        User user = authController.requireAdmin(authorization);
        if (body.platform() == null || body.platform().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform is required");
        }
        String platform = body.platform().trim().toLowerCase();
        if ("linkedin".equals(platform)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Use LinkedIn OAuth connect instead of mock connect"
            );
        }
        if ("facebook".equals(platform)) {
            if (!facebookService.isConfigured()) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Facebook Page credentials are not configured on the server"
                );
            }
            String pageName = facebookService.connectAndVerify();
            facebookService.markConnected(user.id());
            return store.connect(user.id(), "facebook", pageName);
        }
        if ("instagram".equals(platform)) {
            if (!instagramService.isConfigured()) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Instagram Business Account ID / Facebook Page token are not configured on the server"
                );
            }
            String display = instagramService.connectAndVerify();
            instagramService.markConnected(user.id());
            return store.connect(user.id(), "instagram", display);
        }
        if ("threads".equals(platform)) {
            if (!threadsService.isConfigured()) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Threads access token is not configured on the server"
                );
            }
            String display = threadsService.connectAndVerify();
            threadsService.markConnected(user.id());
            return store.connect(user.id(), "threads", display);
        }
        return store.connect(user.id(), platform, body.displayName());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> disconnect(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable String id
    ) {
        User user = authController.requireAdmin(authorization);
        try {
            SocialAccount account = store.disconnect(user.id(), id);
            if ("linkedin".equalsIgnoreCase(account.platform())) {
                linkedInService.clearSession(user.id());
            }
            if ("facebook".equalsIgnoreCase(account.platform())) {
                facebookService.clearConnection(user.id());
            }
            if ("instagram".equalsIgnoreCase(account.platform())) {
                instagramService.clearConnection(user.id());
            }
            if ("threads".equalsIgnoreCase(account.platform())) {
                threadsService.clearConnection(user.id());
            }
            return Map.of("account", account);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
