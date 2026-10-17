package com.example.nexusnotify.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String issuer, String jwtSecretBase64) {}
