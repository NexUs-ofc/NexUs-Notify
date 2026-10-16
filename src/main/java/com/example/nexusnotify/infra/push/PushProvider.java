package com.example.nexusnotify.infra.push;

import com.example.nexusnotify.domain.Notification;

public interface PushProvider {

    Result send(Notification notification, String installationId);

    enum Status {
        ACCEPTED,
        INVALID_INSTALLATION,
        FAILED
    }

    record Result(Status status, String messageId, String errorCode) {
    }
}
