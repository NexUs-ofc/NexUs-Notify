package com.example.nexusnotify.controller;

import com.example.nexusnotify.domain.PushInstallation;
import com.example.nexusnotify.domain.PushInstallationStatus;
import com.example.nexusnotify.exception.GlobalExceptionHandler;
import com.example.nexusnotify.infra.repository.PushInstallationRepository;
import com.example.nexusnotify.infra.security.SecurityConfiguration;
import com.example.nexusnotify.service.PushInstallationService;
import com.example.nexusnotify.util.CurrentProfileResolver;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PushInstallationController.class, properties = {
    "app.auth.issuer=nexus-auth-test",
    "app.auth.jwt-secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "notify.push.enabled=false",
    "CORS_ALLOWED_ORIGINS=https://app.nexus.test"
})
@Import({SecurityConfiguration.class, CurrentProfileResolver.class, PushInstallationService.class,
        GlobalExceptionHandler.class, PushInstallationControllerTest.TestSecurityConfiguration.class})
class PushInstallationControllerTest {

    private static final String ROUTE = "/api/push/installations";
    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PushInstallationRepository repository;

    private MockMvc mockMvc;
    private ObjectId id;
    private String authorization;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
        id = new ObjectId();
        authorization = "Bearer " + token("12");
    }

    @Test
    void registersWithTheJwtProfileAndOnlyReturnsTheInternalIdAndStatus() throws Exception {
        when(repository.register(12, "fid-one", NOW)).thenReturn(active(12, "fid-one", id));

        mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installationId\":\"fid-one\",\"profileId\":99,\"status\":\"REVOKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toHexString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.profileId").doesNotExist())
                .andExpect(jsonPath("$.installationId").doesNotExist())
                .andExpect(jsonPath("$.platform").doesNotExist());

        verify(repository).register(12, "fid-one", NOW);
    }

    @Test
    void acceptsRepeatedRegistrationsAndReturnsTheSameInternalId() throws Exception {
        when(repository.register(12, "fid-one", NOW)).thenReturn(active(12, "fid-one", id));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"installationId\":\"fid-one\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toHexString()));
        }
        verify(repository, times(2)).register(12, "fid-one", NOW);
    }

    @Test
    void allowsMultipleInstallationsForTheSameProfile() throws Exception {
        ObjectId secondId = new ObjectId();
        when(repository.register(12, "fid-one", NOW)).thenReturn(active(12, "fid-one", id));
        when(repository.register(12, "fid-two", NOW)).thenReturn(active(12, "fid-two", secondId));

        mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationId\":\"fid-one\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toHexString()));
        mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationId\":\"fid-two\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(secondId.toHexString()));
    }

    @Test
    void associatesTheSameFidWithTheNewAuthenticatedProfile() throws Exception {
        when(repository.register(13, "fid-one", NOW)).thenReturn(active(13, "fid-one", id));

        mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("13"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationId\":\"fid-one\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toHexString()));

        verify(repository).register(13, "fid-one", NOW);
    }

    @Test
    void revokesOnlyUsingTheAuthenticatedProfilesIdAndAllowsRepeats() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete(ROUTE + "/{id}", id).param("profileId", "99")
                            .header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        }
        verify(repository, times(2)).revoke(id, 12, NOW);
    }

    @Test
    void requiresAuthenticationForBothOperations() throws Exception {
        mockMvc.perform(put(ROUTE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"installationId\":\"fid-one\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ROUTE + "/{id}", id)).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnInvalidProfileInTheJwt() throws Exception {
        String invalid = "Bearer " + token("invalid");
        mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, invalid)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationId\":\"fid-one\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ROUTE + "/{id}", id).header(HttpHeaders.AUTHORIZATION, invalid))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"installationId\":null}", "{\"installationId\":\"\"}",
            "{\"installationId\":\"fid one\"}"})
    void rejectsMissingOrInvalidFids(String body) throws Exception {
        mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Firebase Installation ID invalido"));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "[]", "{\"installationId\":{}}"})
    void returnsASafeErrorForUnreadableBodies(String body) throws Exception {
        mockMvc.perform(put(ROUTE).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisicao invalido"));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnInvalidInternalIdWithoutQueryingTheDatabase() throws Exception {
        mockMvc.perform(delete(ROUTE + "/invalid").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Id do cadastro de instalacao invalido"));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void allowsTheNewCorsMethodsFromAConfiguredOrigin(String method) throws Exception {
        mockMvc.perform(options(ROUTE).header(HttpHeaders.ORIGIN, "https://app.nexus.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.nexus.test"));
    }

    @Test
    void rejectsPreflightFromAnUnknownOrigin() throws Exception {
        mockMvc.perform(options(ROUTE).header(HttpHeaders.ORIGIN, "https://unknown.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());
    }

    private PushInstallation active(int profileId, String fid, ObjectId installationId) {
        return new PushInstallation(installationId, profileId, fid, PushInstallationStatus.ACTIVE,
                NOW, NOW, NOW, null, null);
    }

    private String token(String profileId) {
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef0123456789abcdef"
                .getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("nexus-auth-test").subject(profileId)
                .issuedAt(now).expiresAt(now.plusSeconds(600)).claim("profile_type", "HOUSEHOLD").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
