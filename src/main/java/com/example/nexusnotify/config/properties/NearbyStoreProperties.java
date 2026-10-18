package com.example.nexusnotify.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "notify.nearby-stores")
public record NearbyStoreProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") double radiusKm,
        @DefaultValue("3") int intervalDays
) {

    public NearbyStoreProperties {
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > 100) {
            throw new IllegalArgumentException("O raio deve ser maior que zero e menor ou igual a 100 km");
        }
        if (intervalDays < 3) {
            throw new IllegalArgumentException("O intervalo entre sugestoes deve ser de pelo menos 3 dias");
        }
    }
}
