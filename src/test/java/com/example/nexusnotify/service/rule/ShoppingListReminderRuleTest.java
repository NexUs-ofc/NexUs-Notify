package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.ShoppingListCandidate;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingListReminderRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    private final ShoppingListReminderRule rule = new ShoppingListReminderRule(new NotificationProperties(
            List.of(3, 1, 0), List.of(7, 3, 1, 0), List.of(7, 3, 1, 0), 100,
            ZoneId.of("America/Sao_Paulo")));

    @Test
    void sendsBothMessagesAtEveryConfiguredAntecedenceAndUsesTheListAsTarget() {
        for (int days : List.of(7, 3, 1, 0)) {
            String when = days == 0 ? "hoje" : days == 1 ? "amanhã" : "daqui a " + days + " dias";
            for (boolean completed : List.of(false, true)) {
                LocalDate date = TODAY.plusDays(days);
                Notification notification = rule.evaluate(
                        new ShoppingListCandidate(12, "list-1", "Mercado", date, completed), TODAY).orElseThrow();

                assertThat(notification.getProfileId()).isEqualTo(12);
                assertThat(notification.getSourceId()).isEqualTo("list-1");
                assertThat(notification.getType()).isEqualTo(NotificationType.SHOPPING_LIST_REMINDER);
                assertThat(notification.getAlertKey()).isEqualTo("shopping-list:" + date + ":days:" + days);
                assertThat(notification.getMessage()).isEqualTo(completed
                        ? "Você já concluiu as compras da lista Mercado? Ela está marcada para " + when + "!"
                        : "A lista Mercado está marcada para " + when + "!");
            }
        }
    }

    @Test
    void ignoresUndatedPastAndOtherAntecedences() {
        assertThat(rule.evaluate(new ShoppingListCandidate(12, "id", "Mercado", null, true), TODAY)).isEmpty();
        for (int days : List.of(-1, 2, 4, 5, 6, 8)) {
            assertThat(rule.evaluate(new ShoppingListCandidate(
                    12, "id", "Mercado", TODAY.plusDays(days), false), TODAY)).isEmpty();
        }
    }

    @Test
    void distinguishesEveryReminderAndRescheduledDateButNotCompletionOrTitle() {
        LocalDate date = TODAY.plusDays(7);
        Set<String> keys = new HashSet<>();
        for (int days : List.of(7, 3, 1, 0)) {
            keys.add(rule.evaluate(new ShoppingListCandidate(12, "id", "Mercado", date, false),
                    date.minusDays(days)).orElseThrow().getAlertKey());
        }
        keys.add(rule.evaluate(new ShoppingListCandidate(12, "id", "Novo nome", date, true),
                TODAY).orElseThrow().getAlertKey());
        assertThat(keys).hasSize(4);
        keys.add(rule.evaluate(new ShoppingListCandidate(12, "id", "Mercado", TODAY.plusDays(1), true),
                TODAY).orElseThrow().getAlertKey());
        assertThat(keys).hasSize(5);
    }

    @Test
    void usesShoppingListAntecedencesIndependentlyOfEvents() {
        ShoppingListReminderRule custom = new ShoppingListReminderRule(new NotificationProperties(
                List.of(0), List.of(7), List.of(2), 100, ZoneId.of("America/Sao_Paulo")));
        assertThat(custom.evaluate(new ShoppingListCandidate(12, "id", "Mercado", TODAY.plusDays(2), false), TODAY))
                .isPresent();
        assertThat(custom.evaluate(new ShoppingListCandidate(12, "id", "Mercado", TODAY.plusDays(7), false), TODAY))
                .isEmpty();
    }
}
