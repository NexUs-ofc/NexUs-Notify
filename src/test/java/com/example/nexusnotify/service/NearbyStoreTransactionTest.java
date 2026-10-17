package com.example.nexusnotify.service;

import com.example.nexusnotify.config.MongoTransactionConfiguration;
import com.example.nexusnotify.config.properties.NearbyStoreProperties;
import com.example.nexusnotify.domain.NearbyStoreRotation;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.infra.core.NearbyStoreCandidate;
import com.example.nexusnotify.infra.core.NearbyStoreOption;
import com.example.nexusnotify.infra.repository.NearbyStoreRotationRepository;
import com.example.nexusnotify.infra.repository.NewStoreSuggestionRepository;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import com.example.nexusnotify.service.rule.NearbyStoreRule;
import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NearbyStoreTransactionTest {

    private static final Instant NOW = Instant.parse("2026-10-17T14:00:00Z");
    private final NearbyStoreRotationRepository rotations = mock(NearbyStoreRotationRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NewStoreSuggestionRepository pending = mock(NewStoreSuggestionRepository.class);
    private final ClientSession session = mock(ClientSession.class);

    @Test
    void commitsTheSuggestionOnlyAfterAllThreeWritesSucceed() {
        try (var context = context()) {
            when(notifications.insert(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(context.getBean(NearbyStoreSuggestionService.class).generate(candidate())).isPresent();

            var order = inOrder(rotations, notifications, pending, session);
            order.verify(rotations).save(any(NearbyStoreRotation.class));
            order.verify(notifications).insert(any(Notification.class));
            order.verify(pending).markPresented(12, 4, NOW);
            order.verify(session).commitTransaction();
            verify(session, never()).abortTransaction();
        }
    }

    @Test
    void abortsTheMongoTransactionWhenSavingTheNotificationFails() {
        try (var context = context()) {
            when(notifications.insert(any(Notification.class))).thenThrow(new IllegalStateException("write failed"));

            assertThatThrownBy(() -> context.getBean(NearbyStoreSuggestionService.class).generate(candidate()))
                    .isInstanceOf(IllegalStateException.class).hasMessage("write failed");

            verify(session).abortTransaction();
            verify(session, never()).commitTransaction();
            verify(pending, never()).markPresented(12, 4, NOW);
        }
    }

    private AnnotationConfigApplicationContext context() {
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        when(factory.getSession(any(ClientSessionOptions.class))).thenReturn(session);
        when(rotations.findById(12)).thenReturn(Optional.empty());
        when(pending.findPendingStoreIds(12)).thenReturn(Set.of(4));
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(MongoDatabaseFactory.class, () -> factory);
        context.register(MongoTransactionConfiguration.class);
        context.registerBean(NearbyStoreProperties.class, () -> new NearbyStoreProperties(true, 5, 3));
        context.registerBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));
        context.registerBean(NearbyStoreRotationRepository.class, () -> rotations);
        context.registerBean(NotificationRepository.class, () -> notifications);
        context.registerBean(NewStoreSuggestionRepository.class, () -> pending);
        context.registerBean(NearbyStoreRule.class,
                () -> new NearbyStoreRule(context.getBean(NearbyStoreProperties.class)));
        context.register(NearbyStoreSuggestionService.class);
        context.refresh();
        return context;
    }

    private NearbyStoreCandidate candidate() {
        return new NearbyStoreCandidate(12, List.of(new NearbyStoreOption(4, "Loja D", 0.5)));
    }
}
