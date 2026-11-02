package com.example.nexusnotify.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import java.io.IOException;
import java.time.Duration;

public final class RateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(InMemoryRateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return "OPTIONS".equals(request.getMethod()) || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            long retryAfter = limiter.consume(new InMemoryRateLimiter.Quota(
                    "profile:" + jwt.getToken().getSubject(), properties.requestsPerMinute(), Duration.ofMinutes(1)));
            if (retryAfter > 0) {
                RateLimitResponse.write(response, retryAfter);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
