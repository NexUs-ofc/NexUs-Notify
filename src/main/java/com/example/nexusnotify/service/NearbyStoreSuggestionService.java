package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NearbyStoreProperties;
import com.example.nexusnotify.domain.NearbyStoreRotation;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.NearbyStoreCandidate;
import com.example.nexusnotify.infra.repository.NearbyStoreRotationRepository;
import com.example.nexusnotify.infra.repository.NewStoreSuggestionRepository;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import com.example.nexusnotify.service.rule.NearbyStoreRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class NearbyStoreSuggestionService {

    private final NearbyStoreRotationRepository rotations;
    private final NotificationRepository notifications;
    private final NewStoreSuggestionRepository pending;
    private final NearbyStoreRule rule;
    private final NearbyStoreProperties properties;
    private final Clock clock;

    public NearbyStoreSuggestionService(NearbyStoreRotationRepository rotations,
                                        NotificationRepository notifications, NewStoreSuggestionRepository pending,
                                        NearbyStoreRule rule, NearbyStoreProperties properties, Clock clock) {
        this.rotations = rotations;
        this.notifications = notifications;
        this.pending = pending;
        this.rule = rule;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(transactionManager = "nearbyStoreTransactionManager")
    public Optional<Notification> generate(NearbyStoreCandidate candidate) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        NearbyStoreRotation rotation = rotations.findById(candidate.profileId())
                .orElseGet(() -> new NearbyStoreRotation(candidate.profileId()));
        Set<Integer> newStoreIds = new HashSet<>(pending.findPendingStoreIds(candidate.profileId()));
        for (Integer shown : rotation.getShownStoreIds()) {
            if (newStoreIds.remove(shown)) {
                pending.markPresented(candidate.profileId(), shown, now);
            }
        }
        Optional<NearbyStoreRule.Selection> result = rule.evaluate(rotation, candidate.stores(), newStoreIds, now);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        NearbyStoreRule.Selection selection = result.get();
        String name = selection.store().name();
        Notification notification = new Notification(
                candidate.profileId(),
                selection.newStore() ? NotificationType.NEW_NEARBY_STORE : NotificationType.NEARBY_STORE,
                selection.store().storeId().toString(),
                "nearby-store:cycle:" + selection.cycle(),
                selection.newStore() ? "Nova loja perto de você" : "Loja próxima de você",
                selection.newStore()
                        ? "Uma nova loja chegou perto de você: " + name + "."
                        : "A loja " + name + " fica próxima do seu endereço cadastrado.",
                now
        );
        rotation.registerSuggestion(selection.store().storeId(), selection.cycle(),
                now.plus(Duration.ofDays(properties.intervalDays())));
        rotations.save(rotation);
        Notification saved = notifications.insert(notification);
        pending.markPresented(candidate.profileId(), selection.store().storeId(), now);
        return Optional.of(saved);
    }
}
