package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, ObjectId> {

    Slice<Notification> findByProfileIdOrderByCreatedAtDesc(Integer profileId, Pageable pageable);

    Optional<Notification> findByIdAndProfileId(ObjectId id, Integer profileId);

    boolean existsByProfileIdAndTypeAndSourceIdAndAlertKey(Integer profileId, NotificationType type,
            String sourceId, String alertKey);
}
