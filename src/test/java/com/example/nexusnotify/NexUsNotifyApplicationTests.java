package com.example.nexusnotify;

import com.example.nexusnotify.config.properties.AuthProperties;
import com.example.nexusnotify.config.properties.CoreProperties;
import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.config.properties.SchedulerProperties;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.scheduler.NotificationScheduler;
import com.example.nexusnotify.service.NotificationGenerationService;
import com.example.nexusnotify.service.NotificationWriter;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    "notify.core.connect-timeout=2s",
    "notify.core.read-timeout=5s",
    "notify.scheduler.enabled=false",
    "notify.push.enabled=false",
    "app.auth.issuer=nexus-auth-test",
    "app.auth.jwt-secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class NexUsNotifyApplicationTests {

    @MockitoBean
    private MongoClient mongoClient;

    @MockitoBean
    private HttpClient coreHttpClient;

    @Autowired
    private MongoProperties mongoProperties;

    @Autowired
    private CoreProperties coreProperties;

    @Autowired
    private CoreClient coreClient;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private Clock notificationClock;

    @Autowired
    private SchedulerProperties schedulerProperties;

    @Autowired
    private NotificationGenerationService generationService;

    @Autowired
    private NotificationWriter notificationWriter;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private WebApplicationContext webContext;

    @Test
    void contextLoads() {
    }

    @Test
    void exposesSwaggerWithoutAuthenticationAndKeepsApplicationEndpointsProtected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(context.getBean(FilterChainProxy.class)).build();

        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        mockMvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("NexUs Notify API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].BearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/notifications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/{id}/read'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/push/installations'].put").exists())
                .andExpect(jsonPath("$.paths['/api/push/installations/{id}'].delete").exists());
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/test/read")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/push/installations")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/push/installations/test")).andExpect(status().isUnauthorized());
    }

    @Test
    void loadsMongoSettingsFromBootFourNamespace() {
        assertEquals("mongodb://localhost:27017/notify_test", mongoProperties.getUri());
        assertEquals("notify_test", mongoProperties.getDatabase());
    }

    @Test
    void loadsCoreSettingsAndRegistersTheClient() {
        assertEquals(URI.create("http://core.test"), coreProperties.baseUrl());
        assertEquals("notify-test-token", coreProperties.token());
        assertEquals(Duration.ofSeconds(2), coreProperties.connectTimeout());
        assertEquals(Duration.ofSeconds(5), coreProperties.readTimeout());
        assertNotNull(coreClient);
    }

    @Test
    void loadsNotificationRulesAndRegistersTheClock() {
        assertEquals(List.of(3, 1, 0), notificationProperties.productDaysBefore());
        assertEquals(List.of(7, 3, 1, 0), notificationProperties.eventDaysBefore());
        assertEquals(100, notificationProperties.pageSize());
        assertEquals(ZoneId.of("America/Sao_Paulo"), notificationClock.getZone());
    }

    @Test
    void registersGenerationAndDisablesTheJobDuringTests() {
        assertNotNull(generationService);
        assertNotNull(notificationWriter);
        assertEquals("0 0 11 * * *", schedulerProperties.cron());
        assertFalse(schedulerProperties.enabled());
        assertEquals(0, context.getBeansOfType(NotificationScheduler.class).size());
    }

    @Test
    void loadsAuthenticationSettingsAndRegistersTheDecoder() {
        assertEquals("nexus-auth-test", authProperties.issuer());
        assertNotNull(jwtDecoder);
    }

}
