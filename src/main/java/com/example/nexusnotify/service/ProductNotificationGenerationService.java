package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.ProductCandidate;
import com.example.nexusnotify.service.rule.ProductExpiryRule;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Collections;

@Service
public class ProductNotificationGenerationService {

    private final CoreClient core;
    private final NotificationCreationService creation;
    private final ProductExpiryRule rule;
    private final NotificationProperties properties;

    public ProductNotificationGenerationService(CoreClient core, NotificationCreationService creation,
                                                ProductExpiryRule rule, NotificationProperties properties) {
        this.core = core;
        this.creation = creation;
        this.rule = rule;
        this.properties = properties;
    }

    public int generate(LocalDate today) {
        LocalDate until = today.plusDays(Collections.max(properties.productDaysBefore()));
        int page = 0;
        int created = 0;
        CandidatePage<ProductCandidate> candidates;
        do {
            candidates = core.products(today, until, page, properties.pageSize());
            for (ProductCandidate product : candidates.content()) {
                created += creation.create(rule.evaluate(product, today));
            }
            page++;
        } while (candidates.hasNext());
        return created;
    }
}
