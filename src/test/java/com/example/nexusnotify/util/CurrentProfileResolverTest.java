package com.example.nexusnotify.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentProfileResolverTest {

    private final CurrentProfileResolver resolver = new CurrentProfileResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTheProfileIdFromTheAuthenticatedToken() {
        authenticate("12");
        assertEquals(12, resolver.profileId());
    }

    @Test
    void rejectsAnEmptySecurityContext() {
        assertThrows(InsufficientAuthenticationException.class, resolver::profileId);
    }

    @Test
    void rejectsAuthenticationWithoutAJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "password", List.of()));

        assertThrows(InsufficientAuthenticationException.class, resolver::profileId);
    }

    @Test
    void rejectsAnUnauthenticatedJwt() {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject("12").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThrows(InsufficientAuthenticationException.class, resolver::profileId);
    }

    @Test
    void rejectsInvalidProfileIds() {
        for (String subject : List.of("invalid", "0", "-1", "2147483648")) {
            authenticate(subject);
            assertThrows(InsufficientAuthenticationException.class, resolver::profileId);
        }
    }

    @Test
    void rejectsATokenWithoutASubject() {
        authenticate(null);
        assertThrows(InsufficientAuthenticationException.class, resolver::profileId);
    }

    private void authenticate(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .claim("profile_type", "HOUSEHOLD");
        if (subject != null) {
            builder.subject(subject);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
    }
}
