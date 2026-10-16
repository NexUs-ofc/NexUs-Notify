package com.example.nexusnotify;

import com.example.nexusnotify.config.AuthProperties;
import com.example.nexusnotify.config.CoreProperties;
import com.example.nexusnotify.config.NotificationProperties;
import com.example.nexusnotify.config.SchedulerProperties;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void contextLoads() {
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
