package com.relay.api.linkedin;

import com.relay.api.store.LinkedInCredentialEntity;
import com.relay.api.store.LinkedInCredentialRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LinkedInService {

    public record LinkedInSession(String accessToken, String personUrn, String displayName) {}

    public record ConnectedAccount(String userId, LinkedInSession session) {}

    private final LinkedInProperties props;
    private final LinkedInCredentialRepository credentials;
    private final String frontendUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final Map<String, String> oauthStateToUserId = new ConcurrentHashMap<>();

    public LinkedInService(
        LinkedInProperties props,
        LinkedInCredentialRepository credentials,
        @Value("${relay.frontend-url:http://localhost:3000}") String frontendUrl
    ) {
        this.props = props;
        this.credentials = credentials;
        this.frontendUrl = frontendUrl;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public String buildAuthorizationUrl(String userId) {
        ensureConfigured();
        String state = UUID.randomUUID().toString();
        oauthStateToUserId.put(state, userId);

        // LinkedIn requires scopes space-separated as %20 (not '+')
        String scopeParam = Arrays.stream(props.getScopes().trim().split("\\s+"))
            .filter(s -> !s.isBlank())
            .map(LinkedInService::encode)
            .collect(Collectors.joining("%20"));

        return "https://www.linkedin.com/oauth/v2/authorization"
            + "?response_type=code"
            + "&client_id=" + encode(props.getClientId())
            + "&redirect_uri=" + encode(props.getRedirectUri())
            + "&state=" + encode(state)
            + "&scope=" + scopeParam
            + "&prompt=consent";
    }

    @Transactional
    public ConnectedAccount handleCallback(String code, String state) {
        ensureConfigured();
        String userId = oauthStateToUserId.remove(state);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OAuth state");
        }

        String accessToken = exchangeCodeForToken(code);
        JsonNode profile = fetchUserInfo(accessToken);
        String personId = text(profile, "sub");
        if (personId == null || personId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LinkedIn profile id missing");
        }

        String name = text(profile, "name");
        if (name == null || name.isBlank()) {
            name = "LinkedIn User";
        }

        LinkedInSession session = new LinkedInSession(
            accessToken,
            "urn:li:person:" + personId,
            name
        );
        saveSession(userId, session);
        return new ConnectedAccount(userId, session);
    }

    public LinkedInSession getSession(String userId) {
        return credentials.findById(userId)
            .map(c -> new LinkedInSession(c.getAccessToken(), c.getPersonUrn(), c.getDisplayName()))
            .orElse(null);
    }

    @Transactional
    public void clearSession(String userId) {
        credentials.deleteById(userId);
    }

    private void saveSession(String userId, LinkedInSession session) {
        LinkedInCredentialEntity entity = credentials.findById(userId).orElse(null);
        if (entity == null) {
            credentials.save(new LinkedInCredentialEntity(
                userId,
                session.accessToken(),
                session.personUrn(),
                session.displayName()
            ));
            return;
        }
        entity.update(session.accessToken(), session.personUrn(), session.displayName());
        credentials.save(entity);
    }

    public String frontendAccountsUrl(String status, String message) {
        String base = frontendUrl.replaceAll("/$", "") + "/app/accounts";
        String q = "linkedin=" + encode(status);
        if (message != null && !message.isBlank()) {
            q += "&message=" + encode(message);
        }
        return base + "?" + q;
    }

    public void publishText(String userId, String content) {
        publish(userId, content, null, null, null);
    }

    public void publish(
        String userId,
        String content,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType
    ) {
        LinkedInSession session = getSession(userId);
        if (session == null) {
            throw new IllegalStateException(
                "LinkedIn session expired. Disconnect and Connect LinkedIn again, then publish."
            );
        }

        if (imageBytes != null && imageBytes.length > 0) {
            publishImageViaUgcPosts(session, content, imageBytes, imageFilename, imageContentType);
            return;
        }

        try {
            publishViaUgcPosts(session, content);
        } catch (IllegalStateException ugcError) {
            try {
                publishViaRestPosts(session, content);
            } catch (IllegalStateException restError) {
                throw new IllegalStateException(
                    ugcError.getMessage() + " | fallback: " + restError.getMessage()
                );
            }
        }
    }

    private void publishImageViaUgcPosts(
        LinkedInSession session,
        String content,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType
    ) {
        try {
            String asset = registerAndUploadImage(session, imageBytes, imageFilename, imageContentType);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("author", session.personUrn());
            body.put("lifecycleState", "PUBLISHED");

            Map<String, Object> shareCommentary = Map.of("text", content == null ? "" : content);
            Map<String, Object> media = new LinkedHashMap<>();
            media.put("status", "READY");
            media.put("description", Map.of("text", content == null ? "" : content));
            media.put("media", asset);
            media.put("title", Map.of("text", imageFilename == null ? "Image" : imageFilename));

            Map<String, Object> shareContent = new LinkedHashMap<>();
            shareContent.put("shareCommentary", shareCommentary);
            shareContent.put("shareMediaCategory", "IMAGE");
            shareContent.put("media", List.of(media));

            body.put("specificContent", Map.of(
                "com.linkedin.ugc.ShareContent", shareContent
            ));
            body.put("visibility", Map.of(
                "com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"
            ));

            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.linkedin.com/v2/ugcPosts"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + session.accessToken())
                .header("Content-Type", "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(parseLinkedInError(response.body(), response.statusCode()));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("LinkedIn image publish failed: " + ex.getMessage(), ex);
        }
    }

    private String registerAndUploadImage(
        LinkedInSession session,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType
    ) throws Exception {
        Map<String, Object> registerBody = Map.of(
            "registerUploadRequest", Map.of(
                "recipes", List.of("urn:li:digitalmediaRecipe:feedshare-image"),
                "owner", session.personUrn(),
                "serviceRelationships", List.of(Map.of(
                    "relationshipType", "OWNER",
                    "identifier", "urn:li:userGeneratedContent"
                ))
            )
        );

        HttpRequest registerRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://api.linkedin.com/v2/assets?action=registerUpload"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + session.accessToken())
            .header("Content-Type", "application/json")
            .header("X-Restli-Protocol-Version", "2.0.0")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(registerBody)))
            .build();

        HttpResponse<String> registerResponse = http.send(registerRequest, HttpResponse.BodyHandlers.ofString());
        if (registerResponse.statusCode() >= 300) {
            throw new IllegalStateException(parseLinkedInError(registerResponse.body(), registerResponse.statusCode()));
        }

        JsonNode root = mapper.readTree(registerResponse.body());
        JsonNode value = root.path("value");
        String asset = text(value, "asset");
        JsonNode uploadMechanism = value.path("uploadMechanism")
            .path("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest");
        String uploadUrl = text(uploadMechanism, "uploadUrl");

        if (asset == null || asset.isBlank() || uploadUrl == null || uploadUrl.isBlank()) {
            throw new IllegalStateException("LinkedIn did not return an image upload URL");
        }

        // LinkedIn CDN / S3 upload URLs often reset the connection if Authorization is sent.
        // Prefer headers from register response; default to octet-stream without Bearer.
        Map<String, String> uploadHeaders = new LinkedHashMap<>();
        JsonNode headerNode = uploadMechanism.path("headers");
        if (headerNode != null && headerNode.isObject()) {
            Iterator<String> names = headerNode.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String headerValue = text(headerNode, name);
                if (headerValue != null && !headerValue.isBlank()) {
                    uploadHeaders.put(name, headerValue);
                }
            }
        }
        if (!uploadHeaders.containsKey("Content-Type") && !uploadHeaders.containsKey("content-type")) {
            uploadHeaders.put(
                "Content-Type",
                imageContentType == null || imageContentType.isBlank()
                    ? "application/octet-stream"
                    : imageContentType
            );
        }

        Exception lastError = null;
        for (boolean withAuth : new boolean[] { false, true }) {
            try {
                HttpRequest.Builder uploadBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .timeout(Duration.ofSeconds(120))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(imageBytes));
                for (Map.Entry<String, String> entry : uploadHeaders.entrySet()) {
                    uploadBuilder.header(entry.getKey(), entry.getValue());
                }
                if (withAuth) {
                    uploadBuilder.header("Authorization", "Bearer " + session.accessToken());
                }

                HttpResponse<String> uploadResponse = http.send(
                    uploadBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
                );
                if (uploadResponse.statusCode() >= 300) {
                    lastError = new IllegalStateException(
                        "LinkedIn image upload failed (" + uploadResponse.statusCode() + "): "
                            + uploadResponse.body()
                    );
                    continue;
                }
                return asset;
            } catch (IOException ex) {
                lastError = ex;
                Thread.sleep(800);
            }
        }

        throw new IllegalStateException(
            "LinkedIn image upload failed: "
                + (lastError != null && lastError.getMessage() != null
                    ? lastError.getMessage()
                    : "connection reset"),
            lastError
        );
    }

    private void publishViaUgcPosts(LinkedInSession session, String content) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("author", session.personUrn());
            body.put("lifecycleState", "PUBLISHED");

            Map<String, Object> shareCommentary = Map.of("text", content);
            Map<String, Object> shareContent = new LinkedHashMap<>();
            shareContent.put("shareCommentary", shareCommentary);
            shareContent.put("shareMediaCategory", "NONE");

            body.put("specificContent", Map.of(
                "com.linkedin.ugc.ShareContent", shareContent
            ));
            body.put("visibility", Map.of(
                "com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"
            ));

            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.linkedin.com/v2/ugcPosts"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + session.accessToken())
                .header("Content-Type", "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(parseLinkedInError(response.body(), response.statusCode()));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("LinkedIn ugcPosts failed: " + ex.getMessage(), ex);
        }
    }

    private void publishViaRestPosts(LinkedInSession session, String content) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("author", session.personUrn());
            body.put("commentary", content);
            body.put("visibility", "PUBLIC");
            body.put("distribution", Map.of(
                "feedDistribution", "MAIN_FEED",
                "targetEntities", List.of(),
                "thirdPartyDistributionChannels", List.of()
            ));
            body.put("lifecycleState", "PUBLISHED");
            body.put("isReshareDisabledByAuthor", false);

            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.linkedin.com/rest/posts"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + session.accessToken())
                .header("Content-Type", "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .header("LinkedIn-Version", "202401")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(parseLinkedInError(response.body(), response.statusCode()));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("LinkedIn rest/posts failed: " + ex.getMessage(), ex);
        }
    }

    private String exchangeCodeForToken(String code) {
        try {
            String form = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(props.getRedirectUri())
                + "&client_id=" + encode(props.getClientId())
                + "&client_secret=" + encode(props.getClientSecret());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.linkedin.com/oauth/v2/accessToken"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    parseLinkedInError(response.body(), response.statusCode())
                );
            }

            JsonNode node = mapper.readTree(response.body());
            String token = text(node, "access_token");
            if (token == null || token.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No access_token from LinkedIn");
            }
            return token;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LinkedIn token exchange failed: " + ex.getMessage());
        }
    }

    private JsonNode fetchUserInfo(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.linkedin.com/v2/userinfo"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    parseLinkedInError(response.body(), response.statusCode())
                );
            }
            return mapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LinkedIn profile fetch failed: " + ex.getMessage());
        }
    }

    private void ensureConfigured() {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "LinkedIn Client ID/Secret are not configured on the server"
            );
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private String parseLinkedInError(String body, int status) {
        try {
            JsonNode node = mapper.readTree(body);
            if (node.has("error_description")) {
                return node.get("error_description").asText();
            }
            if (node.has("message")) {
                return node.get("message").asText();
            }
            if (node.has("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "LinkedIn API error (" + status + "): " + body;
    }
}
