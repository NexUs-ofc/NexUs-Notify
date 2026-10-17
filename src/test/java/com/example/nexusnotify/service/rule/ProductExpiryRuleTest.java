package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.ProductCandidate;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductExpiryRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private final ProductExpiryRule rule = new ProductExpiryRule(new NotificationProperties(
            List.of(3, 1, 0), List.of(7, 3, 1, 0), 100, ZoneId.of("America/Sao_Paulo")));

    @Test
    void createsAlertsThreeDaysBeforeOneDayBeforeAndOnExpiry() {
        List<String> messages = List.of("O produto Leite vence em 3 dias.",
                "O produto Leite vence amanhã.", "O produto Leite vence hoje.");
        List<Integer> days = List.of(3, 1, 0);

        for (int index = 0; index < days.size(); index++) {
            int daysBefore = days.get(index);
            LocalDate expiry = TODAY.plusDays(daysBefore);
            Notification notification = rule.evaluate(
                    new ProductCandidate(12, 45, "Leite", expiry), TODAY).orElseThrow();

            assertEquals(12, notification.getProfileId());
            assertEquals("45", notification.getSourceId());
            assertEquals(NotificationType.PRODUCT_EXPIRING, notification.getType());
            assertEquals("expiry:" + expiry + ":days:" + daysBefore, notification.getAlertKey());
            assertEquals(messages.get(index), notification.getMessage());
            assertFalse(notification.isRead());
        }
    }

    @Test
    void skipsExpiredProductsAndDaysWithoutAnAlert() {
        for (int daysBefore : List.of(-1, 2, 4)) {
            ProductCandidate product = new ProductCandidate(12, 45, "Leite", TODAY.plusDays(daysBefore));
            assertTrue(rule.evaluate(product, TODAY).isEmpty());
        }
    }

    @Test
    void distinguishesRemindersAndChangesToTheExpiryDate() {
        LocalDate expiry = TODAY.plusDays(3);
        ProductCandidate product = new ProductCandidate(12, 45, "Leite", expiry);
        Set<String> keys = new HashSet<>();

        for (int daysBefore : List.of(3, 1, 0)) {
            keys.add(rule.evaluate(product, expiry.minusDays(daysBefore)).orElseThrow().getAlertKey());
        }

        ProductCandidate changed = new ProductCandidate(12, 45, "Leite", expiry.plusDays(1));
        keys.add(rule.evaluate(changed, TODAY.plusDays(1)).orElseThrow().getAlertKey());

        assertEquals(4, keys.size());
    }

    @Test
    void skipsProductsWithoutAnExpiryDate() {
        assertTrue(rule.evaluate(new ProductCandidate(12, 45, "Leite", null), TODAY).isEmpty());
    }
}
