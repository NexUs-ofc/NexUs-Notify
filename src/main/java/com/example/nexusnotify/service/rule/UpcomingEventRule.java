package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.EventCandidate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class UpcomingEventRule {

    private final NotificationProperties properties;

    public UpcomingEventRule(NotificationProperties properties) {
        this.properties = properties;
    }

    public Optional<Notification> evaluate(EventCandidate event, LocalDate today) {
        if (event.date() == null) {
            return Optional.empty();
        }

        long daysBefore = ChronoUnit.DAYS.between(today, event.date().toLocalDate());
        if (properties.eventDaysBefore().stream().noneMatch(day -> day == daysBefore)) {
            return Optional.empty();
        }

        String title = daysBefore == 0 ? "Evento acontece hoje" : "Evento próximo";
        String when = switch ((int) daysBefore) {
            case 0 -> "hoje";
            case 1 -> "amanhã";
            default -> "em " + daysBefore + " dias";
        };
        String alertKey = "event:" + event.date() + ":days:" + daysBefore;
        String message = "O evento " + event.title() + " acontece " + when + ".";

        return Optional.of(new Notification(
                event.profileId(), NotificationType.UPCOMING_EVENT,
                event.eventId(), alertKey, title, message));
    }
}
