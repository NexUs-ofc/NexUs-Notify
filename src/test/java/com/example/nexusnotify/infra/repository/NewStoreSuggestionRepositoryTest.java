package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.NewStoreDiscoveryState;
import com.example.nexusnotify.domain.NewStoreSuggestion;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewStoreSuggestionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-17T14:00:00Z");
    private final MongoTemplate template = mock(MongoTemplate.class);
    private final NewStoreSuggestionRepository repository = new NewStoreSuggestionRepository(template);

    @Test
    void repeatingDiscoveryNeverResetsAPresentedSuggestion() {
        repository.register(12, 4, LocalDate.of(2026, 10, 17), NOW);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(template).upsert(query.capture(), update.capture(), eq(NewStoreSuggestion.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("_id", "12:4"));
        assertThat(update.getValue().getUpdateObject().keySet()).containsExactly("$setOnInsert");
        Document initial = update.getValue().getUpdateObject().get("$setOnInsert", Document.class);
        assertThat(initial).containsEntry("profileId", 12).containsEntry("storeId", 4);
        assertThat(initial.get("presentedAt")).isNull();
    }

    @Test
    void consumesOnlyThePendingStoreForTheSelectedProfile() {
        repository.markPresented(12, 4, NOW);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(query.capture(), update.capture(), eq(NewStoreSuggestion.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", "12:4")
                .containsEntry("presentedAt", null);
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("presentedAt", NOW);
    }

    @Test
    void looksUpPendingSuggestionsWithoutMixingProfiles() {
        when(template.find(any(Query.class), eq(NewStoreSuggestion.class)))
                .thenReturn(List.of(new NewStoreSuggestion("12:4", 12, 4, "2026-10-17", NOW, null)));

        assertThat(repository.findPendingStoreIds(12)).containsExactly(4);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(template).find(query.capture(), eq(NewStoreSuggestion.class));
        assertThat(query.getValue().getQueryObject())
                .containsEntry("profileId", 12).containsEntry("presentedAt", null);
    }

    @Test
    void advancesTheCheckpointMonotonicallyEvenWithOverlappingExecutions() {
        repository.markScanned(LocalDate.of(2026, 10, 17));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(template).upsert(any(Query.class), update.capture(), eq(NewStoreDiscoveryState.class));
        assertThat(update.getValue().getUpdateObject().get("$max", Document.class))
                .containsEntry("scannedThrough", "2026-10-17");
    }

    @Test
    void retriesACheckpointInsertionRaceWithoutMovingTheDateBackwards() {
        when(template.upsert(any(Query.class), any(Update.class), eq(NewStoreDiscoveryState.class)))
                .thenThrow(new DuplicateKeyException("concurrent checkpoint"));
        when(template.updateFirst(any(Query.class), any(Update.class), eq(NewStoreDiscoveryState.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        repository.markScanned(LocalDate.of(2026, 10, 17));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(any(Query.class), update.capture(), eq(NewStoreDiscoveryState.class));
        assertThat(update.getValue().getUpdateObject().get("$max", Document.class))
                .containsEntry("scannedThrough", "2026-10-17");
    }

    @Test
    void doesNotHideADiscoveryCollisionWithoutAnExistingPair() {
        when(template.upsert(any(Query.class), any(Update.class), eq(NewStoreSuggestion.class)))
                .thenThrow(new DuplicateKeyException("conflicting index"));

        assertThatThrownBy(() -> repository.register(12, 4, LocalDate.of(2026, 10, 17), NOW))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
