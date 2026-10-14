package com.example.nexusnotify;

import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.ProductCandidate;
import com.example.nexusnotify.service.NotificationGenerationService;
import com.example.nexusnotify.service.NotificationWriter;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.mongodb.uri=mongodb://localhost:27017/notify_test",
    "spring.mongodb.database=notify_test",
    "spring.data.mongodb.auto-index-creation=false",
    "notify.core.base-url=http://core.test",
    "notify.core.token=notify-test-token",
    "notify.scheduler.enabled=false",
    "app.auth.issuer=nexus-auth-test",
    "app.auth.jwt-secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class ObservabilityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private NotificationGenerationService generationService;

    @Autowired
    private Clock clock;

    @MockitoBean
    private MongoClient mongoClient;

    @MockitoBean
    private HttpClient coreHttpClient;

    @MockitoBean
    private CoreClient coreClient;

    @MockitoBean
    private NotificationWriter writer;

    private MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
        MongoDatabase database = mock(MongoDatabase.class);
        MongoIterable<String> databases = mock(MongoIterable.class);
        when(mongoClient.listDatabaseNames()).thenReturn(databases);
        when(mongoClient.getDatabase("notify_test")).thenReturn(database);
        when(database.runCommand(any(Document.class))).thenReturn(new Document("maxWireVersion", 17));
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept("notify_test");
            return null;
        }).when(databases).forEach(any());
    }

    @Test
    void exposesHealthPubliclyAndHidesDependencyDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());

        verify(mongoClient).listDatabaseNames();
        verify(mongoClient.getDatabase("notify_test")).runCommand(new Document("hello", 1));
    }

    @Test
    void reportsMongoFailuresWithoutExposingTheError() throws Exception {
        when(mongoClient.listDatabaseNames()).thenThrow(new MongoException("mongo-test-unavailable"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void requiresAuthenticationForMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics/notify.generation.duration"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOperationalAccessFromANonAdminProfile() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, token("HOUSEHOLD")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics/notify.generation.duration")
                        .header(HttpHeaders.AUTHORIZATION, token("HOUSEHOLD")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsAndFiltersGenerationMetricsForAnAdmin() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").value(hasItem("notify.generation.duration")));
        mockMvc.perform(get("/actuator/metrics/notify.generation.duration").param("tag", "result:failure")
                        .header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUnit").value("seconds"))
                .andExpect(jsonPath("$.measurements[*].statistic").value(hasItem("COUNT")))
                .andExpect(jsonPath("$.measurements[*].statistic").value(hasItem("TOTAL_TIME")));
    }

    @Test
    void exposesCountersForNotificationsCreatedByTheService() throws Exception {
        LocalDate today = LocalDate.now(clock);
        ProductCandidate product = new ProductCandidate(12, 45, "Leite", today.plusDays(1));
        when(coreClient.products(any(), any(), anyInt(), anyInt()))
                .thenReturn(new CandidatePage<>(List.of(product), false));
        when(coreClient.events(any(), any(), anyInt(), anyInt())).thenReturn(new CandidatePage<>(List.of(), false));
        when(writer.saveIfAbsent(any())).thenReturn(true);

        generationService.generate();

        mockMvc.perform(get("/actuator/metrics/notify.notifications.created")
                        .param("tag", "type:PRODUCT_EXPIRING")
                        .header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].statistic").value("COUNT"))
                .andExpect(jsonPath("$.measurements[0].value").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.availableTags[0].tag").value("application"))
                .andExpect(jsonPath("$.availableTags[0].values").value(hasItem("NexUs-Notify")));
    }

    @Test
    void keepsUnselectedActuatorEndpointsUnavailableEvenToAdmins() throws Exception {
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isNotFound());
    }

    private String token(String profileType) {
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef0123456789abcdef"
                .getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("nexus-auth-test").subject("12")
                .issuedAt(now).expiresAt(now.plusSeconds(600)).claim("profile_type", profileType).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
