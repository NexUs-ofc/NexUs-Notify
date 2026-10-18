package com.example.nexusnotify.service.rule;

import com.example.nexusnotify.config.properties.NearbyStoreProperties;
import com.example.nexusnotify.domain.NearbyStoreRotation;
import com.example.nexusnotify.infra.core.NearbyStoreOption;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NearbyStoreRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-17T14:00:00Z");
    private final NearbyStoreRule rule = new NearbyStoreRule(new NearbyStoreProperties(true, 5, 3));
    private final NearbyStoreOption a = new NearbyStoreOption(1, "Loja A", 1);
    private final NearbyStoreOption b = new NearbyStoreOption(2, "Loja B", 2);
    private final NearbyStoreOption c = new NearbyStoreOption(3, "Loja C", 3);

    @Test
    void choosesTheNearestUnseenStoreRegardlessOfResponseOrder() {
        var selection = rule.evaluate(new NearbyStoreRotation(12), List.of(c, a, b), Set.of(), NOW).orElseThrow();

        assertThat(selection.store()).isEqualTo(a);
        assertThat(selection.cycle()).isEqualTo(1);
        assertThat(selection.newStore()).isFalse();
    }

    @Test
    void enforcesSeventyTwoHoursAcrossBothKindsOfSuggestionAndAllowsTheBoundary() {
        NearbyStoreRotation rotation = new NearbyStoreRotation(12);
        rotation.registerSuggestion(1, 1, NOW.plus(Duration.ofDays(3)));

        assertThat(rule.evaluate(rotation, List.of(b), Set.of(2), NOW.plusSeconds(259199))).isEmpty();
        assertThat(rule.evaluate(rotation, List.of(b), Set.of(2), NOW.plusSeconds(259200))).isPresent();
    }

    @Test
    void followsTheAgreedExampleWithANewStoreBetweenRegularSuggestions() {
        NearbyStoreRotation rotation = new NearbyStoreRotation(12);
        NearbyStoreOption d = new NearbyStoreOption(4, "Loja D", 0.5);
        var first = rule.evaluate(rotation, List.of(a, b, c), Set.of(), NOW).orElseThrow();
        rotation.registerSuggestion(first.store().storeId(), first.cycle(), NOW.plus(Duration.ofDays(3)));

        assertThat(rule.evaluate(rotation, List.of(d, a, b, c), Set.of(4), NOW.plus(Duration.ofDays(1))))
                .isEmpty();
        var novelty = rule.evaluate(rotation, List.of(d, a, b, c), Set.of(4), NOW.plus(Duration.ofDays(3)))
                .orElseThrow();
        assertThat(novelty.store()).isEqualTo(d);
        assertThat(novelty.newStore()).isTrue();
        rotation.registerSuggestion(4, novelty.cycle(), NOW.plus(Duration.ofDays(6)));

        var next = rule.evaluate(rotation, List.of(d, a, b, c), Set.of(), NOW.plus(Duration.ofDays(6)))
                .orElseThrow();
        assertThat(next.store()).isEqualTo(b);
        assertThat(rotation.getShownStoreIds()).containsExactlyInAnyOrder(1, 4);
    }

    @Test
    void prioritizesTheNearestPendingNoveltyEvenWhenARegularStoreIsCloser() {
        var selected = rule.evaluate(new NearbyStoreRotation(12), List.of(a, b, c), Set.of(2, 3), NOW)
                .orElseThrow();

        assertThat(selected.store()).isEqualTo(b);
        assertThat(selected.newStore()).isTrue();
    }

    @Test
    void doesNotRepeatAShownStoreJustBecauseItIsDiscoveredAsNew() {
        NearbyStoreRotation rotation = new NearbyStoreRotation(12);
        rotation.registerSuggestion(1, 1, NOW);

        assertThat(rule.evaluate(rotation, List.of(a, b), Set.of(1), NOW).orElseThrow().store()).isEqualTo(b);
    }

    @Test
    void restartsOnlyAfterAllCurrentlyAvailableStoresHaveBeenShown() {
        NearbyStoreRotation rotation = new NearbyStoreRotation(12);
        rotation.registerSuggestion(1, 1, NOW);
        rotation.registerSuggestion(2, 1, NOW);
        rotation.registerSuggestion(3, 1, NOW);

        var selected = rule.evaluate(rotation, List.of(a, b, c), Set.of(), NOW).orElseThrow();

        assertThat(selected.cycle()).isEqualTo(2);
        assertThat(selected.store()).isEqualTo(a);
        rotation.registerSuggestion(selected.store().storeId(), selected.cycle(), NOW.plus(Duration.ofDays(3)));
        assertThat(rotation.getShownStoreIds()).containsExactly(1);
    }

    @Test
    void avoidsThePreviousLastStoreAtTheStartOfANewCycleWhenItIsNowTheClosest() {
        NearbyStoreRotation rotation = new NearbyStoreRotation(12);
        rotation.registerSuggestion(2, 1, NOW);
        rotation.registerSuggestion(1, 1, NOW);

        var selected = rule.evaluate(rotation, List.of(a, b), Set.of(), NOW).orElseThrow();

        assertThat(selected.store()).isEqualTo(b);
        assertThat(selected.cycle()).isEqualTo(2);
    }

    @Test
    void allowsTheOnlyAvailableStoreAgainInTheNextCycle() {
        NearbyStoreRotation rotation = new NearbyStoreRotation(12);
        rotation.registerSuggestion(1, 1, NOW);

        var selected = rule.evaluate(rotation, List.of(a), Set.of(), NOW).orElseThrow();

        assertThat(selected.store()).isEqualTo(a);
        assertThat(selected.cycle()).isEqualTo(2);
    }

    @Test
    void ignoresInactiveOrDistantPendingStoresMissingFromTheCurrentCoreResponse() {
        var selected = rule.evaluate(new NearbyStoreRotation(12), List.of(a), Set.of(99), NOW).orElseThrow();

        assertThat(selected.store()).isEqualTo(a);
        assertThat(selected.newStore()).isFalse();
    }

    @Test
    void includesFiveKilometersAndRejectsInvalidOrDistantCandidates() {
        NearbyStoreOption boundary = new NearbyStoreOption(5, "Limite", 5);
        List<NearbyStoreOption> stores = List.of(
                new NearbyStoreOption(6, "Fora", 5.001),
                new NearbyStoreOption(7, "Invalida", Double.NaN),
                new NearbyStoreOption(8, "Negativa", -1),
                boundary);

        assertThat(rule.evaluate(new NearbyStoreRotation(12), stores, Set.of(), NOW).orElseThrow().store())
                .isEqualTo(boundary);
        assertThat(rule.evaluate(new NearbyStoreRotation(12), List.of(), Set.of(), NOW)).isEmpty();
    }

    @Test
    void breaksDistanceTiesByStoreId() {
        NearbyStoreOption tied = new NearbyStoreOption(9, "Empate", 1);

        assertThat(rule.evaluate(new NearbyStoreRotation(12), List.of(tied, a), Set.of(), NOW)
                .orElseThrow().store()).isEqualTo(a);
    }
}
