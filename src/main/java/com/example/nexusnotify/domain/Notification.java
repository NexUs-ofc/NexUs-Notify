package com.example.nexusnotify.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;
import java.util.Objects;

@Document(collection = "notifications")
public class Notification {

    @Id
    private ObjectId id;

    @Field("profile_id")
    private Integer profileId;

    private NotificationType type;

    @Field("source_id")
    private String sourceId;

    @Field("alert_key")
    private String alertKey;

    private String title;

    private String message;

    @Field("created_at")
    private Instant createdAt;

    @Field("read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(Integer profileId, NotificationType type, String sourceId, String alertKey,
                        String title, String message) {
        this.profileId = Objects.requireNonNull(profileId);
        this.type = Objects.requireNonNull(type);
        this.sourceId = Objects.requireNonNull(sourceId);
        this.alertKey = Objects.requireNonNull(alertKey);
        this.title = Objects.requireNonNull(title);
        this.message = Objects.requireNonNull(message);
        this.createdAt = Instant.now();
    }

    public void markAsRead(Instant when) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(when);
        }
    }

    public ObjectId getId() {
        return id;
    }

    public Integer getProfileId() {
        return profileId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getAlertKey() {
        return alertKey;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
