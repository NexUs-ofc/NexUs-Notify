package com.example.nexusnotify.infra.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void updatesTheReadTimeOnlyForAnUnreadNotificationOfTheProfile() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase("nexus-notify-test")).thenReturn(database);
        when(database.getCodecRegistry()).thenReturn(MongoClientSettings.getDefaultCodecRegistry());
        when(database.getCollection("notifications", Document.class)).thenReturn(collection);
        when(collection.updateMany(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        MongoTemplate template = new MongoTemplate(client, "nexus-notify-test");
        NotificationRepository repository = new MongoRepositoryFactory(template)
                .getRepository(NotificationRepository.class);
        ObjectId id = new ObjectId();
        Instant when = Instant.parse("2026-10-13T14:00:00Z");

        assertThat(repository.markAsRead(id, 12, when)).isEqualTo(1);

        ArgumentCaptor<Bson> query = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateMany(query.capture(), update.capture(), any(UpdateOptions.class));
        BsonDocument filter = query.getValue().toBsonDocument(Document.class,
                MongoClientSettings.getDefaultCodecRegistry());
        BsonDocument changes = update.getValue().toBsonDocument(Document.class,
                MongoClientSettings.getDefaultCodecRegistry());

        assertThat(filter.keySet()).containsExactlyInAnyOrder("_id", "profile_id", "read_at");
        assertThat(filter.getObjectId("_id").getValue()).isEqualTo(id);
        assertThat(filter.getInt32("profile_id").getValue()).isEqualTo(12);
        assertThat(filter.get("read_at").isNull()).isTrue();
        assertThat(changes.keySet()).containsExactly("$set");
        assertThat(changes.getDocument("$set").keySet()).containsExactly("read_at");
        assertThat(changes.getDocument("$set").getDateTime("read_at").getValue()).isEqualTo(when.toEpochMilli());
    }
}
