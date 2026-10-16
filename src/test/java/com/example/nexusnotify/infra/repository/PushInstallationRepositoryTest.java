package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.PushInstallation;
import com.example.nexusnotify.domain.PushInstallationStatus;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PushInstallationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final String FID = "fid-installation-one";

    private MongoCollection<Document> collection;
    private PushInstallationRepository repository;
    private ObjectId id;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("nexus-notify-test")).thenReturn(database);
        when(database.getCodecRegistry()).thenReturn(MongoClientSettings.getDefaultCodecRegistry());
        when(database.getCollection("push_installations", Document.class)).thenReturn(collection);
        repository = new PushInstallationRepository(new MongoTemplate(client, "nexus-notify-test"));
        id = new ObjectId();
    }

    @Test
    void atomicallyUpsertsByFidAndResetsTheBindingWithoutChangingTheOriginalCreationDate() {
        Instant created = NOW.minusSeconds(3600);
        when(collection.findOneAndUpdate(any(Bson.class), any(Bson.class), any(FindOneAndUpdateOptions.class)))
                .thenReturn(activeDocument(13, created));

        PushInstallation result = repository.register(13, FID, NOW);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.profileId()).isEqualTo(13);
        assertThat(result.createdAt()).isEqualTo(created);
        assertThat(result.lastSeenAt()).isEqualTo(NOW);
        assertThat(result.status()).isEqualTo(PushInstallationStatus.ACTIVE);
        assertThat(result.disabledAt()).isNull();
        assertThat(result.disabledReason()).isNull();

        ArgumentCaptor<Bson> query = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<FindOneAndUpdateOptions> options = ArgumentCaptor.forClass(FindOneAndUpdateOptions.class);
        verify(collection).findOneAndUpdate(query.capture(), update.capture(), options.capture());
        assertThat(bson(query.getValue())).isEqualTo(BsonDocument.parse("{'installation_id': '" + FID + "'}"));
        BsonDocument changes = bson(update.getValue());
        BsonDocument set = changes.getDocument("$set");
        assertThat(set.getInt32("profile_id").getValue()).isEqualTo(13);
        assertThat(set.getString("status").getValue()).isEqualTo("ACTIVE");
        assertThat(set.getDateTime("updated_at").getValue()).isEqualTo(NOW.toEpochMilli());
        assertThat(set.getDateTime("last_seen_at").getValue()).isEqualTo(NOW.toEpochMilli());
        assertThat(set.get("disabled_at").isNull()).isTrue();
        assertThat(set.get("disabled_reason").isNull()).isTrue();
        assertThat(set.keySet()).doesNotContain("created_at", "installation_id", "platform", "_id");
        assertThat(changes.getDocument("$setOnInsert").getDateTime("created_at").getValue())
                .isEqualTo(NOW.toEpochMilli());
        assertThat(options.getValue().isUpsert()).isTrue();
        assertThat(options.getValue().getReturnDocument()).isEqualTo(ReturnDocument.AFTER);
        verifyNoMoreInteractions(collection);
    }

    @Test
    void retriesAnUpsertCollisionAsAnUpdateOfTheSameFid() {
        when(collection.findOneAndUpdate(any(Bson.class), any(Bson.class), any(FindOneAndUpdateOptions.class)))
                .thenThrow(duplicate()).thenReturn(activeDocument(12, NOW.minusSeconds(60)));

        assertThat(repository.register(12, FID, NOW).id()).isEqualTo(id);

        ArgumentCaptor<Bson> query = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<FindOneAndUpdateOptions> options = ArgumentCaptor.forClass(FindOneAndUpdateOptions.class);
        verify(collection, times(2)).findOneAndUpdate(query.capture(), any(Bson.class), options.capture());
        assertThat(bson(query.getAllValues().get(0))).isEqualTo(bson(query.getAllValues().get(1)));
        assertThat(options.getAllValues().get(0).isUpsert()).isTrue();
        assertThat(options.getAllValues().get(1).isUpsert()).isFalse();
        assertThat(options.getAllValues().get(1).getReturnDocument()).isEqualTo(ReturnDocument.AFTER);
        verifyNoMoreInteractions(collection);
    }

    @Test
    void propagatesADuplicateIfThereIsNoExistingInstallationToUpdate() {
        when(collection.findOneAndUpdate(any(Bson.class), any(Bson.class), any(FindOneAndUpdateOptions.class)))
                .thenThrow(duplicate()).thenReturn(null);

        assertThatThrownBy(() -> repository.register(12, FID, NOW)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void doesNotHideAFailureDuringTheCollisionRetry() {
        when(collection.findOneAndUpdate(any(Bson.class), any(Bson.class), any(FindOneAndUpdateOptions.class)))
                .thenThrow(duplicate()).thenThrow(duplicate());

        assertThatThrownBy(() -> repository.register(12, FID, NOW)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsAnUnexpectedEmptyUpsertResult() {
        assertThatThrownBy(() -> repository.register(12, FID, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Nao foi possivel cadastrar a instalacao de push");
    }

    @Test
    void revokesOnlyTheCurrentProfilesBindingAndPreservesCreationAndLastSeenTimes() {
        when(collection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        repository.revoke(id, 12, NOW);

        ArgumentCaptor<Bson> query = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<UpdateOptions> options = ArgumentCaptor.forClass(UpdateOptions.class);
        verify(collection).updateOne(query.capture(), update.capture(), options.capture());
        BsonDocument filter = bson(query.getValue());
        assertThat(filter.keySet()).containsExactlyInAnyOrder("_id", "profile_id");
        assertThat(filter.getObjectId("_id").getValue()).isEqualTo(id);
        assertThat(filter.getInt32("profile_id").getValue()).isEqualTo(12);
        BsonDocument set = bson(update.getValue()).getDocument("$set");
        assertThat(set.get("profile_id").isNull()).isTrue();
        assertThat(set.getString("status").getValue()).isEqualTo("REVOKED");
        assertThat(set.getString("disabled_reason").getValue()).isEqualTo("LOGOUT");
        assertThat(set.getDateTime("disabled_at").getValue()).isEqualTo(NOW.toEpochMilli());
        assertThat(set.getDateTime("updated_at").getValue()).isEqualTo(NOW.toEpochMilli());
        assertThat(set.keySet()).doesNotContain("created_at", "last_seen_at", "installation_id");
        assertThat(options.getValue().isUpsert()).isFalse();
        verifyNoMoreInteractions(collection);
    }

    @Test
    void acceptsAMissingBindingWithoutCreatingOrDeletingDocuments() {
        when(collection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        repository.revoke(id, 13, NOW);

        verify(collection).updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class));
        verifyNoMoreInteractions(collection);
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectsOnlyActiveInstallationsOfTheRecipientUsingMappedMongoFields() {
        FindIterable<Document> found = mock(FindIterable.class, RETURNS_SELF);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(collection.find(any(Bson.class), eq(Document.class))).thenReturn(found);
        when(found.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(activeDocument(12, NOW.minusSeconds(120)));

        assertThat(repository.findActiveByProfileId(12)).singleElement().satisfies(installation -> {
            assertThat(installation.profileId()).isEqualTo(12);
            assertThat(installation.installationId()).isEqualTo(FID);
            assertThat(installation.status()).isEqualTo(PushInstallationStatus.ACTIVE);
        });

        ArgumentCaptor<Bson> query = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(query.capture(), eq(Document.class));
        assertThat(bson(query.getValue())).isEqualTo(BsonDocument.parse("{'profile_id': 12, 'status': 'ACTIVE'}"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void rechecksTheExactActiveBindingBeforeSending(long matches) {
        when(collection.getNamespace()).thenReturn(new MongoNamespace("nexus-notify-test", "push_installations"));
        when(collection.countDocuments(any(Bson.class), any(CountOptions.class))).thenReturn(matches);

        assertThat(repository.isCurrentActive(snapshot())).isEqualTo(matches == 1);

        ArgumentCaptor<Bson> query = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<CountOptions> options = ArgumentCaptor.forClass(CountOptions.class);
        verify(collection).countDocuments(query.capture(), options.capture());
        assertSnapshotFilter(bson(query.getValue()));
        assertThat(options.getValue().getLimit()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void invalidatesOnlyTheSnapshotAndReportsWhetherItWasStillCurrent(long modified) {
        when(collection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(UpdateResult.acknowledged(modified, modified, null));

        assertThat(repository.markInvalid(snapshot(), NOW)).isEqualTo(modified == 1);

        ArgumentCaptor<Bson> query = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<UpdateOptions> options = ArgumentCaptor.forClass(UpdateOptions.class);
        verify(collection).updateOne(query.capture(), update.capture(), options.capture());
        assertSnapshotFilter(bson(query.getValue()));
        BsonDocument set = bson(update.getValue()).getDocument("$set");
        assertThat(set.keySet()).containsExactlyInAnyOrder("status", "updated_at", "disabled_at", "disabled_reason");
        assertThat(set.getString("status").getValue()).isEqualTo("INVALID");
        assertThat(set.getString("disabled_reason").getValue()).isEqualTo("FCM_UNREGISTERED");
        assertThat(set.getDateTime("disabled_at").getValue()).isEqualTo(NOW.toEpochMilli());
        assertThat(set.getDateTime("updated_at").getValue()).isEqualTo(NOW.toEpochMilli());
        assertThat(options.getValue().isUpsert()).isFalse();
    }

    private PushInstallation snapshot() {
        return new PushInstallation(
                id,
                12,
                FID,
                PushInstallationStatus.ACTIVE,
                NOW.minusSeconds(120),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                null,
                null
        );
    }

    private void assertSnapshotFilter(BsonDocument filter) {
        assertThat(filter.keySet()).containsExactlyInAnyOrder(
                "_id", "profile_id", "installation_id", "status", "updated_at");
        assertThat(filter.getObjectId("_id").getValue()).isEqualTo(id);
        assertThat(filter.getInt32("profile_id").getValue()).isEqualTo(12);
        assertThat(filter.getString("installation_id").getValue()).isEqualTo(FID);
        assertThat(filter.getString("status").getValue()).isEqualTo("ACTIVE");
        assertThat(filter.getDateTime("updated_at").getValue()).isEqualTo(NOW.minusSeconds(60).toEpochMilli());
    }

    private Document activeDocument(int profileId, Instant createdAt) {
        return new Document("_id", id)
                .append("profile_id", profileId)
                .append("installation_id", FID)
                .append("status", "ACTIVE")
                .append("created_at", Date.from(createdAt))
                .append("updated_at", Date.from(NOW))
                .append("last_seen_at", Date.from(NOW))
                .append("disabled_at", null)
                .append("disabled_reason", null);
    }

    private MongoWriteException duplicate() {
        return new MongoWriteException(new WriteError(11000, "duplicate key", new BsonDocument()), new ServerAddress());
    }

    private BsonDocument bson(Bson value) {
        return value.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    }
}
