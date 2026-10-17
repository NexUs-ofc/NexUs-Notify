package com.example.nexusnotify.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

@Document(collection = "new_store_suggestions")
public record NewStoreSuggestion(
        @Id String id,
        @Field("profile_id") Integer profileId,
        @Field("store_id") Integer storeId,
        @Field("store_created_date") String storeCreatedDate,
        @Field("discovered_at") Instant discoveredAt,
        @Field("presented_at") Instant presentedAt
) {}
