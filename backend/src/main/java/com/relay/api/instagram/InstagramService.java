package com.relay.api.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.api.facebook.FacebookProperties;
import com.relay.api.facebook.FacebookService;
import com.relay.api.media.TempMediaStore;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Instagram Content Publishing API (Business / Creator account linked to a Facebook Page).
 * Uses the Page access token from {@link FacebookProperties}.
 */
@Service
public class InstagramService {

    private final InstagramProperties props;
    private final FacebookProperties facebookProps;
    private final FacebookService facebookService;
    private final TempMediaStore tempMediaStore;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    private final Set<String> connectedUserIds = new HashSet<>();

    public InstagramService(
        InstagramProperties props,
        FacebookProperties facebookProps,
        FacebookService facebookService,
        TempMediaStore tempMediaStore
    ) {
        this.props = props;
        this.facebookProps = facebookProps;
        this.facebookService = facebookService;
        this.tempMediaStore = tempMediaStore;
    }

    public boolean isConfigured() {
        return props.isConfigured()
            && facebookProps.getPageAccessToken() != null
            && !facebookProps.getPageAccessToken().isBlank();
    }

    public boolean isConnected(String userId) {
        return connectedUserIds.contains(userId);
    }

    public void markConnected(String userId) {
        connectedUserIds.add(userId);
    }

    public void clearConnection(String userId) {
        connectedUserIds.remove(userId);
    }

    /** Verifies IG user + page token; returns @username. */
    public String connectAndVerify() {
        ensureConfigured();
        try {
            String url = graphBase() + "/" + encode(props.getBusinessAccountId())
                + "?fields=id,username,name"
                + "&access_token=" + encode(facebookProps.getPageAccessToken());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    parseError(response.body(), response.statusCode())
                );
            }

            JsonNode node = mapper.readTree(response.body());
            String username = text(node, "username");
            if (username != null && !username.isBlank()) {
                return "@" + username.replaceFirst("^@", "");
            }
            String name = text(node, "name");
            if (name != null && !name.isBlank()) {
                return name;
            }
            return "Instagram (" + props.getBusinessAccountId() + ")";
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Instagram verify failed: " + ex.getMessage()
            );
        }
    }

    /**
     * Publishes an image post. Prefers PUBLIC_BASE_URL when set; otherwise stages the image
     * as an unpublished Facebook Page photo and uses Meta's CDN URL (no ngrok required).
     */
    public String publish(String content, byte[] imageBytes, String imageFilename, String imageContentType) {
        ensureConfigured();
        try {
            if (imageBytes == null || imageBytes.length == 0) {
                throw new IllegalStateException(
                    "Instagram requires an image. Attach an image, or post text-only on Facebook/LinkedIn/Threads."
                );
            }

            String imageUrl;
            if (props.hasPublicBaseUrl()) {
                String mediaId = tempMediaStore.put(imageBytes, imageContentType, imageFilename);
                imageUrl = trimSlash(props.getPublicBaseUrl()) + "/api/media/" + mediaId;
            } else if (facebookProps.isConfigured()) {
                imageUrl = facebookService.uploadStagingImageUrl(
                    imageBytes,
                    imageFilename,
                    imageContentType
                );
            } else {
                throw new IllegalStateException(
                    "Instagram image posts need either PUBLIC_BASE_URL (e.g. ngrok) or a configured Facebook Page token."
                );
            }

            return createAndPublishImage(content, imageUrl);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Instagram publish failed: " + ex.getMessage(), ex);
        }
    }

    private String createAndPublishImage(String content, String imageUrl) throws Exception {
        String form = "image_url=" + encode(imageUrl)
            + "&caption=" + encode(content == null ? "" : content)
            + "&access_token=" + encode(facebookProps.getPageAccessToken());

        HttpRequest createReq = HttpRequest.newBuilder()
            .uri(URI.create(graphBase() + "/" + encode(props.getBusinessAccountId()) + "/media"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

        HttpResponse<String> createRes = http.send(createReq, HttpResponse.BodyHandlers.ofString());
        if (createRes.statusCode() >= 300) {
            throw new IllegalStateException(parseError(createRes.body(), createRes.statusCode()));
        }

        JsonNode createNode = mapper.readTree(createRes.body());
        String creationId = text(createNode, "id");
        if (creationId == null || creationId.isBlank()) {
            throw new IllegalStateException("Instagram did not return a media container id");
        }

        waitUntilContainerReady(creationId);

        String publishForm = "creation_id=" + encode(creationId)
            + "&access_token=" + encode(facebookProps.getPageAccessToken());

        HttpRequest publishReq = HttpRequest.newBuilder()
            .uri(URI.create(graphBase() + "/" + encode(props.getBusinessAccountId()) + "/media_publish"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(publishForm))
            .build();

        HttpResponse<String> publishRes = http.send(publishReq, HttpResponse.BodyHandlers.ofString());
        if (publishRes.statusCode() >= 300) {
            throw new IllegalStateException(parseError(publishRes.body(), publishRes.statusCode()));
        }

        JsonNode publishNode = mapper.readTree(publishRes.body());
        String postId = text(publishNode, "id");
        if (postId == null || postId.isBlank()) {
            throw new IllegalStateException("Instagram did not return a published media id");
        }
        return "Published to Instagram (" + postId + ")";
    }

    private void waitUntilContainerReady(String creationId) throws Exception {
        for (int i = 0; i < 20; i++) {
            String url = graphBase() + "/" + encode(creationId)
                + "?fields=status_code"
                + "&access_token=" + encode(facebookProps.getPageAccessToken());
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(parseError(response.body(), response.statusCode()));
            }
            JsonNode node = mapper.readTree(response.body());
            String status = text(node, "status_code");
            if ("FINISHED".equalsIgnoreCase(status)) {
                return;
            }
            if ("ERROR".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Instagram media container status: " + status);
            }
            Thread.sleep(1500);
        }
        throw new IllegalStateException("Instagram media container did not become ready in time");
    }

    private String graphBase() {
        String version = facebookProps.getGraphVersion();
        if (version == null || version.isBlank()) {
            version = "v21.0";
        }
        return "https://graph.facebook.com/" + version;
    }

    private void ensureConfigured() {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INSTAGRAM_BUSINESS_ACCOUNT_ID is not configured on the server"
            );
        }
        if (facebookProps.getPageAccessToken() == null || facebookProps.getPageAccessToken().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Facebook Page Access Token is required for Instagram (set FACEBOOK_PAGE_ACCESS_TOKEN)"
            );
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private String parseError(String body, int status) {
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode error = node.get("error");
            if (error != null) {
                String message = error.has("message") ? error.get("message").asText() : null;
                if (message != null && !message.isBlank()) {
                    return message;
                }
                if (error.has("type")) {
                    return error.get("type").asText();
                }
            }
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Instagram API error (" + status + "): " + body;
    }
}
