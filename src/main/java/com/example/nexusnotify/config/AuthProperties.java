package com.example.nexusnotify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String issuer, String jwtSecretBase64) {}
