package com.relay.api.instagram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "relay.instagram")
public class InstagramProperties {

    private String businessAccountId = "";
    /** Public HTTPS base URL Meta can fetch (e.g. ngrok) — required for image posts. */
    private String publicBaseUrl = "";

    public String getBusinessAccountId() {
        return businessAccountId;
    }

    public void setBusinessAccountId(String businessAccountId) {
        this.businessAccountId = businessAccountId;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean hasPublicBaseUrl() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank();
    }

    public boolean isConfigured() {
        return businessAccountId != null && !businessAccountId.isBlank();
    }
}
