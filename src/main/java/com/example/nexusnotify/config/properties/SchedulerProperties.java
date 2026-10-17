package com.example.nexusnotify.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notify.scheduler")
public record SchedulerProperties(boolean enabled, String cron) {}
