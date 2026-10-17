package com.example.nexusnotify.service;

import com.example.nexusnotify.config.properties.NotificationProperties;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.core.CandidatePage;
import com.example.nexusnotify.infra.core.CoreClient;
import com.example.nexusnotify.infra.core.CoreIntegrationException;
import com.example.nexusnotify.infra.core.EventCandidate;
import com.example.nexusnotify.infra.core.ProductCandidate;
import com.example.nexusnotify.service.rule.ProductExpiryRule;
import com.example.nexusnotify.service.rule.UpcomingEventRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private CoreClient coreClient;
    private NotificationWriter writer;
    private PushDispatchService pushDispatch;
    private NearbyStoreNotificationGenerationService stores;
    private NotificationGenerationService service;
    private SimpleMeterRegistry metrics;
    private MockClock metricsClock;

    @BeforeEach
    void setUp() {
        coreClient = mock(CoreClient.class);
        writer = mock(NotificationWriter.class);
        pushDispatch = mock(PushDispatchService.class);
        stores = mock(NearbyStoreNotificationGenerationService.class);
        metricsClock = new MockClock();
        metrics = new SimpleMeterRegistry(SimpleConfig.DEFAULT, metricsClock);
        NotificationProperties properties = new NotificationProperties(
                List.of(3, 1, 0), List.of(7, 3, 1, 0), 2, ZoneId.of("America/Sao_Paulo"));
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T02:30:00Z"), properties.zoneId());
        NotificationCreationService creation = new NotificationCreationService(writer, pushDispatch, metrics);
        service = new NotificationGenerationService(
                new ProductNotificationGenerationService(coreClient, creation, new ProductExpiryRule(properties),
                        properties),
                new EventNotificationGenerationService(coreClient, creation, new UpcomingEventRule(properties),
                        properties),
                stores, clock, metrics);

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
        verify(pushDispatch, times(5)).send(any());
        assertEquals(2, count("notify.notifications.created", NotificationType.PRODUCT_EXPIRING));
        assertEquals(3, count("notify.notifications.created", NotificationType.UPCOMING_EVENT));
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "success").timer().count());
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
        verify(pushDispatch).send(any());
        assertEquals(1, count("notify.notifications.created", NotificationType.PRODUCT_EXPIRING));
        assertEquals(1, count("notify.notifications.duplicates", NotificationType.PRODUCT_EXPIRING));
        assertEquals(2, metrics.get("notify.generation.duration").tag("result", "success").timer().count());
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
        assertEquals(1, count("notify.notifications.created", NotificationType.UPCOMING_EVENT));
        assertEquals(1, count("notify.notifications.duplicates", NotificationType.UPCOMING_EVENT));
    }

    @Test
    void doesNotCountAnExistingProductReminder() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));
        when(writer.saveIfAbsent(any())).thenReturn(false);

        assertEquals(0, service.generate());
        verify(writer).saveIfAbsent(any());
        verifyNoInteractions(pushDispatch);
        assertEquals(0, count("notify.notifications.created", NotificationType.PRODUCT_EXPIRING));
        assertEquals(1, count("notify.notifications.duplicates", NotificationType.PRODUCT_EXPIRING));
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
        assertEquals(1, count("notify.notifications.created", NotificationType.PRODUCT_EXPIRING));
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "failure").timer().count());
        assertEquals(1, service.generate());
        verify(writer, times(3)).saveIfAbsent(any());
        assertEquals(1, count("notify.notifications.created", NotificationType.PRODUCT_EXPIRING));
        assertEquals(1, count("notify.notifications.created", NotificationType.UPCOMING_EVENT));
        assertEquals(1, count("notify.notifications.duplicates", NotificationType.PRODUCT_EXPIRING));
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "success").timer().count());
    }

    @Test
    void propagatesWriterFailures() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));
        IllegalStateException exception = new IllegalStateException("unavailable");
        when(writer.saveIfAbsent(any())).thenThrow(exception);

        assertSame(exception, assertThrows(IllegalStateException.class, service::generate));
        verifyNoInteractions(pushDispatch);
        assertEquals(0, count("notify.notifications.created", NotificationType.PRODUCT_EXPIRING));
        assertEquals(0, count("notify.notifications.duplicates", NotificationType.PRODUCT_EXPIRING));
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "failure").timer().count());
    }

    @Test
    void propagatesCoreFailures() {
        CoreIntegrationException exception = new CoreIntegrationException("unavailable");
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2)).thenThrow(exception);

        assertSame(exception, assertThrows(CoreIntegrationException.class, service::generate));
        verifyNoInteractions(writer);
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "failure").timer().count());
        assertEquals(0, metrics.get("notify.generation.duration").tag("result", "success").timer().count());
    }

    @Test
    void returnsZeroWhenBothQueriesAreEmpty() {
        assertEquals(0, service.generate());
        verifyNoInteractions(writer);
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "success").timer().count());
        assertEquals(0, metrics.get("notify.generation.duration").tag("result", "failure").timer().count());
    }

    @Test
    void generatesEventsEvenWhenTheProductQueryFails() {
        CoreIntegrationException failure = new CoreIntegrationException("products unavailable");
        when(coreClient.products(any(), any(), anyInt(), anyInt())).thenThrow(failure);
        when(coreClient.events(TODAY, TODAY.plusDays(7), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(event("event-1", 1)), false));

        assertSame(failure, assertThrows(CoreIntegrationException.class, service::generate));

        verify(pushDispatch).send(argThat(notification -> notification.getType() == NotificationType.UPCOMING_EVENT));
        assertEquals(1, count("notify.notifications.created", NotificationType.UPCOMING_EVENT));
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "failure").timer().count());
    }

    @Test
    void generatesEventsEvenWhenSavingAProductFails() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));
        when(coreClient.events(TODAY, TODAY.plusDays(7), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(event("event-1", 1)), false));
        when(writer.saveIfAbsent(argThat(notification -> notification.getType() == NotificationType.PRODUCT_EXPIRING)))
                .thenThrow(new IllegalStateException("product failure"));

        assertThrows(IllegalStateException.class, service::generate);

        verify(pushDispatch).send(argThat(notification -> notification.getType() == NotificationType.UPCOMING_EVENT));
        assertEquals(1, count("notify.notifications.created", NotificationType.UPCOMING_EVENT));
    }

    @Test
    void runsStoresAfterBothOtherFlowsFailAndPreservesBothFailures() {
        CoreIntegrationException productFailure = new CoreIntegrationException("products unavailable");
        CoreIntegrationException eventFailure = new CoreIntegrationException("events unavailable");
        when(coreClient.products(any(), any(), anyInt(), anyInt())).thenThrow(productFailure);
        when(coreClient.events(any(), any(), anyInt(), anyInt())).thenThrow(eventFailure);
        when(stores.generate(TODAY)).thenReturn(1);

        assertSame(productFailure, assertThrows(CoreIntegrationException.class, service::generate));

        assertThat(productFailure.getSuppressed()).containsExactly(eventFailure);
        verify(stores).generate(TODAY);
        assertEquals(1, metrics.get("notify.generation.flow.duration")
                .tags("flow", "stores", "result", "success").timer().count());
    }

    @Test
    void preservesProductAndEventNotificationsWhenTheStoresFlowFails() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));
        when(coreClient.events(TODAY, TODAY.plusDays(7), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(event("event-1", 1)), false));
        when(stores.generate(TODAY)).thenThrow(new IllegalStateException("stores unavailable"));

        assertThrows(IllegalStateException.class, service::generate);

        verify(pushDispatch, times(2)).send(any());
        assertEquals(1, count("notify.notifications.created", NotificationType.PRODUCT_EXPIRING));
        assertEquals(1, count("notify.notifications.created", NotificationType.UPCOMING_EVENT));
        assertEquals(1, metrics.get("notify.generation.duration").tag("result", "failure").timer().count());
    }

    @Test
    void measuresTimeSpentQueryingAndSavingWithoutTagsForUserData() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2)).thenAnswer(invocation -> {
            metricsClock.add(Duration.ofSeconds(2));
            return new CandidatePage<>(List.of(product(45, 3)), false);
        });
        when(writer.saveIfAbsent(any())).thenAnswer(invocation -> {
            metricsClock.add(Duration.ofSeconds(1));
            return true;
        });

        assertEquals(1, service.generate());

        assertEquals(3, metrics.get("notify.generation.duration").tag("result", "success")
                .timer().totalTime(TimeUnit.SECONDS));
        assertThat(metrics.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .allSatisfy(tag -> assertThat(tag.getKey()).isIn("type", "result", "flow")));
    }

    @Test
    void recordsTheDurationOfAFailedExecution() {
        when(coreClient.products(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            metricsClock.add(Duration.ofSeconds(4));
            throw new CoreIntegrationException("unavailable");
        });

        assertThrows(CoreIntegrationException.class, service::generate);

        assertEquals(4, metrics.get("notify.generation.duration").tag("result", "failure")
                .timer().totalTime(TimeUnit.SECONDS));
        assertEquals(0, metrics.get("notify.generation.duration").tag("result", "success").timer().count());
    }

    @Test
    void dispatchesOnlyAfterTheNotificationHasBeenPersisted() {
        when(coreClient.products(TODAY, TODAY.plusDays(3), 0, 2))
                .thenReturn(new CandidatePage<>(List.of(product(45, 3)), false));

        assertEquals(1, service.generate());

        InOrder order = inOrder(writer, pushDispatch);
        order.verify(writer).saveIfAbsent(any());
        order.verify(pushDispatch).send(argThat(notification -> notification.getProfileId() == 12
                && notification.getType() == NotificationType.PRODUCT_EXPIRING));
        order.verifyNoMoreInteractions();
    }

    private double count(String name, NotificationType type) {
        Counter counter = metrics.find(name).tag("type", type.name()).counter();
        return counter == null ? 0 : counter.count();
    }

    private ProductCandidate product(int id, int daysBefore) {
        return new ProductCandidate(12, id, "Leite", TODAY.plusDays(daysBefore));
    }

    private EventCandidate event(String id, int daysBefore) {
        return new EventCandidate(12, id, "Jantar", TODAY.plusDays(daysBefore).atTime(19, 30));
    }
}
