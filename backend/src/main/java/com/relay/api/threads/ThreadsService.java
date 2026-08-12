package com.relay.api.threads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class ThreadsService {

    private final ThreadsProperties props;
    private final TempMediaStore tempMediaStore;
    private final FacebookService facebookService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    private final Set<String> connectedUserIds = new HashSet<>();

    /** Threads profile id resolved from the access token (/me). */
    private volatile String resolvedThreadsUserId;

    public ThreadsService(
        ThreadsProperties props,
        TempMediaStore tempMediaStore,
        FacebookService facebookService
    ) {
        this.props = props;
        this.tempMediaStore = tempMediaStore;
        this.facebookService = facebookService;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public void markConnected(String userId) {
        connectedUserIds.add(userId);
    }

    public void clearConnection(String userId) {
        connectedUserIds.remove(userId);
    }

    /** Verifies the token and returns @username (or display fallback). */
    public String connectAndVerify() {
        ensureConfigured();
        try {
            JsonNode node = fetchMe();
            String id = text(node, "id");
            if (id == null || id.isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Threads /me did not return a user id — check THREADS_ACCESS_TOKEN scopes (threads_basic)"
                );
            }
            resolvedThreadsUserId = id;

            String configured = props.getUserId();
            if (configured != null && !configured.isBlank() && !configured.equals(id)) {
                // Wrong ID in .env is a common cause of publish failures; /me wins.
                System.out.println(
                    "[threads] THREADS_USER_ID=" + configured
                        + " does not match token profile id=" + id
                        + " — using /me id instead"
                );
            }

            String username = text(node, "username");
            if (username != null && !username.isBlank()) {
                return "@" + username.replaceFirst("^@", "");
            }
            String name = text(node, "name");
            if (name != null && !name.isBlank()) {
                return name;
            }
            return "Threads (" + id + ")";
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Threads verify failed: " + ex.getMessage()
            );
        }
    }

    public String publish(String content, byte[] imageBytes, String imageFilename, String imageContentType) {
        ensureConfigured();
        try {
            ensureResolvedUserId();
            boolean hasImage = imageBytes != null && imageBytes.length > 0;
            if (hasImage) {
                String imageUrl;
                if (props.hasPublicBaseUrl()) {
                    String mediaId = tempMediaStore.put(imageBytes, imageContentType, imageFilename);
                    imageUrl = trimSlash(props.getPublicBaseUrl()) + "/api/media/" + mediaId;
                } else if (facebookService.isConfigured()) {
                    // Stage on Facebook Page so Meta can fetch a public CDN URL (no ngrok).
                    imageUrl = facebookService.uploadStagingImageUrl(
                        imageBytes,
                        imageFilename,
                        imageContentType
                    );
                } else {
                    throw new IllegalStateException(
                        "Threads image posts need either PUBLIC_BASE_URL (e.g. ngrok) "
                            + "or a configured FACEBOOK_PAGE_ACCESS_TOKEN to stage the image."
                    );
                }
                return createAndPublishImage(content, imageUrl);
            }
            return createAndPublishText(content);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Threads publish failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureResolvedUserId() throws Exception {
        if (resolvedThreadsUserId != null && !resolvedThreadsUserId.isBlank()) {
            return;
        }
        JsonNode me = fetchMe();
        String id = text(me, "id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                "Could not resolve Threads user from token. Reconnect Threads or check THREADS_ACCESS_TOKEN."
            );
        }
        resolvedThreadsUserId = id;
    }

    private JsonNode fetchMe() throws Exception {
        String url = apiBase() + "/me?fields=id,username,name"
            + "&access_token=" + encode(props.getAccessToken());

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
        return mapper.readTree(response.body());
    }

    private String createAndPublishText(String content) throws Exception {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new IllegalStateException("Threads requires text when no image is provided");
        }
        if (text.length() > 500) {
            throw new IllegalStateException("Threads caption max is 500 characters");
        }

        String form = "media_type=TEXT"
            + "&text=" + encode(text)
            + "&access_token=" + encode(props.getAccessToken());

        String creationId = createContainer(form);
        publishContainer(creationId);
        return "Published to Threads";
    }

    private String createAndPublishImage(String content, String imageUrl) throws Exception {
        String text = content == null ? "" : content.trim();
        if (text.length() > 500) {
            throw new IllegalStateException("Threads caption max is 500 characters");
        }

        StringBuilder form = new StringBuilder();
        form.append("media_type=IMAGE");
        form.append("&image_url=").append(encode(imageUrl));
        form.append("&access_token=").append(encode(props.getAccessToken()));
        if (!text.isEmpty()) {
            form.append("&text=").append(encode(text));
        }

        String creationId = createContainer(form.toString());
        waitForContainer(creationId);
        publishContainer(creationId);
        return "Published image to Threads";
    }

    private String createContainer(String formBody) throws Exception {
        // Always use "me" so a wrong THREADS_USER_ID in .env cannot break publish.
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase() + "/me/threads"))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(enrichPermissionHint(parseError(response.body(), response.statusCode())));
        }

        JsonNode node = mapper.readTree(response.body());
        String id = text(node, "id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Threads did not return a container id");
        }
        return id;
    }

    private void waitForContainer(String creationId) throws Exception {
        for (int i = 0; i < 8; i++) {
            String url = apiBase() + "/" + encode(creationId)
                + "?fields=status,error_message"
                + "&access_token=" + encode(props.getAccessToken());
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 300) {
                JsonNode node = mapper.readTree(response.body());
                String status = text(node, "status");
                if ("FINISHED".equalsIgnoreCase(status) || "PUBLISHED".equalsIgnoreCase(status)) {
                    return;
                }
                if ("ERROR".equalsIgnoreCase(status)) {
                    String err = text(node, "error_message");
                    throw new IllegalStateException(err != null ? err : "Threads media container error");
                }
            }
            Thread.sleep(1500);
        }
    }

    private void publishContainer(String creationId) throws Exception {
        String form = "creation_id=" + encode(creationId)
            + "&access_token=" + encode(props.getAccessToken());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase() + "/me/threads_publish"))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(enrichPermissionHint(parseError(response.body(), response.statusCode())));
        }

        JsonNode node = mapper.readTree(response.body());
        if (!node.has("id")) {
            throw new IllegalStateException("Threads did not return a post id");
        }
    }

    private String enrichPermissionHint(String message) {
        if (message == null) {
            return "Threads API error";
        }
        String lower = message.toLowerCase();
        if (lower.contains("does not exist") || lower.contains("missing permissions")
            || lower.contains("unsupported post request")) {
            return message + " — Use a Threads user access token (starts with THAAT…) with "
                + "threads_basic + threads_content_publish. Get the correct id from "
                + "GET https://graph.threads.net/v1.0/me?fields=id,username&access_token=YOUR_TOKEN "
                + "(do not use a Facebook Page ID). THREADS_USER_ID is optional now.";
        }
        return message;
    }

    private String apiBase() {
        return "https://graph.threads.net/" + props.getApiVersion();
    }

    private void ensureConfigured() {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "THREADS_ACCESS_TOKEN is not configured on the server"
            );
        }
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
                    String lower = message.toLowerCase();
                    if (lower.contains("access blocked") || lower.contains("api access blocked")
                        || lower.contains("application request limit")
                        || lower.contains("permission")) {
                        return message + " — Use a Threads user token from the Threads app (App Dashboard → "
                            + "App settings → Basic → Threads App ID). Permissions: threads_basic, threads_content_publish. "
                            + "Add your Threads account as a tester if the app is in Development mode. "
                            + "Update THREADS_ACCESS_TOKEN in backend/.env and restart.";
                    }
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
        return "Threads API error (" + status + "): " + body;
    }
}
