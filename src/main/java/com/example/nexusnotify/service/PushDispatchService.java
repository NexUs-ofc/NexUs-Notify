package com.example.nexusnotify.service;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.PushInstallation;
import com.example.nexusnotify.infra.push.PushProvider;
import com.example.nexusnotify.infra.repository.PushInstallationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class PushDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(PushDispatchService.class);

    /** Uma contagem por tentativa de envio, com o desfecho na tag {@code result}. Alimenta o painel. */
    static final String DELIVERIES_METRIC = "notify.push.deliveries";

    private final ObjectProvider<PushProvider> providers;
    private final PushInstallationRepository installations;
    private final Clock clock;
    private final MeterRegistry metrics;

    public PushDispatchService(
            ObjectProvider<PushProvider> providers,
            PushInstallationRepository installations,
            Clock clock,
            MeterRegistry metrics) {
        this.providers = providers;
        this.installations = installations;
        this.clock = clock;
        this.metrics = metrics;
    }

    public void send(Notification notification) {
        try {
            PushProvider provider = providers.getIfAvailable();
            if (provider == null) {
                return;
            }

            for (PushInstallation installation : installations.findActiveByProfileId(notification.getProfileId())) {
                sendToInstallation(provider, notification, installation);
            }
        } catch (RuntimeException exception) {
            logger.error(
                    "Falha ao processar push notification_id={} error_type={}",
                    notification.getId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void sendToInstallation(
            PushProvider provider,
            Notification notification,
            PushInstallation installation) {
        try {
            if (!notification.getProfileId().equals(installation.profileId())
                    || !installations.isCurrentActive(installation)) {
                return;
            }

            PushProvider.Result result = provider.send(notification, installation.installationId());
            countDelivery(result.status().name().toLowerCase(Locale.ROOT));

            switch (result.status()) {
                case ACCEPTED -> logger.info(
                        "Push aceito notification_id={} installation_record_id={} message_id={}",
                        notification.getId(),
                        installation.id(),
                        result.messageId()
                );
                case INVALID_INSTALLATION -> {
                    boolean invalidated = installations.markInvalid(installation, Instant.now(clock));

                    logger.warn(
                            "Registro FCM invalido notification_id={} installation_record_id={} invalidated={}",
                            notification.getId(),
                            installation.id(),
                            invalidated
                    );
                }
                case FAILED -> logger.warn(
                        "Falha no push notification_id={} installation_record_id={} code={}",
                        notification.getId(),
                        installation.id(),
                        result.errorCode()
                );
                default -> throw new IllegalStateException("Resultado de envio nao reconhecido");
            }
        } catch (RuntimeException exception) {
            countDelivery("error");
            logger.error(
                    "Falha no push notification_id={} installation_record_id={} error_type={}",
                    notification.getId(),
                    installation.id(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void countDelivery(String result) {
        metrics.counter(DELIVERIES_METRIC, "result", result).increment();
    }
}
