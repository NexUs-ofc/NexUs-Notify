package com.example.nexusnotify.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong();
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(1000, now::get);
    private final RateLimitProperties properties = new RateLimitProperties(1000, 3);
    private final RateLimitFilter filter = new RateLimitFilter(limiter, properties);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsJsonAndRetryAfterAndKeepsProfilesIndependent() throws Exception {
        authenticate("12");
        for (int index = 0; index < 3; index++) {
            assertThat(call("GET", "/api/items").getStatus()).isEqualTo(200);
        }
        var response = call("GET", "/api/items");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        authenticate("13");
        assertThat(call("GET", "/api/items").getStatus()).isEqualTo(200);
        authenticate("12");
        now.set(Duration.ofMinutes(1).toNanos());
        assertThat(call("GET", "/api/items").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotCountPreflightHealthOrSwaggerRequests() throws Exception {
        authenticate("12");
        for (int index = 0; index < 5; index++) {
            assertThat(call("OPTIONS", "/api/items").getStatus()).isEqualTo(200);
            assertThat(call("GET", "/actuator/health").getStatus()).isEqualTo(200);
            assertThat(call("GET", "/v3/api-docs").getStatus()).isEqualTo(200);
        }
        assertThat(call("GET", "/api/items").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesUnauthenticatedRequestsToTheSecurityChain() throws Exception {
        for (int index = 0; index < 5; index++) {
            assertThat(call("GET", "/api/items").getStatus()).isEqualTo(200);
        }
        authenticate("12");
        assertThat(call("GET", "/api/items").getStatus()).isEqualTo(200);
    }

    @Test
    void sharesTheQuotaAcrossNotificationAndPushRoutes() throws Exception {
        authenticate("12");
        assertThat(call("GET", "/api/notifications").getStatus()).isEqualTo(200);
        assertThat(call("PUT", "/api/push/installations").getStatus()).isEqualTo(200);
        assertThat(call("DELETE", "/api/push/installations/id").getStatus()).isEqualTo(200);
        assertThat(call("GET", "/api/notifications").getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse call(String method, String path) throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(method, path), response, chain);
        assertThat(chain.getRequest() == null).isEqualTo(response.getStatus() == 429);
        return response;
    }

    private void authenticate(String profileId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(profileId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
