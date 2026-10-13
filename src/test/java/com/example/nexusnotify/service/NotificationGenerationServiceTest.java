package com.example.nexusnotify.service;

import com.example.nexusnotify.config.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.CoreIntegrationException;
import com.example.nexusnotify.infra.core.EventCandidate;
import com.example.nexusnotify.infra.core.ProductCandidate;
import com.example.nexusnotify.service.rule.ProductExpiryRule;
import com.example.nexusnotify.service.rule.UpcomingEventRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private CoreClient coreClient;
    private NotificationWriter writer;
    private NotificationGenerationService service;

    @BeforeEach
    void setUp() {
        coreClient = mock(CoreClient.class);
        writer = mock(NotificationWriter.class);
        NotificationProperties properties = new NotificationProperties(
                List.of(3, 1, 0), List.of(7, 3, 1, 0), 2, ZoneId.of("America/Sao_Paulo"));
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T02:30:00Z"), properties.zoneId());
        service = new NotificationGenerationService(coreClient, writer,
                new ProductExpiryRule(properties), new UpcomingEventRule(properties), properties, clock);

        when(coreClient.products(any(), any(), anyInt(), anyInt())).thenReturn(new CandidatePage<>(List.of(), false));
        when(coreClient.events(any(), any(), anyInt(), anyInt())).thenReturn(new CandidatePage<>(List.of(), false));
        when(writer.saveIfAbsent(any())).thenReturn(true);
    }

    @Test
    void readsEveryPageAndAppliesRulesUsingTheConfiguredTimeZone() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3), product(46, 2)), true));
        when(coreClient.products(TODAY, TODAY.plusDays(3), 1, 2))
                .thenReturn(new CandidatePage<>(List.of(product(47, 1)), false));
        when(coreClient.events(TODAY, TODAY.plusDays(7), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(event("event-1", 7), event("event-2", 5)), true));
        when(coreClient.events(TODAY, TODAY.plusDays(7), 1, 2))
                .thenReturn(new CandidatePage<>(List.of(event("event-3", 3), event("event-4", 1)), false));

        assertEquals(5, service.generate());

        verify(coreClient).products(TODAY, TODAY.plusDays(3), 1, 2);
        verify(coreClient).events(TODAY, TODAY.plusDays(7), 1, 2);
        verify(writer, times(5)).saveIfAbsent(any());
    }

    @Test
    void repeatingGenerationDoesNotInsertTheSameReminderAgain() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));

        Set<String> saved = new HashSet<>();
        when(writer.saveIfAbsent(any())).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            return saved.add(notification.getAlertKey());
        });

        assertEquals(1, service.generate());
        assertEquals(0, service.generate());

        verify(writer, times(2)).saveIfAbsent(any());
    }

    @Test
    void queriesTodaysEventsAndDoesNotDuplicateTheEventDayReminder() {
        when(coreClient.events(TODAY, TODAY.plusDays(7), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(event("event-1", 0)), false));
        when(writer.saveIfAbsent(any())).thenReturn(true, false);

        assertEquals(1, service.generate());
        assertEquals(0, service.generate());

        verify(coreClient, times(2)).events(TODAY, TODAY.plusDays(7), 0, 2);
        verify(writer, times(2)).saveIfAbsent(argThat(notification ->
                "event:2026-10-12T19:30:days:0".equals(notification.getAlertKey())));
    }

    @Test
    void doesNotCountAnExistingProductReminder() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));
        when(writer.saveIfAbsent(any())).thenReturn(false);

        assertEquals(0, service.generate());
        verify(writer).saveIfAbsent(any());
    }

    @Test
    void canRepeatGenerationAfterAnEventQueryFails() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));
        when(coreClient.events(TODAY, TODAY.plusDays(7), 0, 2))
                .thenThrow(new CoreIntegrationException("unavailable"))
                .thenReturn(new CandidatePage<>(List.of(event("event-1", 1)), false));
        Set<String> saved = new HashSet<>();
        when(writer.saveIfAbsent(any())).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            return saved.add(notification.getAlertKey());
        });

        assertThrows(CoreIntegrationException.class, service::generate);
        assertEquals(1, service.generate());
        verify(writer, times(3)).saveIfAbsent(any());
    }

    @Test
    void propagatesWriterFailures() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));
        IllegalStateException exception = new IllegalStateException("unavailable");
        when(writer.saveIfAbsent(any())).thenThrow(exception);

        assertSame(exception, assertThrows(IllegalStateException.class, service::generate));
    }

    @Test
    void propagatesCoreFailures() {
        CoreIntegrationException exception = new CoreIntegrationException("unavailable");
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2)).thenThrow(exception);

        assertSame(exception, assertThrows(CoreIntegrationException.class, service::generate));
        verifyNoInteractions(writer);
    }

    @Test
    void returnsZeroWhenBothQueriesAreEmpty() {
        assertEquals(0, service.generate());
        verifyNoInteractions(writer);
    }

    private ProductCandidate product(int id, int daysBefore) {
        return new ProductCandidate(12, id, "Leite", TODAY.plusDays(daysBefore));
    }

    private EventCandidate event(String id, int daysBefore) {
        return new EventCandidate(12, id, "Jantar", TODAY.plusDays(daysBefore).atTime(19, 30));
    }
}
