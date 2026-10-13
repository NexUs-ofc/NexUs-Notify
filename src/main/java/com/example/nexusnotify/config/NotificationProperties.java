package com.example.nexusnotify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.ZoneId;
import java.util.List;

@ConfigurationProperties(prefix = "notify.notification")
public record NotificationProperties(
        List<Integer> productDaysBefore,
        List<Integer> eventDaysBefore,
        int pageSize,
        ZoneId zoneId
) {

    public NotificationProperties {
        productDaysBefore = validateDays(productDaysBefore, 0);
        eventDaysBefore = validateDays(eventDaysBefore, 0);

        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("O tamanho do lote deve estar entre 1 e 500");
        }

        if (zoneId == null) {
            throw new IllegalArgumentException("O fuso das notificacoes deve ser informado");
        }
    }

    private static List<Integer> validateDays(List<Integer> days, int minimum) {
        if (days == null || days.isEmpty() || days.stream().anyMatch(day -> day == null || day < minimum)) {
            throw new IllegalArgumentException("As antecedencias das notificacoes sao invalidas");
        }
        return List.copyOf(days);
    }
}
