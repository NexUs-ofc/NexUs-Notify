package com.example.nexusnotify;

import com.example.nexusnotify.config.CoreProperties;
import com.example.nexusnotify.infra.core.CoreClient;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.mongodb.uri=mongodb://localhost:27017/notify_test",
    "spring.mongodb.database=notify_test",
    "spring.data.mongodb.auto-index-creation=false",
    "notify.core.base-url=http://core.test",
    "notify.core.token=notify-test-token",
    "notify.core.connect-timeout=2s",
    "notify.core.read-timeout=5s"
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

}
