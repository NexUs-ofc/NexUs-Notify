package com.example.nexusnotify.service;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.domain.PushInstallation;
import com.example.nexusnotify.domain.PushInstallationStatus;
import com.example.nexusnotify.infra.push.PushProvider;
import com.example.nexusnotify.infra.repository.PushInstallationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushDispatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private PushProvider provider;
    private PushInstallationRepository installations;
    private PushDispatchService service;
    private Clock clock;
    private Notification notification;
    private PushInstallation first;
    private PushInstallation second;
    private MeterRegistry metrics;

    @BeforeEach
    void setUp() {
        provider = mock(PushProvider.class);
        installations = mock(PushInstallationRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        metrics = new SimpleMeterRegistry();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("pushProvider", provider);
        service = new PushDispatchService(beans.getBeanProvider(PushProvider.class), installations, clock,
                metrics);
        notification = new Notification(
                12,
                NotificationType.PRODUCT_EXPIRING,
                "45",
                "expiry:test",
                "Produto próximo do vencimento",
                "O produto Leite vence amanhã."
        );
        ReflectionTestUtils.setField(notification, "id", new ObjectId());
        first = installation(12, "first-fid");
        second = installation(12, "second-fid");
        when(installations.findActiveByProfileId(12)).thenReturn(List.of(first, second));
        when(installations.isCurrentActive(any())).thenReturn(true);
        when(provider.send(any(), any())).thenReturn(accepted());
    }

    @Test
    void countsEachDeliveryAttemptByResultForTheAdminPanel() {
        service.send(notification);

        assertThat(metrics.counter(PushDispatchService.DELIVERIES_METRIC, "result", "accepted").count())
                .isEqualTo(2.0);
    }

    @Test
    void doesNotAccessMongoOrFirebaseWhenPushIsDisabled() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        service = new PushDispatchService(beans.getBeanProvider(PushProvider.class), installations, clock,
                metrics);

        service.send(notification);

        verifyNoInteractions(installations, provider);
    }

    @Test
    void sendsToEveryActiveInstallationOfTheRecipientWithoutChangingItsReadState() {
        service.send(notification);

        verify(provider).send(notification, first.installationId());
        verify(provider).send(notification, second.installationId());
        verify(installations, never()).markInvalid(any(), any());
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void doesNotSendWhenTheProfileHasNoActiveInstallations() {
        when(installations.findActiveByProfileId(12)).thenReturn(List.of());

        service.send(notification);

        verifyNoInteractions(provider);
    }

    @Test
    void skipsABindingRevokedTransferredOrUpdatedSinceItWasSelected() {
        when(installations.isCurrentActive(first)).thenReturn(false);

        service.send(notification);

        verify(provider, never()).send(notification, first.installationId());
        verify(provider).send(notification, second.installationId());
    }

    @Test
    void refusesAnInstallationOfAnotherProfileEvenIfReturnedByTheRepository() {
        PushInstallation anotherProfile = installation(13, "another-profile-fid");
        when(installations.findActiveByProfileId(12)).thenReturn(List.of(anotherProfile));

        service.send(notification);

        verifyNoInteractions(provider);
        verify(installations, never()).isCurrentActive(anotherProfile);
    }

    @Test
    void marksAnUnregisteredInstallationInvalidWithoutStoppingOtherRecipients() {
        when(provider.send(notification, first.installationId()))
                .thenReturn(new PushProvider.Result(
                        PushProvider.Status.INVALID_INSTALLATION,
                        null,
                        "UNREGISTERED"
                ));
        when(installations.markInvalid(first, NOW)).thenReturn(true);

        service.send(notification);

        verify(installations).markInvalid(first, NOW);
        verify(provider).send(notification, second.installationId());
        verify(installations, never()).markInvalid(second, NOW);
    }

    @Test
    void leavesInstallationsActiveOnTemporaryOrConfigurationFailures() {
        when(provider.send(notification, first.installationId()))
                .thenReturn(new PushProvider.Result(
                        PushProvider.Status.FAILED,
                        null,
                        "UNAVAILABLE"
                ));

        service.send(notification);

        verify(provider).send(notification, second.installationId());
        verify(installations, never()).markInvalid(any(), any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void isolatesUnexpectedSendFailuresAndDoesNotLogTheRawException(CapturedOutput output) {
        when(provider.send(notification, first.installationId()))
                .thenThrow(new IllegalArgumentException("private-fid-and-payload-must-not-be-logged"));

        assertThatCode(() -> service.send(notification)).doesNotThrowAnyException();

        verify(provider).send(notification, second.installationId());
        verify(installations, never()).markInvalid(any(), any());
        assertThat(output.getAll())
                .contains("IllegalArgumentException")
                .doesNotContain("private-fid-and-payload-must-not-be-logged", first.installationId());
    }

    @Test
    void preservesTheSavedNotificationWhenTheInstallationLookupFails() {
        when(installations.findActiveByProfileId(12))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));

        assertThatCode(() -> service.send(notification)).doesNotThrowAnyException();

        verifyNoInteractions(provider);
        assertThat(notification.getId()).isNotNull();
    }

    private PushInstallation installation(int profileId, String fid) {
        return new PushInstallation(
                new ObjectId(),
                profileId,
                fid,
                PushInstallationStatus.ACTIVE,
                NOW.minusSeconds(120),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                null,
                null
        );
    }

    private PushProvider.Result accepted() {
        return new PushProvider.Result(PushProvider.Status.ACCEPTED, "projects/test/messages/123", null);
    }
}
