package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.properties.NearbyStoreProperties;
import com.example.nexusnotify.domain.NearbyStoreRotation;
import com.example.nexusnotify.infra.core.NearbyStoreOption;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class NearbyStoreRule {

    private static final Comparator<NearbyStoreOption> ORDER = Comparator
            .comparingDouble(NearbyStoreOption::distanceKm).thenComparing(NearbyStoreOption::storeId);

    private final NearbyStoreProperties properties;

    public NearbyStoreRule(NearbyStoreProperties properties) {
        this.properties = properties;
    }

    public Optional<Selection> evaluate(NearbyStoreRotation rotation, List<NearbyStoreOption> options,
                                        Set<Integer> newStoreIds, Instant now) {
        if (!rotation.canSuggestAt(now)) {
            return Optional.empty();
        }
        List<NearbyStoreOption> available = options.stream()
                .filter(Objects::nonNull)
                .filter(store -> store.storeId() != null && store.storeId() > 0)
                .filter(store -> store.name() != null && !store.name().isBlank())
                .filter(store -> Double.isFinite(store.distanceKm()) && store.distanceKm() >= 0
                        && store.distanceKm() <= properties.radiusKm())
                .sorted(ORDER).toList();
        if (available.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> shown = rotation.getShownStoreIds();
        List<NearbyStoreOption> unseen = available.stream()
                .filter(store -> !shown.contains(store.storeId())).toList();
        if (!unseen.isEmpty()) {
            NearbyStoreOption selected = unseen.stream()
                    .filter(store -> newStoreIds.contains(store.storeId())).findFirst().orElse(unseen.get(0));
            return Optional.of(new Selection(rotation.getCycle(), selected, newStoreIds.contains(selected.storeId())));
        }

        NearbyStoreOption selected = available.stream()
                .filter(store -> !Objects.equals(store.storeId(), rotation.getLastStoreId()))
                .findFirst().orElse(available.get(0));
        return Optional.of(new Selection(Math.addExact(rotation.getCycle(), 1), selected, false));
    }

    public record Selection(long cycle, NearbyStoreOption store, boolean newStore) {}
}
