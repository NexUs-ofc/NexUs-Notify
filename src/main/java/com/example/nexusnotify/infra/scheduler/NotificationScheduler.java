package com.example.nexusnotify.infra.scheduler;

import com.example.nexusnotify.service.NotificationGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "notify.scheduler", name = "enabled", havingValue = "true")
public class NotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationScheduler.class);
    private final NotificationGenerationService generationService;

    public NotificationScheduler(NotificationGenerationService generationService) {
        this.generationService = generationService;
    }

    @Scheduled(cron = "${notify.scheduler.cron}", zone = "${notify.notification.zone-id}")
    public void generate() {
        long started = System.nanoTime();
        logger.info("Iniciando geração automática de notificações");
        try {
            int created = generationService.generate();
            logger.info("Geração de notificações concluída criadas={} duracao_ms={}", created,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (RuntimeException exception) {
            logger.error("Falha na geração automática de notificações duracao_ms={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), exception);
        }
    }
}
