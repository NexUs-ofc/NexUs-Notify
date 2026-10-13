package com.example.nexusnotify.service;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class NotificationWriter {

    private final NotificationRepository repository;

    public NotificationWriter(NotificationRepository repository) {
        this.repository = repository;
    }

    public boolean saveIfAbsent(Notification notification) {
        if (exists(notification)) {
            return false;
        }

        try {
            repository.insert(notification);
            return true;
        } catch (DuplicateKeyException exception) {
            if (exists(notification)) {
                return false;
            }
            throw exception;
        }
    }

    private boolean exists(Notification notification) {
        return repository.existsByProfileIdAndTypeAndSourceIdAndAlertKey(notification.getProfileId(),
                notification.getType(), notification.getSourceId(), notification.getAlertKey());
    }
}
