package com.example.nexusnotify.ratelimit;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.concurrent.atomic.AtomicLong;

@TestConfiguration
public class RateLimitTestConfiguration {

    @Bean
    AtomicLong rateLimitNanoTime() {
        return new AtomicLong();
    }

    @Bean
    @Primary
    InMemoryRateLimiter deterministicRateLimiter(AtomicLong rateLimitNanoTime) {
        return new InMemoryRateLimiter(100000, rateLimitNanoTime::get);
    }
}
