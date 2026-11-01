package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.ShoppingListCandidate;
import com.example.nexusnotify.service.rule.ShoppingListReminderRule;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Collections;

@Service
public class ShoppingListNotificationGenerationService {

    private final CoreClient core;
    private final NotificationCreationService creation;
    private final ShoppingListReminderRule rule;
    private final NotificationProperties properties;

    public ShoppingListNotificationGenerationService(CoreClient core, NotificationCreationService creation,
                                                     ShoppingListReminderRule rule,
                                                     NotificationProperties properties) {
        this.core = core;
        this.creation = creation;
        this.rule = rule;
        this.properties = properties;
    }

    public int generate(LocalDate today) {
        LocalDate from = today.plusDays(Collections.min(properties.shoppingListDaysBefore()));
        LocalDate until = today.plusDays(Collections.max(properties.shoppingListDaysBefore()));
        int page = 0;
        int created = 0;
        CandidatePage<ShoppingListCandidate> candidates;
        do {
            candidates = core.shoppingLists(from, until, page, properties.pageSize());
            for (ShoppingListCandidate list : candidates.content()) {
                created += creation.create(rule.evaluate(list, today));
            }
            page++;
        } while (candidates.hasNext());
        return created;
    }
}
