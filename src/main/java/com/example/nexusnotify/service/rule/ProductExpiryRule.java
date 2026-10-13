package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.ProductCandidate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class ProductExpiryRule {

    private final NotificationProperties properties;

    public ProductExpiryRule(NotificationProperties properties) {
        this.properties = properties;
    }

    public Optional<Notification> evaluate(ProductCandidate product, LocalDate today) {
        if (product.expiryDate() == null) {
            return Optional.empty();
        }

        long daysBefore = ChronoUnit.DAYS.between(today, product.expiryDate());
        if (properties.productDaysBefore().stream().noneMatch(day -> day == daysBefore)) {
            return Optional.empty();
        }

        String title = daysBefore == 0 ? "Produto vence hoje" : "Produto próximo do vencimento";
        String when = switch ((int) daysBefore) {
            case 0 -> "hoje";
            case 1 -> "amanhã";
            default -> "em " + daysBefore + " dias";
        };
        String alertKey = "expiry:" + product.expiryDate() + ":days:" + daysBefore;
        String message = "O produto " + product.foodName() + " vence " + when + ".";

        return Optional.of(new Notification(
                product.profileId(), NotificationType.PRODUCT_EXPIRING,
                product.itemId().toString(), alertKey, title, message));
    }
}
