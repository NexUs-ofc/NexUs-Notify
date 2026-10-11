package com.example.nexusnotify.domain;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMappingTests {

    private MongoMappingContext context;
    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> {});
        context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(Notification.class));
        context.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @Test
    void readsDocumentUsingDataloadFieldNames() {
        ObjectId id = new ObjectId();
        Instant createdAt = Instant.parse("2026-10-11T12:00:00Z");
        Document document = new Document("_id", id)
            .append("profile_id", 1)
            .append("type", "UPCOMING_EVENT")
            .append("source_id", new ObjectId().toHexString())
            .append("alert_key", "event:one-day")
            .append("title", "Evento amanhã")
            .append("message", "Confira os ingredientes do evento.")
            .append("created_at", Date.from(createdAt))
            .append("read_at", null);

        Notification notification = converter.read(Notification.class, document);

        assertThat(notification.getId()).isEqualTo(id);
        assertThat(notification.getProfileId()).isEqualTo(1);
        assertThat(notification.getType()).isEqualTo(NotificationType.UPCOMING_EVENT);
        assertThat(notification.getSourceId()).isEqualTo(document.getString("source_id"));
        assertThat(notification.getAlertKey()).isEqualTo("event:one-day");
        assertThat(notification.getTitle()).isEqualTo("Evento amanhã");
        assertThat(notification.getMessage()).isEqualTo("Confira os ingredientes do evento.");
        assertThat(notification.getCreatedAt()).isEqualTo(createdAt);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void writesFieldsAndPreservesFirstReadTime() {
        Notification notification = new Notification(1, NotificationType.PRODUCT_EXPIRING,
            "42", "expiry:one-day", "Produto vencendo", "Confira a validade do produto.");
        Instant readAt = notification.getCreatedAt().plusSeconds(60);
        notification.markAsRead(readAt);
        notification.markAsRead(readAt.plusSeconds(60));
        Document document = new Document();

        converter.write(notification, document);

        assertThat(document).containsEntry("profile_id", 1)
            .containsEntry("type", "PRODUCT_EXPIRING")
            .containsEntry("source_id", "42")
            .containsEntry("alert_key", "expiry:one-day")
            .containsEntry("read_at", Date.from(readAt));
        assertThat(document).containsKeys("title", "message", "created_at")
            .doesNotContainKeys("priority", "is_read", "account_id", "description", "profileId", "readAt");
        Notification restored = converter.read(Notification.class, document);
        assertThat(restored.isRead()).isTrue();
        assertThat(restored.getReadAt()).isEqualTo(Instant.ofEpochMilli(readAt.toEpochMilli()));
    }

    @Test
    void definesUniqueAlertAndProfileDateIndexes() {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(context);

        assertThat(resolver.resolveIndexFor(Notification.class)).anySatisfy(index -> {
            assertThat(index.getIndexKeys()).isEqualTo(Document.parse(
                "{'profile_id': 1, 'type': 1, 'source_id': 1, 'alert_key': 1}"));
            assertThat(index.getIndexOptions()).containsEntry("unique", true);
        }).anySatisfy(index -> assertThat(index.getIndexKeys())
            .isEqualTo(Document.parse("{'profile_id': 1, 'created_at': -1}")));
    }
}
