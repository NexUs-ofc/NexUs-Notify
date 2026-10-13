package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.EventCandidate;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpcomingEventRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private final UpcomingEventRule rule = new UpcomingEventRule(new NotificationProperties(
            List.of(3, 1, 0), List.of(7, 3, 1, 0), 100, ZoneId.of("America/Sao_Paulo")));

    @Test
    void createsAlertsSevenThreeAndOneDayBeforeAndOnTheEventDay() {
        List<String> messages = List.of("O evento Jantar acontece em 7 dias.",
                "O evento Jantar acontece em 3 dias.", "O evento Jantar acontece amanhã.",
                "O evento Jantar acontece hoje.");
        List<Integer> days = List.of(7, 3, 1, 0);

        for (int index = 0; index < days.size(); index++) {
            int daysBefore = days.get(index);
            LocalDateTime date = TODAY.plusDays(daysBefore).atTime(19, 30);
            Notification notification = rule.evaluate(
                    new EventCandidate(12, "event-1", "Jantar", date), TODAY).orElseThrow();

            assertEquals(12, notification.getProfileId());
            assertEquals("event-1", notification.getSourceId());
            assertEquals(NotificationType.UPCOMING_EVENT, notification.getType());
            assertEquals("event:" + date + ":days:" + daysBefore, notification.getAlertKey());
            assertEquals(messages.get(index), notification.getMessage());
            assertEquals(daysBefore == 0 ? "Evento acontece hoje" : "Evento próximo", notification.getTitle());
        }
    }

    @Test
    void skipsPastEventsAndOtherAntecedences() {
        for (int daysBefore : List.of(-1, 2, 4, 5, 6, 8)) {
            EventCandidate event = new EventCandidate(12, "event-1", "Jantar",
                    TODAY.plusDays(daysBefore).atTime(19, 30));
            assertTrue(rule.evaluate(event, TODAY).isEmpty());
        }
    }

    @Test
    void distinguishesRemindersAndRescheduledEvents() {
        LocalDateTime date = TODAY.plusDays(7).atTime(19, 30);
        EventCandidate event = new EventCandidate(12, "event-1", "Jantar", date);
        Set<String> keys = new HashSet<>();

        for (int daysBefore : List.of(7, 3, 1, 0)) {
            keys.add(rule.evaluate(event, date.toLocalDate().minusDays(daysBefore)).orElseThrow().getAlertKey());
        }

        EventCandidate rescheduled = new EventCandidate(12, "event-1", "Jantar", date.plusHours(1));
        keys.add(rule.evaluate(rescheduled, TODAY).orElseThrow().getAlertKey());

        assertEquals(5, keys.size());
    }

    @Test
    void skipsEventsWithoutADate() {
        assertTrue(rule.evaluate(new EventCandidate(12, "event-1", "Jantar", null), TODAY).isEmpty());
    }
}
