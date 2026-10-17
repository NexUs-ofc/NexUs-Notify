package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NearbyStoreProperties;
import com.example.nexusnotify.domain.NearbyStoreRotation;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.NearbyStoreCandidate;
import com.example.nexusnotify.infra.core.NearbyStoreOption;
import com.example.nexusnotify.infra.repository.NearbyStoreRotationRepository;
import com.example.nexusnotify.infra.repository.NewStoreSuggestionRepository;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import com.example.nexusnotify.service.rule.NearbyStoreRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NearbyStoreSuggestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T14:00:00Z");
    private final NearbyStoreRotationRepository rotations = mock(NearbyStoreRotationRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NewStoreSuggestionRepository pending = mock(NewStoreSuggestionRepository.class);
    private final NearbyStoreProperties properties = new NearbyStoreProperties(true, 5, 3);
    private NearbyStoreSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new NearbyStoreSuggestionService(rotations, notifications, pending,
                new NearbyStoreRule(properties), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(rotations.findById(12)).thenReturn(Optional.empty());
        when(pending.findPendingStoreIds(12)).thenReturn(Set.of());
        when(notifications.insert(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOneRegularSuggestionAndAdvancesTheSharedFrequencyAndCycle() {
        Notification result = service.generate(candidate()).orElseThrow();

        assertThat(result.getType()).isEqualTo(NotificationType.NEARBY_STORE);
        assertThat(result.getSourceId()).isEqualTo("1");
        assertThat(result.getAlertKey()).isEqualTo("nearby-store:cycle:1");
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        ArgumentCaptor<NearbyStoreRotation> state = ArgumentCaptor.forClass(NearbyStoreRotation.class);
        verify(rotations).save(state.capture());
        assertThat(state.getValue().getNextEligibleAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
        assertThat(state.getValue().getLastStoreId()).isEqualTo(1);
        assertThat(state.getValue().getShownStoreIds()).containsExactly(1);
        verify(pending).markPresented(12, 1, NOW);
    }

    @Test
    void turnsAPendingNoveltyIntoADistinctNotificationAndMarksItInTheSameCycle() {
        when(pending.findPendingStoreIds(12)).thenReturn(Set.of(2));

        Notification result = service.generate(candidate()).orElseThrow();

        assertThat(result.getType()).isEqualTo(NotificationType.NEW_NEARBY_STORE);
        assertThat(result.getSourceId()).isEqualTo("2");
        assertThat(result.getMessage()).contains("Loja B");
        ArgumentCaptor<NearbyStoreRotation> state = ArgumentCaptor.forClass(NearbyStoreRotation.class);
        verify(rotations).save(state.capture());
        assertThat(state.getValue().getShownStoreIds()).containsExactly(2);
        verify(pending).markPresented(12, 2, NOW);
    }

    @Test
    void keepsPendingSuggestionsDuringTheCooldownWithoutAdvancingTheState() {
        NearbyStoreRotation state = new NearbyStoreRotation(12);
        state.registerSuggestion(1, 1, NOW.plusSeconds(1));
        when(rotations.findById(12)).thenReturn(Optional.of(state));
        when(pending.findPendingStoreIds(12)).thenReturn(Set.of(2));

        assertThat(service.generate(candidate())).isEmpty();

        verify(rotations, never()).save(any());
        verify(notifications, never()).insert(any(Notification.class));
        verify(pending, never()).markPresented(12, 2, NOW);
    }

    @Test
    void consumesOverlappingDiscoveryForAStoreAlreadyShownInTheCurrentCycle() {
        NearbyStoreRotation state = new NearbyStoreRotation(12);
        state.registerSuggestion(1, 1, NOW);
        when(rotations.findById(12)).thenReturn(Optional.of(state));
        when(pending.findPendingStoreIds(12)).thenReturn(Set.of(1));

        assertThat(service.generate(candidate()).orElseThrow().getSourceId()).isEqualTo("2");

        verify(pending).markPresented(12, 1, NOW);
    }

    @Test
    void doesNotInsertANotificationWhenAnotherExecutionHasChangedTheProfileState() {
        when(rotations.save(any())).thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> service.generate(candidate()))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(notifications, never()).insert(any(Notification.class));
    }

    @Test
    void requiresOneMongoTransactionForTheStateNotificationAndPendingAcknowledgement() throws Exception {
        Transactional annotation = NearbyStoreSuggestionService.class
                .getMethod("generate", NearbyStoreCandidate.class).getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.transactionManager()).isEqualTo("nearbyStoreTransactionManager");
    }

    private NearbyStoreCandidate candidate() {
        return new NearbyStoreCandidate(12, List.of(
                new NearbyStoreOption(1, "Loja A", 1),
                new NearbyStoreOption(2, "Loja B", 2)));
    }
}
