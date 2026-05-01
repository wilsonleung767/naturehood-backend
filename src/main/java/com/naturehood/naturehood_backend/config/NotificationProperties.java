package com.naturehood.naturehood_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notifications")
public class NotificationProperties {

    private String sendUrl = "";
    private String apiKey = "";
    private boolean enabled = true;

    public String getSendUrl() { return sendUrl; }
    public void setSendUrl(String sendUrl) { this.sendUrl = sendUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
