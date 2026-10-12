package com.example.nexusnotify;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.mongodb.uri=mongodb://localhost:27017/notify_test",
    "spring.mongodb.database=notify_test",
    "spring.data.mongodb.auto-index-creation=false"
})
class NexUsNotifyApplicationTests {

    @MockitoBean
    private MongoClient mongoClient;

    @Autowired
    private MongoProperties mongoProperties;

    @Test
    void contextLoads() {
    }

    @Test
    void loadsMongoSettingsFromBootFourNamespace() {
        assertEquals("mongodb://localhost:27017/notify_test", mongoProperties.getUri());
        assertEquals("notify_test", mongoProperties.getDatabase());
    }

}
