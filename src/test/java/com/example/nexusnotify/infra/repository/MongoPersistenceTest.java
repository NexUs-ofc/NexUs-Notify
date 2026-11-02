package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.service.NotificationService;
import com.example.nexusnotify.service.NotificationWriter;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.Sort.Direction.ASC;

@EnabledIfSystemProperty(named = "mongodb.test.uri", matches = ".+")
class MongoPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static MongoClient client;
    private static MongoTemplate template;
    private static NotificationRepository notifications;
    private static PushInstallationRepository installations;
    private static NewStoreSuggestionRepository suggestions;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(System.getProperty("mongodb.test.uri"));
        template = new MongoTemplate(client, "notify_test_" + UUID.randomUUID().toString().replace("-", ""));
        notifications = new MongoRepositoryFactory(template).getRepository(NotificationRepository.class);
        installations = new PushInstallationRepository(template);
        suggestions = new NewStoreSuggestionRepository(template);
        template.indexOps("notifications").ensureIndex(new Index()
                .on("profile_id", ASC).on("type", ASC).on("source_id", ASC).on("alert_key", ASC).unique());
        template.indexOps("push_installations").ensureIndex(new Index().on("installation_id", ASC).unique());
    }

    @AfterAll
    static void disconnect() {
        try {
            if (template != null) {
                template.getDb().drop();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @BeforeEach
    void clear() {
        for (String collection : template.getCollectionNames()) {
            template.getCollection(collection).deleteMany(new org.bson.Document());
        }
    }

    @Test
    void storesNotificationsOnceAndPaginatesOnlyTheAuthenticatedProfilesHistory() {
        NotificationWriter writer = new NotificationWriter(notifications);
        assertThat(writer.saveIfAbsent(notification(12, "first", NOW))).isTrue();
        assertThat(writer.saveIfAbsent(notification(12, "first", NOW))).isFalse();
        assertThat(writer.saveIfAbsent(notification(12, "second", NOW.plusSeconds(1)))).isTrue();
        assertThat(writer.saveIfAbsent(notification(13, "other-profile", NOW.plusSeconds(2)))).isTrue();

        NotificationService service = new NotificationService(notifications);
        var first = service.list(12, 0, 1);
        var last = service.list(12, 1, 1);

        assertThat(first.content()).hasSize(1);
        assertThat(first.content().get(0).sourceId()).isEqualTo("second");
        assertThat(first.hasNext()).isTrue();
        assertThat(last.content().get(0).sourceId()).isEqualTo("first");
        assertThat(last.hasNext()).isFalse();
        assertThat(notifications.findByProfileIdOrderByCreatedAtDesc(13, PageRequest.of(0, 10))).hasSize(1);
        assertThat(template.getCollection("notifications").find().first()).containsKey("profile_id");
    }

    @Test
    void rebindsAnInstallationWithoutDuplicatingItAndRejectsStaleInvalidation() {
        var first = installations.register(12, "fid-installation-one", NOW);
        var rebound = installations.register(13, "fid-installation-one", NOW.plusSeconds(1));

        assertThat(rebound.id()).isEqualTo(first.id());
        assertThat(rebound.createdAt()).isEqualTo(NOW);
        assertThat(installations.findActiveByProfileId(12)).isEmpty();
        assertThat(installations.findActiveByProfileId(13)).hasSize(1);
        assertThat(installations.markInvalid(first, NOW.plusSeconds(2))).isFalse();
        installations.revoke(first.id(), 12, NOW.plusSeconds(3));
        assertThat(installations.isCurrentActive(rebound)).isTrue();
        assertThat(installations.markInvalid(rebound, NOW.plusSeconds(4))).isTrue();
        assertThat(installations.findActiveByProfileId(13)).isEmpty();
    }

    @Test
    void revokesOnlyTheCurrentOwnersInstallation() {
        var installation = installations.register(12, "fid-installation-one", NOW);

        installations.revoke(installation.id(), 13, NOW.plusSeconds(1));
        assertThat(installations.isCurrentActive(installation)).isTrue();
        installations.revoke(installation.id(), 12, NOW.plusSeconds(2));

        assertThat(installations.findActiveByProfileId(12)).isEmpty();
        assertThat(template.getCollection("push_installations").find().first())
                .containsEntry("status", "REVOKED").containsEntry("disabled_reason", "LOGOUT");
    }

    @Test
    void persistsSuggestionDeduplicationAndNeverMovesTheDiscoveryCursorBackwards() {
        LocalDate date = LocalDate.of(2026, 11, 2);
        suggestions.register(12, 4, date, NOW);
        suggestions.register(12, 4, date, NOW.plusSeconds(1));
        suggestions.register(13, 5, date, NOW);

        assertThat(suggestions.findPendingStoreIds(12)).containsExactly(4);
        suggestions.markPresented(12, 4, NOW.plusSeconds(2));
        assertThat(suggestions.findPendingStoreIds(12)).isEmpty();
        assertThat(suggestions.findPendingStoreIds(13)).containsExactly(5);
        suggestions.markScanned(date);
        suggestions.markScanned(date.minusDays(1));
        assertThat(suggestions.lastScanDate()).contains(date);
    }

    private Notification notification(int profileId, String source, Instant createdAt) {
        return new Notification(profileId, NotificationType.UPCOMING_EVENT, source, "event:today",
                "Evento", "Lembrete", createdAt);
    }
}
