package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NearbyStoreProperties;
import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.NearbyStoreCandidate;
import com.example.nexusnotify.infra.core.NewStoreCandidate;
import com.example.nexusnotify.infra.repository.NewStoreSuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class NearbyStoreNotificationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(NearbyStoreNotificationGenerationService.class);
    private final CoreClient core;
    private final NearbyStoreSuggestionService suggestions;
    private final NewStoreSuggestionRepository pending;
    private final NotificationCreationService creation;
    private final NearbyStoreProperties properties;
    private final NotificationProperties notifications;
    private final Clock clock;

    public NearbyStoreNotificationGenerationService(CoreClient core, NearbyStoreSuggestionService suggestions,
                                                    NewStoreSuggestionRepository pending,
                                                    NotificationCreationService creation,
                                                    NearbyStoreProperties properties,
                                                    NotificationProperties notifications, Clock clock) {
        this.core = core;
        this.suggestions = suggestions;
        this.pending = pending;
        this.creation = creation;
        this.properties = properties;
        this.notifications = notifications;
        this.clock = clock;
    }

    public int generate(LocalDate today) {
        if (!properties.enabled()) {
            return 0;
        }
        discoverNewStores(today);
        int page = 0;
        int created = 0;
        RuntimeException failure = null;
        CandidatePage<NearbyStoreCandidate> candidates;
        do {
            candidates = core.nearbyStores(properties.radiusKm(), page, notifications.pageSize());
            for (NearbyStoreCandidate candidate : candidates.content()) {
                try {
                    Optional<Notification> result = suggestions.generate(candidate);
                    if (result.isPresent()) {
                        created++;
                        creation.dispatchCreated(result.get());
                    }
                } catch (RuntimeException exception) {
                    logger.error("Falha na sugestao de loja profile_id={} error_type={}",
                            candidate.profileId(), exception.getClass().getSimpleName());
                    if (failure == null) {
                        failure = exception;
                    }
                }
            }
            page++;
        } while (candidates.hasNext());
        if (failure != null) {
            throw new IllegalStateException("A geracao de lojas proximas teve falhas parciais", failure);
        }
        return created;
    }

    private void discoverNewStores(LocalDate today) {
        LocalDate from = pending.lastScanDate().orElse(today);
        while (!from.isAfter(today)) {
            LocalDate until = from.plusDays(30).isAfter(today) ? today : from.plusDays(30);
            int page = 0;
            CandidatePage<NewStoreCandidate> candidates;
            do {
                candidates = core.newStores(from, until, properties.radiusKm(), page, notifications.pageSize());
                for (NewStoreCandidate store : candidates.content()) {
                    for (Integer profileId : store.profileIds()) {
                        pending.register(profileId, store.storeId(), store.createdDate(), clock.instant());
                    }
                }
                page++;
            } while (candidates.hasNext());
            pending.markScanned(until);
            from = until.plusDays(1);
        }
    }
}
