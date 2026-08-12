package com.relay.api.linkedin;

import com.relay.api.auth.AuthController;
import com.relay.api.store.InMemoryStore;
import com.relay.api.store.InMemoryStore.User;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/oauth/linkedin")
public class LinkedInOAuthController {

    private final LinkedInService linkedInService;
    private final InMemoryStore store;

    public LinkedInOAuthController(LinkedInService linkedInService, InMemoryStore store) {
        this.linkedInService = linkedInService;
        this.store = store;
    }

    @GetMapping("/start")
    public Map<String, String> start(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "token", required = false) String tokenParam
    ) {
        User user = resolveUser(authorization, tokenParam);
        String url = linkedInService.buildAuthorizationUrl(user.id());
        return Map.of("url", url, "configured", String.valueOf(linkedInService.isConfigured()));
    }

    @GetMapping("/redirect")
    public ResponseEntity<Void> redirectStart(@RequestParam("token") String token) {
        User user = store.userFromToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        String url = linkedInService.buildAuthorizationUrl(user.id());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error,
        @RequestParam(value = "error_description", required = false) String errorDescription
    ) {
        if (error != null) {
            String msg = errorDescription != null ? errorDescription : error;
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(linkedInService.frontendAccountsUrl("error", msg)))
                .build();
        }

        try {
            if (code == null || state == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing code or state");
            }
            LinkedInService.ConnectedAccount connected = linkedInService.handleCallback(code, state);
            store.connect(connected.userId(), "linkedin", connected.session().displayName());
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(
                    linkedInService.frontendAccountsUrl("connected", connected.session().displayName())
                ))
                .build();
        } catch (Exception ex) {
            String msg = ex instanceof ResponseStatusException rse && rse.getReason() != null
                ? rse.getReason()
                : (ex.getMessage() != null ? ex.getMessage() : "LinkedIn connect failed");
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(linkedInService.frontendAccountsUrl("error", msg)))
                .build();
        }
    }

    private User resolveUser(String authorization, String tokenParam) {
        String token = AuthController.extractToken(authorization);
        if (token == null || token.isBlank()) {
            token = tokenParam;
        }
        return store.userFromToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
