package com.sub9.userservice.notification.infrastructure.external.slack;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.slack")
public class SlackProperties {

    private boolean enabled = true;
    private long workerDelayMs = 5000;
    private int maxRetries = 3;
    private long retryDelayMs = 30000;
    private Map<String, String> webhooks = new HashMap<>();

    public String webhookUrl(String destination) {
        return webhooks.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(destination))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
