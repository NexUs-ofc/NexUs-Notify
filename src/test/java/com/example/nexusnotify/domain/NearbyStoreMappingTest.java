package com.example.nexusnotify.domain;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NearbyStoreMappingTest {

    @Test
    void roundTripsTheCycleWithIntegerProfileIdAndOptimisticVersion() {
        MappingMongoConverter converter = converter();
        Instant next = Instant.parse("2026-10-20T14:00:00Z");
        Document stored = new Document("_id", 12).append("version", 2L).append("cycle", 3L)
                .append("shown_store_ids", java.util.List.of(1, 4))
                .append("last_store_id", 4).append("next_eligible_at", Date.from(next));

        NearbyStoreRotation state = converter.read(NearbyStoreRotation.class, stored);
        Document result = new Document();
        converter.write(state, result);

        assertThat(state.getCycle()).isEqualTo(3);
        assertThat(state.getShownStoreIds()).containsExactlyInAnyOrder(1, 4);
        assertThat(state.getNextEligibleAt()).isEqualTo(next);
        assertThat(result).containsEntry("_id", 12).containsEntry("version", 2L)
                .containsEntry("last_store_id", 4).containsEntry("next_eligible_at", Date.from(next));
        assertThat(result).doesNotContainKeys("profileId", "shownStoreIds", "nextEligibleAt");
    }

    @Test
    void writesDiscoveryAndPendingFieldsWithoutAutomaticIndexes() {
        MappingMongoConverter converter = converter();
        Instant now = Instant.parse("2026-10-17T14:00:00Z");
        Document result = new Document();
        converter.write(new NewStoreSuggestion("12:4", 12, 4, "2026-10-17", now, null), result);

        assertThat(result).containsEntry("_id", "12:4").containsEntry("profile_id", 12)
                .containsEntry("store_id", 4).containsEntry("store_created_date", "2026-10-17")
                .containsEntry("discovered_at", Date.from(now));
        var resolver = new org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver(
                converter.getMappingContext());
        assertThat(resolver.resolveIndexFor(NearbyStoreRotation.class)).isEmpty();
        assertThat(resolver.resolveIndexFor(NewStoreSuggestion.class)).isEmpty();
    }

    private MappingMongoConverter converter() {
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> {});
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(NearbyStoreRotation.class, NewStoreSuggestion.class));
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return converter;
    }
}
