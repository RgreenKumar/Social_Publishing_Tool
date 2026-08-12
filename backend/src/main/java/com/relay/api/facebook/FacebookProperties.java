package com.relay.api.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "relay.facebook")
public class FacebookProperties {

    private String pageId = "";
    private String pageAccessToken = "";
    private String userAccessToken = "";
    private String graphVersion = "v21.0";

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getPageAccessToken() {
        return pageAccessToken;
    }

    public void setPageAccessToken(String pageAccessToken) {
        this.pageAccessToken = pageAccessToken;
    }

    public String getUserAccessToken() {
        return userAccessToken;
    }

    public void setUserAccessToken(String userAccessToken) {
        this.userAccessToken = userAccessToken;
    }

    public String getGraphVersion() {
        return graphVersion;
    }

    public void setGraphVersion(String graphVersion) {
        this.graphVersion = graphVersion;
    }

    public boolean isConfigured() {
        return pageId != null && !pageId.isBlank()
            && pageAccessToken != null && !pageAccessToken.isBlank();
    }
}
