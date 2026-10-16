package com.example.nexusnotify.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

@Document(collection = "push_installations")
public record PushInstallation(
        @Id ObjectId id,
        @Field("profile_id") Integer profileId,
        @Field("installation_id") String installationId,
        PushInstallationStatus status,
        @Field("created_at") Instant createdAt,
        @Field("updated_at") Instant updatedAt,
        @Field("last_seen_at") Instant lastSeenAt,
        @Field("disabled_at") Instant disabledAt,
        @Field("disabled_reason") String disabledReason
) {
}
