package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.NewStoreDiscoveryState;
import com.example.nexusnotify.domain.NewStoreSuggestion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class NewStoreSuggestionRepository {

    private static final String DISCOVERY_ID = "new-stores";
    private final MongoTemplate template;

    public NewStoreSuggestionRepository(MongoTemplate template) {
        this.template = template;
    }

    public void register(Integer profileId, Integer storeId, LocalDate createdDate, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(profileId + ":" + storeId));
        Update update = new Update()
                .setOnInsert("profileId", profileId)
                .setOnInsert("storeId", storeId)
                .setOnInsert("storeCreatedDate", createdDate.toString())
                .setOnInsert("discoveredAt", now)
                .setOnInsert("presentedAt", null);
        try {
            template.upsert(query, update, NewStoreSuggestion.class);
        } catch (DuplicateKeyException exception) {
            if (!template.exists(query, NewStoreSuggestion.class)) {
                throw exception;
            }
        }
    }

    public Set<Integer> findPendingStoreIds(Integer profileId) {
        Query query = Query.query(Criteria.where("profileId").is(profileId).and("presentedAt").is(null));
        return template.find(query, NewStoreSuggestion.class).stream()
                .map(NewStoreSuggestion::storeId).collect(Collectors.toSet());
    }

    public void markPresented(Integer profileId, Integer storeId, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(profileId + ":" + storeId).and("presentedAt").is(null));
        template.updateFirst(query, new Update().set("presentedAt", now), NewStoreSuggestion.class);
    }

    public Optional<LocalDate> lastScanDate() {
        return Optional.ofNullable(template.findById(DISCOVERY_ID, NewStoreDiscoveryState.class))
                .map(state -> LocalDate.parse(state.scannedThrough()));
    }

    public void markScanned(LocalDate date) {
        Query query = Query.query(Criteria.where("id").is(DISCOVERY_ID));
        Update update = new Update().max("scannedThrough", date.toString());
        try {
            template.upsert(query, update, NewStoreDiscoveryState.class);
        } catch (DuplicateKeyException exception) {
            if (template.updateFirst(query, update, NewStoreDiscoveryState.class).getMatchedCount() == 0) {
                throw exception;
            }
        }
    }
}
