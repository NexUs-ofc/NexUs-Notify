package com.example.nexusnotify.controller;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.exception.GlobalExceptionHandler;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import com.example.nexusnotify.infra.security.SecurityConfiguration;
import com.example.nexusnotify.service.NotificationService;
import com.example.nexusnotify.util.CurrentProfileResolver;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class, properties = {
    "app.auth.issuer=nexus-auth-test",
    "app.auth.jwt-secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import({SecurityConfiguration.class, CurrentProfileResolver.class, NotificationService.class,
        GlobalExceptionHandler.class, NotificationControllerTest.TestSecurityConfiguration.class})
class NotificationControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T14:00:00Z");

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private NotificationRepository repository;

    private MockMvc mockMvc;
    private Notification notification;
    private ObjectId id;
    private String authorization;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
        id = new ObjectId();
        notification = new Notification(12, NotificationType.UPCOMING_EVENT, "event-id", "event:today",
                "Evento hoje", "O jantar acontece hoje.");
        ReflectionTestUtils.setField(notification, "id", id);
        authorization = "Bearer " + token("12");
    }

    @Test
    void listsTheAuthenticatedProfileWithDefaultPagination() throws Exception {
        when(repository.findByProfileIdOrderByCreatedAtDesc(eq(12), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(notification), PageRequest.of(0, 20), true));

        mockMvc.perform(get("/api/notifications").param("profileId", "99")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toHexString()))
                .andExpect(jsonPath("$.content[0].type").value("UPCOMING_EVENT"))
                .andExpect(jsonPath("$.content[0].sourceId").value("event-id"))
                .andExpect(jsonPath("$.content[0].title").value(notification.getTitle()))
                .andExpect(jsonPath("$.content[0].message").value(notification.getMessage()))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].profileId").doesNotExist())
                .andExpect(jsonPath("$.content[0].alertKey").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalElements").doesNotExist());

        verify(repository).findByProfileIdOrderByCreatedAtDesc(12,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void usesTheRequestedPageAndReturnsAnEmptyLastSlice() throws Exception {
        Pageable pageable = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "id"));
        when(repository.findByProfileIdOrderByCreatedAtDesc(12, pageable))
                .thenReturn(new SliceImpl<>(List.of(), pageable, false));

        mockMvc.perform(get("/api/notifications").param("page", "2").param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(repository).findByProfileIdOrderByCreatedAtDesc(12, pageable);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "page,invalid", "size,invalid", "page,2147483648"})
    void rejectsInvalidPagination(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/notifications").param(parameter, value)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(repository);
    }

    @Test
    void marksTheAuthenticatedProfileNotificationAsRead() throws Exception {
        when(repository.markAsRead(id, 12, NOW)).thenReturn(1L);

        mockMvc.perform(patch("/api/notifications/{id}/read", id).param("profileId", "99")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(repository).markAsRead(id, 12, NOW);
    }

    @Test
    void acceptsRepeatedReadRequests() throws Exception {
        notification.markAsRead(NOW.minusSeconds(60));
        when(repository.findByIdAndProfileId(id, 12)).thenReturn(Optional.of(notification));

        mockMvc.perform(patch("/api/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNoContent());
    }

    @Test
    void hidesNotificationsFromOtherProfiles() throws Exception {
        when(repository.findByIdAndProfileId(id, 13)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("13")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Notificacao nao encontrada"));

        verify(repository).markAsRead(id, 13, NOW);
        verify(repository).findByIdAndProfileId(id, 13);
    }

    @Test
    void rejectsAnInvalidNotificationId() throws Exception {
        mockMvc.perform(patch("/api/notifications/invalid/read")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Id de notificacao invalido"));

        verifyNoInteractions(repository);
    }

    @Test
    void requiresAuthenticationForBothEndpoints() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/{id}/read", id)).andExpect(status().isUnauthorized());

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnInvalidProfileInTheToken() throws Exception {
        String invalidAuthorization = "Bearer " + token("invalid");

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, invalidAuthorization))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, invalidAuthorization))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"NEARBY_STORE", "NEW_NEARBY_STORE"})
    void exposesStoreNotificationsThroughTheExistingProfileHistory(NotificationType type) throws Exception {
        notification = new Notification(12, type, "4", "nearby-store:cycle:1", "Loja próxima", "Loja D");
        ReflectionTestUtils.setField(notification, "id", id);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
        when(repository.findByProfileIdOrderByCreatedAtDesc(12, pageable))
                .thenReturn(new SliceImpl<>(List.of(notification), pageable, false));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value(type.name()))
                .andExpect(jsonPath("$.content[0].sourceId").value("4"))
                .andExpect(jsonPath("$.content[0].profileId").doesNotExist())
                .andExpect(jsonPath("$.content[0].alertKey").doesNotExist());
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
