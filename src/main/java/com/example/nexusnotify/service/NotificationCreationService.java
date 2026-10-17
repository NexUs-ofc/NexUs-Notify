package com.example.nexusnotify.service;

import com.example.nexusnotify.domain.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class NotificationCreationService {

    private final NotificationWriter writer;
    private final PushDispatchService pushDispatch;
    private final MeterRegistry metrics;

    public NotificationCreationService(NotificationWriter writer, PushDispatchService pushDispatch,
                                        MeterRegistry metrics) {
        this.writer = writer;
        this.pushDispatch = pushDispatch;
        this.metrics = metrics;
    }

    public int create(Optional<Notification> result) {
        if (result.isEmpty()) {
            return 0;
        }
        Notification notification = result.get();
        if (!writer.saveIfAbsent(notification)) {
            metrics.counter("notify.notifications.duplicates", "type", notification.getType().name()).increment();
            return 0;
        }
        dispatchCreated(notification);
        return 1;
    }

    public void dispatchCreated(Notification notification) {
        metrics.counter("notify.notifications.created", "type", notification.getType().name()).increment();
        pushDispatch.send(notification);
    }
}
