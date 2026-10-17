package com.example.nexusnotify.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "notify.core")
public record CoreProperties(
        URI baseUrl,
        String token,
        Duration connectTimeout,
        Duration readTimeout
) {

    public CoreProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("A URL do Core deve ser um endereco HTTP ou HTTPS valido");
        }
        if (connectTimeout == null || readTimeout == null
                || connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("Os timeouts da integracao com o Core devem ser positivos");
        }
    }
}
