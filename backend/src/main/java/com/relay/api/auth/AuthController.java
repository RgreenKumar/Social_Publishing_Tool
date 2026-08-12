package com.relay.api.auth;

import com.relay.api.store.InMemoryStore;
import com.relay.api.store.InMemoryStore.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final InMemoryStore store;

    public AuthController(InMemoryStore store) {
        this.store = store;
    }

    public record SignupRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password
    ) {}

    public record LoginRequest(
        String login,
        String email,
        @NotBlank String password
    ) {}

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody SignupRequest body) {
        try {
            User user = store.signup(body.name().trim(), body.email().trim(), body.password());
            String token = store.issueToken(user);
            return authResponse(token, user);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body) {
        String loginValue = body.login() != null && !body.login().isBlank()
            ? body.login().trim()
            : (body.email() != null ? body.email().trim() : "");
        if (loginValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email or username is required");
        }
        User user = store.findByLogin(loginValue)
            .filter(u -> u.password().equals(body.password()))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email/username or password"
            ));
        String token = store.issueToken(user);
        return authResponse(token, user);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = requireUser(authorization);
        return Map.of("user", userView(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractToken(authorization);
        if (token != null) {
            store.revokeToken(token);
        }
        return ResponseEntity.noContent().build();
    }

    public User requireUser(String authorization) {
        return store.userFromToken(extractToken(authorization))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    public User requireAdmin(String authorization) {
        User user = requireUser(authorization);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return user;
    }

    public static String extractToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    private static Map<String, Object> authResponse(String token, User user) {
        return Map.of(
            "token", token,
            "user", userView(user)
        );
    }

    private static Map<String, Object> userView(User user) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", user.id());
        view.put("name", user.name());
        view.put("email", user.email());
        view.put("username", user.username());
        view.put("role", user.role());
        view.put("adminId", user.adminId());
        return view;
    }
}
