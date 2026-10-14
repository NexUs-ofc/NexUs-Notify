package com.example.nexusnotify.service;

import com.example.nexusnotify.controller.dto.NotificationResponse;
import com.example.nexusnotify.controller.dto.SliceResponse;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.exception.NotFoundException;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import com.example.nexusnotify.mapper.NotificationMapper;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public SliceResponse<NotificationResponse> list(Integer profileId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("A pagina deve ser maior ou igual a zero");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("O tamanho da pagina deve estar entre 1 e 100");
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Slice<Notification> notifications = repository.findByProfileIdOrderByCreatedAtDesc(profileId, pageable);
        return new SliceResponse<>(notifications.getContent().stream().map(NotificationMapper::toResponse).toList(),
                notifications.hasNext());
    }

    public void markAsRead(Integer profileId, String id) {
        if (!ObjectId.isValid(id)) {
            throw new IllegalArgumentException("Id de notificacao invalido");
        }

        ObjectId notificationId = new ObjectId(id);
        long updated = repository.markAsRead(notificationId, profileId, Instant.now(clock));
        if (updated == 0 && repository.findByIdAndProfileId(notificationId, profileId).isEmpty()) {
            throw new NotFoundException("Notificacao nao encontrada");
        }
    }
}
