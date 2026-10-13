package com.example.nexusnotify.service;

import com.example.nexusnotify.config.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.EventCandidate;
import com.example.nexusnotify.infra.core.ProductCandidate;
import com.example.nexusnotify.service.rule.ProductExpiryRule;
import com.example.nexusnotify.service.rule.UpcomingEventRule;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

@Service
public class NotificationGenerationService {

    private final CoreClient coreClient;
    private final NotificationWriter writer;
    private final ProductExpiryRule productRule;
    private final UpcomingEventRule eventRule;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationGenerationService(CoreClient coreClient, NotificationWriter writer,
                                         ProductExpiryRule productRule, UpcomingEventRule eventRule,
                                         NotificationProperties properties, Clock clock) {
        this.coreClient = coreClient;
        this.writer = writer;
        this.productRule = productRule;
        this.eventRule = eventRule;
        this.properties = properties;
        this.clock = clock;
    }

    public int generate() {
        LocalDate today = LocalDate.now(clock);
        return generateProducts(today) + generateEvents(today);
    }

    private int generateProducts(LocalDate today) {
        LocalDate until = today.plusDays(Collections.max(properties.productDaysBefore()));

        int page = 0;
        int created = 0;
        CandidatePage<ProductCandidate> candidates;

        do {
            candidates = coreClient.products(today, until, page, properties.pageSize());
            for (ProductCandidate product : candidates.content()) {
                created += create(productRule.evaluate(product, today));
            }
            page++;
        } while (candidates.hasNext());

        return created;
    }

    private int generateEvents(LocalDate today) {
        LocalDate from = today.plusDays(Collections.min(properties.eventDaysBefore()));
        LocalDate until = today.plusDays(Collections.max(properties.eventDaysBefore()));

        int page = 0;
        int created = 0;
        CandidatePage<EventCandidate> candidates;

        do {
            candidates = coreClient.events(from, until, page, properties.pageSize());
            for (EventCandidate event : candidates.content()) {
                created += create(eventRule.evaluate(event, today));
            }
            page++;
        } while (candidates.hasNext());

        return created;
    }

    private int create(Optional<Notification> result) {
        if (result.isEmpty()) {
            return 0;
        }
        return writer.saveIfAbsent(result.get()) ? 1 : 0;
    }
}
