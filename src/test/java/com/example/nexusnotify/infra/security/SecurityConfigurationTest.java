package com.example.nexusnotify.infra.security;

import com.example.nexusnotify.config.AuthProperties;
import com.example.nexusnotify.util.CurrentProfileResolver;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigurationTest.TestController.class, properties = {
    "app.auth.issuer=nexus-auth-test",
    "app.auth.jwt-secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.tracing-access-key=",
    "CORS_ALLOWED_ORIGINS=https://app.nexus.test"
})
@Import({SecurityConfiguration.class, CurrentProfileResolver.class,
        SecurityConfigurationTest.TestSecurityConfiguration.class, SecurityConfigurationTest.TestController.class})
class SecurityConfigurationTest {

    private static final String SIGNING_KEY = "0123456789abcdef0123456789abcdef";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
    }

    @Test
    void rejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/test/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void blocksMetricsWhenTheMonitoringKeyIsNotConfigured() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("TRACING_ACESS nao configurada no servidor."));
    }

    @Test
    void acceptsAnAuthTokenAndUsesItsProfileAndRole() throws Exception {
        String token = token(claims().build());

        mockMvc.perform(get("/test/profile").param("profileId", "99")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(12))
                .andExpect(jsonPath("$.roles").value(hasItem("ROLE_HOUSEHOLD")))
                .andDo(result -> assertNull(result.getRequest().getSession(false)));
    }

    @Test
    void rejectsAMalformedToken() throws Exception {
        mockMvc.perform(get("/test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() throws Exception {
        String token = token(claims().build(), "fedcba9876543210fedcba9876543210");

        mockMvc.perform(get("/test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenFromAnotherIssuer() throws Exception {
        String token = token(claims().issuer("another-service").build());

        mockMvc.perform(get("/test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        Instant now = Instant.now();
        String token = token(claims().issuedAt(now.minusSeconds(900)).expiresAt(now.minusSeconds(300)).build());

        mockMvc.perform(get("/test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenThatIsNotValidYet() throws Exception {
        String token = token(claims().notBefore(Instant.now().plusSeconds(300)).build());

        mockMvc.perform(get("/test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnInvalidProfileWhenResolvingTheUser() throws Exception {
        String token = token(claims().subject("invalid").build());

        mockMvc.perform(get("/test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAnAuthenticatedPatchWithoutASessionOrCsrfToken() throws Exception {
        String token = token(claims().build());

        mockMvc.perform(patch("/test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("12"));
    }

    @Test
    void allowsPreflightFromAConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/test/profile")
                        .header(HttpHeaders.ORIGIN, "https://app.nexus.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.nexus.test"));
    }

    @Test
    void rejectsPreflightFromAnUnknownOrigin() throws Exception {
        mockMvc.perform(options("/test/profile")
                        .header(HttpHeaders.ORIGIN, "https://other.nexus.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsASigningKeyShorterThanThirtyTwoBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        SecurityConfiguration configuration = new SecurityConfiguration();

        assertThrows(IllegalStateException.class,
                () -> configuration.jwtSecret(new AuthProperties("nexus-auth-test", shortKey)));
    }

    private JwtClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().issuer("nexus-auth-test").subject("12")
                .issuedAt(now).expiresAt(now.plusSeconds(600)).claim("profile_type", "HOUSEHOLD");
    }

    private String token(JwtClaimsSet claims) {
        return token(claims, SIGNING_KEY);
    }

    private String token(JwtClaimsSet claims, String signingKey) {
        SecretKey key = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfiguration {
    }

    @RestController
    static class TestController {

        private final CurrentProfileResolver resolver;

        TestController(CurrentProfileResolver resolver) {
            this.resolver = resolver;
        }

        @GetMapping("/test/profile")
        public Map<String, Object> profile(Authentication authentication) {
            return Map.of("profileId", resolver.profileId(), "roles",
                    authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList());
        }

        @PatchMapping("/test/profile")
        public Integer update() {
            return resolver.profileId();
        }
    }
}
