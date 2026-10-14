package com.example.nexusnotify.controller.dto;

import com.example.nexusnotify.domain.NotificationType;
import java.time.Instant;

public record NotificationResponse(String id, NotificationType type, String sourceId, String title,
                                   String message, Instant createdAt, Instant readAt, boolean read) {
}
