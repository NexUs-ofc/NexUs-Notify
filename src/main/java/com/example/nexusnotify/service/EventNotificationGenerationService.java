package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.EventCandidate;
import com.example.nexusnotify.service.rule.UpcomingEventRule;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Collections;

@Service
public class EventNotificationGenerationService {

    private final CoreClient core;
    private final NotificationCreationService creation;
    private final UpcomingEventRule rule;
    private final NotificationProperties properties;

    public EventNotificationGenerationService(CoreClient core, NotificationCreationService creation,
                                              UpcomingEventRule rule, NotificationProperties properties) {
        this.core = core;
        this.creation = creation;
        this.rule = rule;
        this.properties = properties;
    }

    public int generate(LocalDate today) {
        LocalDate from = today.plusDays(Collections.min(properties.eventDaysBefore()));
        LocalDate until = today.plusDays(Collections.max(properties.eventDaysBefore()));
        int page = 0;
        int created = 0;
        CandidatePage<EventCandidate> candidates;
        do {
            candidates = core.events(from, until, page, properties.pageSize());
            for (EventCandidate event : candidates.content()) {
                created += creation.create(rule.evaluate(event, today));
            }
            page++;
        } while (candidates.hasNext());
        return created;
    }
}
