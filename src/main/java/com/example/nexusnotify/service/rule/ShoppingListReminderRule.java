package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.ShoppingListCandidate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class ShoppingListReminderRule {

    private final NotificationProperties properties;

    public ShoppingListReminderRule(NotificationProperties properties) {
        this.properties = properties;
    }

    public Optional<Notification> evaluate(ShoppingListCandidate list, LocalDate today) {
        if (list.date() == null) {
            return Optional.empty();
        }
        long daysBefore = ChronoUnit.DAYS.between(today, list.date());
        if (properties.shoppingListDaysBefore().stream().noneMatch(day -> day == daysBefore)) {
            return Optional.empty();
        }
        String when = switch ((int) daysBefore) {
            case 0 -> "hoje";
            case 1 -> "amanhã";
            default -> "daqui a " + daysBefore + " dias";
        };
        String title = daysBefore == 0 ? "Lista de compras marcada para hoje" : "Lembrete de lista de compras";
        String message = list.completed()
                ? "Você já concluiu as compras da lista " + list.title() + "? Ela está marcada para " + when + "!"
                : "A lista " + list.title() + " está marcada para " + when + "!";
        String alertKey = "shopping-list:" + list.date() + ":days:" + daysBefore;
        return Optional.of(new Notification(list.profileId(), NotificationType.SHOPPING_LIST_REMINDER,
                list.shoppingListId(), alertKey, title, message));
    }
}
