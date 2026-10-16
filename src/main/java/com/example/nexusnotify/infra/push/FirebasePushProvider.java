package com.example.nexusnotify.infra.push;

import com.example.nexusnotify.domain.Notification;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "notify.push", name = "enabled", havingValue = "true")
public class FirebasePushProvider implements PushProvider {

    private final FirebaseMessaging messaging;

    public FirebasePushProvider(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public Result send(Notification notification, String installationId) {
        Message message = Message.builder()
                .setFid(installationId)
                .putData("notificationId", notification.getId().toHexString())
                .putData("profileId", notification.getProfileId().toString())
                .putData("type", notification.getType().name())
                .putData("sourceId", notification.getSourceId())
                .putData("title", notification.getTitle())
                .putData("message", notification.getMessage())
                .setAndroidConfig(
                        AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setTtl(Duration.ofHours(1).toMillis())
                                .build()
                )
                .build();

        try {
            return new Result(Status.ACCEPTED, messaging.send(message), null);
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode messagingCode = exception.getMessagingErrorCode();
            String errorCode = messagingCode != null
                    ? messagingCode.name()
                    : String.valueOf(exception.getErrorCode());
            Status status = messagingCode == MessagingErrorCode.UNREGISTERED
                    ? Status.INVALID_INSTALLATION
                    : Status.FAILED;

            return new Result(status, null, errorCode);
        }
    }
}
