package com.example.nexusnotify.infra.push;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FirebasePushProviderTest {

    private FirebaseMessaging messaging;
    private FirebasePushProvider provider;
    private Notification notification;

    @BeforeEach
    void setUp() {
        messaging = mock(FirebaseMessaging.class);
        provider = new FirebasePushProvider(messaging);
        notification = new Notification(
                12,
                NotificationType.PRODUCT_EXPIRING,
                "45",
                "expiry:test",
                "Produto próximo do vencimento",
                "O produto Leite vence amanhã."
        );
        ReflectionTestUtils.setField(notification, "id", new ObjectId());
    }

    @Test
    void sendsTheSavedNotificationAsDataToAFidAndReturnsTheFirebaseMessageId() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/test/messages/123");

        PushProvider.Result result = provider.send(notification, "registered-fid");

        assertThat(result.status()).isEqualTo(PushProvider.Status.ACCEPTED);
        assertThat(result.messageId()).isEqualTo("projects/test/messages/123");
        assertThat(result.errorCode()).isNull();

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(sent.capture());

        // Inspeciona o payload produzido pelo proprio SDK, sem uma chamada de rede.
        Object transport = ReflectionTestUtils.invokeMethod(sent.getValue(), "wrapForTransport", false);
        JsonObject payload = JsonParser.parseString(GsonFactory.getDefaultInstance().toString(transport))
                .getAsJsonObject()
                .getAsJsonObject("message");
        assertThat(payload.get("fid").getAsString()).isEqualTo("registered-fid");
        assertThat(payload.has("token")).isFalse();
        assertThat(payload.has("notification")).isFalse();

        JsonObject data = payload.getAsJsonObject("data");
        assertThat(data.get("notificationId").getAsString()).isEqualTo(notification.getId().toHexString());
        assertThat(data.get("profileId").getAsString()).isEqualTo("12");
        assertThat(data.get("type").getAsString()).isEqualTo("PRODUCT_EXPIRING");
        assertThat(data.get("sourceId").getAsString()).isEqualTo("45");
        assertThat(data.get("title").getAsString()).isEqualTo(notification.getTitle());
        assertThat(data.get("message").getAsString()).isEqualTo(notification.getMessage());

        JsonObject android = payload.getAsJsonObject("android");
        assertThat(android.get("priority").getAsString()).isEqualTo("high");
        assertThat(android.get("ttl").getAsString()).isEqualTo("3600s");
    }

    @Test
    void identifiesOnlyUnregisteredAsAnInvalidInstallation() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        PushProvider.Result result = provider.send(notification, "unregistered-fid");

        assertThat(result.status()).isEqualTo(PushProvider.Status.INVALID_INSTALLATION);
        assertThat(result.errorCode()).isEqualTo("UNREGISTERED");
        assertThat(result.messageId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = MessagingErrorCode.class, names = "UNREGISTERED", mode = EnumSource.Mode.EXCLUDE)
    void doesNotInvalidateForPayloadConfigurationQuotaOrServerErrors(MessagingErrorCode code) throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        PushProvider.Result result = provider.send(notification, "registered-fid");

        assertThat(result.status()).isEqualTo(PushProvider.Status.FAILED);
        assertThat(result.errorCode()).isEqualTo(code.name());
        assertThat(result.messageId()).isNull();
    }

    @Test
    void handlesAnAuthenticationErrorWithoutAFcmSpecificCode() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.UNAUTHENTICATED);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        PushProvider.Result result = provider.send(notification, "registered-fid");

        assertThat(result.status()).isEqualTo(PushProvider.Status.FAILED);
        assertThat(result.errorCode()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void doesNotRequireMessagingWhenPushIsDisabledOrUnspecified() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(FirebasePushProvider.class);
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PushProvider.class);
        });
        runner.withPropertyValues("notify.push.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PushProvider.class);
        });
    }

    @Test
    void registersTheProviderWithoutSendingMessagesAtStartupWhenEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(FirebasePushProvider.class)
                .withBean(FirebaseMessaging.class, () -> messaging)
                .withPropertyValues("notify.push.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PushProvider.class);
                    verifyNoInteractions(messaging);
                });
    }
}
