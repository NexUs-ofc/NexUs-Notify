package com.example.nexusnotify.mapper;

import com.example.nexusnotify.controller.dto.NotificationResponse;
import com.example.nexusnotify.domain.Notification;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId().toHexString(), notification.getType(), notification.getSourceId(),
                notification.getTitle(), notification.getMessage(), notification.getCreatedAt(),
                notification.getReadAt(), notification.isRead());
    }
}
