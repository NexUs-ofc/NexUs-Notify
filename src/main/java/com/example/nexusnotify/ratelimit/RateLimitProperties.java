package com.example.nexusnotify.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(@DefaultValue("100000") int maxEntries,
                                  @DefaultValue("60") int requestsPerMinute) {
    public RateLimitProperties {
        if (maxEntries < 1 || requestsPerMinute < 1) {
            throw new IllegalArgumentException("Rate limits must be positive");
        }
    }
}
