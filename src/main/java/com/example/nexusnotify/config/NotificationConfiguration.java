package com.example.nexusnotify.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({NotificationProperties.class, SchedulerProperties.class})
public class NotificationConfiguration {

    @Bean
    Clock notificationClock(NotificationProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
