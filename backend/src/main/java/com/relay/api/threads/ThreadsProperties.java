package com.relay.api.threads;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "relay.threads")
public class ThreadsProperties {

    /** Optional. Prefer leaving blank — we resolve the ID from the token via /me. */
    private String userId = "";
    private String accessToken = "";
    private String apiVersion = "v1.0";
    /** Public base URL Meta can fetch (e.g. https://xxxx.ngrok.io). Required for image posts. */
    private String publicBaseUrl = "";

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank();
    }

    public boolean hasPublicBaseUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return false;
        }
        String lower = publicBaseUrl.toLowerCase();
        return !lower.contains("localhost")
            && !lower.contains("127.0.0.1")
            && !lower.contains("your-public-host")
            && !lower.contains("example.com");
    }
}
