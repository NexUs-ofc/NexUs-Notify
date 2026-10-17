package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NearbyStoreProperties;
import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.CoreIntegrationException;
import com.example.nexusnotify.infra.core.NearbyStoreCandidate;
import com.example.nexusnotify.infra.core.NearbyStoreOption;
import com.example.nexusnotify.infra.core.NewStoreCandidate;
import com.example.nexusnotify.infra.repository.NewStoreSuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NearbyStoreNotificationGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Instant NOW = Instant.parse("2026-10-17T14:00:00Z");
    private final CoreClient core = mock(CoreClient.class);
    private final NearbyStoreSuggestionService suggestions = mock(NearbyStoreSuggestionService.class);
    private final NewStoreSuggestionRepository pending = mock(NewStoreSuggestionRepository.class);
    private final NotificationCreationService creation = mock(NotificationCreationService.class);
    private NearbyStoreNotificationGenerationService service;

    @BeforeEach
    void setUp() {
        service = service(true);
        when(core.newStores(any(), any(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new CandidatePage<>(List.of(), false));
        when(core.nearbyStores(anyDouble(), anyInt(), anyInt())).thenReturn(new CandidatePage<>(List.of(), false));
        when(pending.lastScanDate()).thenReturn(Optional.empty());
        when(suggestions.generate(any())).thenReturn(Optional.empty());
    }

    @Test
    void capturesNewStoresEvenWhenTheProfileCannotYetReceiveAnotherSuggestion() {
        when(core.newStores(TODAY, TODAY, 5, 0, 2)).thenReturn(new CandidatePage<>(
                List.of(new NewStoreCandidate(4, "Loja D", TODAY, List.of(12, 13))), false));
        when(core.nearbyStores(5, 0, 2)).thenReturn(new CandidatePage<>(List.of(candidate(12)), false));

        assertThat(service.generate(TODAY)).isZero();

        InOrder order = inOrder(pending, suggestions);
        order.verify(pending).register(12, 4, TODAY, NOW);
        order.verify(pending).register(13, 4, TODAY, NOW);
        order.verify(pending).markScanned(TODAY);
        order.verify(suggestions).generate(candidate(12));
        verifyNoInteractions(creation);
    }

    @Test
    void continuesAfterEmptyPagesInBothCoreEndpointsAndDispatchesOnlyCommittedNotifications() {
        when(core.newStores(TODAY, TODAY, 5, 0, 2)).thenReturn(new CandidatePage<>(List.of(), true));
        when(core.newStores(TODAY, TODAY, 5, 1, 2)).thenReturn(new CandidatePage<>(
                List.of(new NewStoreCandidate(4, "Loja D", TODAY, List.of(12))), false));
        when(core.nearbyStores(5, 0, 2)).thenReturn(new CandidatePage<>(List.of(), true));
        when(core.nearbyStores(5, 1, 2)).thenReturn(new CandidatePage<>(List.of(candidate(12)), false));
        Notification saved = new Notification(12, NotificationType.NEW_NEARBY_STORE,
                "4", "nearby-store:cycle:1", "Nova loja", "Loja D");
        when(suggestions.generate(candidate(12))).thenReturn(Optional.of(saved));

        assertThat(service.generate(TODAY)).isEqualTo(1);

        verify(core).newStores(TODAY, TODAY, 5, 1, 2);
        verify(core).nearbyStores(5, 1, 2);
        verify(creation).dispatchCreated(saved);
    }

    @Test
    void rescansTheLastCompletedDayToCaptureStoresRegisteredAfterThePreviousJob() {
        when(pending.lastScanDate()).thenReturn(Optional.of(TODAY.minusDays(1)));

        service.generate(TODAY);

        verify(core).newStores(TODAY.minusDays(1), TODAY, 5, 0, 2);
    }

    @Test
    void splitsRecoveryIntoWindowsAcceptedByTheCoreInsteadOfLosingAnOutage() {
        LocalDate first = TODAY.minusDays(70);
        when(pending.lastScanDate()).thenReturn(Optional.of(first));

        service.generate(TODAY);

        verify(core).newStores(first, first.plusDays(30), 5, 0, 2);
        verify(core).newStores(first.plusDays(31), first.plusDays(61), 5, 0, 2);
        verify(core).newStores(first.plusDays(62), TODAY, 5, 0, 2);
        verify(pending).markScanned(first.plusDays(30));
        verify(pending).markScanned(first.plusDays(61));
        verify(pending).markScanned(TODAY);
    }

    @Test
    void doesNotAdvanceTheDiscoveryCheckpointWhenTheLastPageFails() {
        when(core.newStores(TODAY, TODAY, 5, 0, 2)).thenReturn(new CandidatePage<>(List.of(), true));
        when(core.newStores(TODAY, TODAY, 5, 1, 2)).thenThrow(new CoreIntegrationException("unavailable"));

        assertThatThrownBy(() -> service.generate(TODAY)).isInstanceOf(CoreIntegrationException.class);

        verify(pending, never()).markScanned(any());
        verify(core, never()).nearbyStores(anyDouble(), anyInt(), anyInt());
    }

    @Test
    void continuesOtherProfilesAfterAFailureButDoesNotReportCompleteSuccess() {
        when(core.nearbyStores(5, 0, 2)).thenReturn(
                new CandidatePage<>(List.of(candidate(12), candidate(13)), false));
        when(suggestions.generate(candidate(12))).thenThrow(new IllegalStateException("state unavailable"));

        assertThatThrownBy(() -> service.generate(TODAY)).isInstanceOf(IllegalStateException.class);

        verify(suggestions).generate(candidate(13));
        verifyNoInteractions(creation);
    }

    @Test
    void doesNotPublishOrDispatchWhenNoSuggestionWasCommitted() {
        when(core.nearbyStores(5, 0, 2)).thenReturn(new CandidatePage<>(List.of(candidate(12)), false));
        when(suggestions.generate(candidate(12))).thenThrow(new IllegalStateException("transaction failed"));

        assertThatThrownBy(() -> service.generate(TODAY)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(creation);
    }

    @Test
    void disablesDiscoveryAndGenerationTogether() {
        assertThat(service(false).generate(TODAY)).isZero();

        verifyNoInteractions(core, pending, suggestions, creation);
    }

    private NearbyStoreNotificationGenerationService service(boolean enabled) {
        NotificationProperties notifications = new NotificationProperties(
                List.of(3, 1, 0), List.of(7, 3, 1, 0), 2, ZoneId.of("America/Sao_Paulo"));
        return new NearbyStoreNotificationGenerationService(core, suggestions, pending, creation,
                new NearbyStoreProperties(enabled, 5, 3), notifications,
                Clock.fixed(NOW, notifications.zoneId()));
    }

    private NearbyStoreCandidate candidate(int profileId) {
        return new NearbyStoreCandidate(profileId, List.of(new NearbyStoreOption(4, "Loja D", 0.5)));
    }
}
