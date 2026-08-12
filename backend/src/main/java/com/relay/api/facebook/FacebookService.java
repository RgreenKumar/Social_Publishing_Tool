package com.relay.api.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.relay.api.common.MultipartBody;

@Service
public class FacebookService {

    private final FacebookProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    private final Set<String> connectedUserIds = new HashSet<>();

    public FacebookService(FacebookProperties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        return props.isConfigured();
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

    /** Verifies the page token and returns the page display name. */
    public String connectAndVerify() {
        ensureConfigured();
        try {
            String url = graphBase() + "/" + encode(props.getPageId())
                + "?fields=id,name"
                + "&access_token=" + encode(props.getPageAccessToken());

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
            String name = text(node, "name");
            if (name == null || name.isBlank()) {
                name = "Facebook Page";
            }
            return name;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Facebook verify failed: " + ex.getMessage()
            );
        }
    }

    public void publishText(String content) {
        publish(content, null, null, null);
    }

    public void publish(String content, byte[] imageBytes, String imageFilename, String imageContentType) {
        ensureConfigured();
        try {
            if (imageBytes != null && imageBytes.length > 0) {
                publishPhoto(content, imageBytes, imageFilename, imageContentType);
            } else {
                publishFeedText(content);
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Facebook publish failed: " + ex.getMessage(), ex);
        }
    }

    private void publishFeedText(String content) throws Exception {
        String form = "message=" + encode(content == null ? "" : content)
            + "&access_token=" + encode(props.getPageAccessToken());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(graphBase() + "/" + encode(props.getPageId()) + "/feed"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(parseError(response.body(), response.statusCode()));
        }

        JsonNode node = mapper.readTree(response.body());
        if (!node.has("id")) {
            throw new IllegalStateException("Facebook did not return a post id");
        }
    }

    private void publishPhoto(
        String content,
        byte[] imageBytes,
        String imageFilename,
        String imageContentType
    ) throws Exception {
        String filename = imageFilename == null || imageFilename.isBlank() ? "photo.jpg" : imageFilename;
        String type = imageContentType == null || imageContentType.isBlank() ? "image/jpeg" : imageContentType;

        var parts = MultipartBody.list(
            MultipartBody.Part.text("access_token", props.getPageAccessToken()),
            MultipartBody.Part.text("caption", content == null ? "" : content),
            MultipartBody.Part.text("published", "true"),
            MultipartBody.Part.file("source", filename, type, imageBytes)
        );
        MultipartBody multipart = MultipartBody.of(parts);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(graphBase() + "/" + encode(props.getPageId()) + "/photos"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", multipart.contentTypeHeader())
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.body()))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(parseError(response.body(), response.statusCode()));
        }

        JsonNode node = mapper.readTree(response.body());
        if (!node.has("id") && !node.has("post_id")) {
            throw new IllegalStateException("Facebook did not return a photo/post id");
        }
    }

    /**
     * Uploads an unpublished Page photo and returns a Meta CDN URL Instagram can fetch.
     * Avoids needing PUBLIC_BASE_URL / ngrok for local Instagram publishing.
     */
    public String uploadStagingImageUrl(byte[] imageBytes, String imageFilename, String imageContentType) {
        ensureConfigured();
        try {
            String filename = imageFilename == null || imageFilename.isBlank() ? "photo.jpg" : imageFilename;
            String type = imageContentType == null || imageContentType.isBlank() ? "image/jpeg" : imageContentType;

            var parts = MultipartBody.list(
                MultipartBody.Part.text("access_token", props.getPageAccessToken()),
                MultipartBody.Part.text("published", "false"),
                MultipartBody.Part.text("temporary", "true"),
                MultipartBody.Part.file("source", filename, type, imageBytes)
            );
            MultipartBody multipart = MultipartBody.of(parts);

            HttpRequest uploadReq = HttpRequest.newBuilder()
                .uri(URI.create(graphBase() + "/" + encode(props.getPageId()) + "/photos"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", multipart.contentTypeHeader())
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.body()))
                .build();

            HttpResponse<String> uploadRes = http.send(uploadReq, HttpResponse.BodyHandlers.ofString());
            if (uploadRes.statusCode() >= 300) {
                throw new IllegalStateException(parseError(uploadRes.body(), uploadRes.statusCode()));
            }

            JsonNode uploadNode = mapper.readTree(uploadRes.body());
            String photoId = text(uploadNode, "id");
            if (photoId == null || photoId.isBlank()) {
                throw new IllegalStateException("Facebook did not return a staging photo id");
            }

            String url = graphBase() + "/" + encode(photoId)
                + "?fields=images,source"
                + "&access_token=" + encode(props.getPageAccessToken());
            HttpRequest metaReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            HttpResponse<String> metaRes = http.send(metaReq, HttpResponse.BodyHandlers.ofString());
            if (metaRes.statusCode() >= 300) {
                throw new IllegalStateException(parseError(metaRes.body(), metaRes.statusCode()));
            }

            JsonNode meta = mapper.readTree(metaRes.body());
            String source = text(meta, "source");
            if (source != null && !source.isBlank()) {
                return source;
            }

            JsonNode images = meta.get("images");
            if (images != null && images.isArray() && images.size() > 0) {
                String best = null;
                int bestArea = -1;
                for (JsonNode img : images) {
                    int w = img.has("width") ? img.get("width").asInt(0) : 0;
                    int h = img.has("height") ? img.get("height").asInt(0) : 0;
                    String src = text(img, "source");
                    if (src == null || src.isBlank()) {
                        continue;
                    }
                    int area = w * h;
                    if (area >= bestArea) {
                        bestArea = area;
                        best = src;
                    }
                }
                if (best != null) {
                    return best;
                }
            }

            throw new IllegalStateException("Facebook staging photo had no public image URL");
        } catch (IllegalStateException | ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Could not stage image on Facebook for Instagram: " + ex.getMessage(),
                ex
            );
        }
    }

    private String graphBase() {
        return "https://graph.facebook.com/" + props.getGraphVersion();
    }

    private void ensureConfigured() {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Facebook Page ID / Access Token are not configured on the server"
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

    private String parseError(String body, int status) {
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode error = node.get("error");
            if (error != null) {
                String message = error.has("message") ? error.get("message").asText() : null;
                if (message != null && !message.isBlank()) {
                    String lower = message.toLowerCase();
                    if (lower.contains("cannot call api for app") || lower.contains("on behalf of user")) {
                        return message + " — Regenerate a Page Access Token for this Page in Graph API Explorer "
                            + "(same Meta app, permissions: pages_show_list, pages_manage_posts, pages_read_engagement). "
                            + "Add your Facebook account as an app Admin/Developer/Tester, or switch the app to Live mode. "
                            + "Update FACEBOOK_PAGE_ID and FACEBOOK_PAGE_ACCESS_TOKEN in backend/.env, then restart.";
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
        return "Facebook API error (" + status + "): " + body;
    }
}
