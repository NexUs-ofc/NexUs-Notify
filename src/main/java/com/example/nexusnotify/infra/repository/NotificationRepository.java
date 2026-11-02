package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, ObjectId> {

    Slice<Notification> findByProfileIdOrderByCreatedAtDesc(Integer profileId, Pageable pageable);

    boolean existsByProfileIdAndTypeAndSourceIdAndAlertKey(Integer profileId, NotificationType type,
            String sourceId, String alertKey);
}
