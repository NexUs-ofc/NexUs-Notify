package com.example.nexusnotify.infra.scheduler;

import com.example.nexusnotify.config.NotificationConfiguration;
import com.example.nexusnotify.service.NotificationGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.SimpleTriggerContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class NotificationSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfiguration.class, NotificationScheduler.class)
            .withBean(NotificationGenerationService.class, () -> mock(NotificationGenerationService.class))
            .withPropertyValues(
                    "notify.notification.product-days-before=3,1,0",
                    "notify.notification.event-days-before=7,3,1,0",
                    "notify.notification.page-size=100",
                    "notify.notification.zone-id=America/Sao_Paulo",
                    "notify.scheduler.enabled=true",
                    "notify.scheduler.cron=0 0 11 * * *");

    @Test
    void runsGenerationAndLogsTheResult(CapturedOutput output) {
        NotificationGenerationService service = mock(NotificationGenerationService.class);
        when(service.generate()).thenReturn(3);
        NotificationScheduler scheduler = new NotificationScheduler(service);

        scheduler.generate();

        verify(service).generate();
        assertThat(output).contains("Geração de notificações concluída criadas=3 duracao_ms=");
    }

    @Test
    void logsFailuresAndAllowsTheNextExecution(CapturedOutput output) {
        NotificationGenerationService service = mock(NotificationGenerationService.class);
        when(service.generate()).thenThrow(new IllegalStateException("unavailable")).thenReturn(1);
        NotificationScheduler scheduler = new NotificationScheduler(service);

        assertDoesNotThrow(scheduler::generate);
        assertDoesNotThrow(scheduler::generate);

        verify(service, times(2)).generate();
        assertThat(output).contains("Falha na geração automática de notificações duracao_ms=")
                .contains("Geração de notificações concluída criadas=1 duracao_ms=");
    }

    @Test
    void schedulesGenerationAtElevenInSaoPaulo() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(NotificationScheduler.class);
            ScheduledAnnotationBeanPostProcessor processor = context.getBean(
                    ScheduledAnnotationBeanPostProcessor.class);
            assertThat(processor.getScheduledTasks()).hasSize(1);
            CronTask task = (CronTask) processor.getScheduledTasks().iterator().next().getTask();
            Clock clock = Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC);

            assertEquals("0 0 11 * * *", task.getExpression());
            assertEquals(Instant.parse("2026-10-13T14:00:00Z"),
                    task.getTrigger().nextExecution(new SimpleTriggerContext(clock)));
        });
    }

    @Test
    void doesNotRegisterTheJobWhenDisabled() {
        contextRunner.withPropertyValues("notify.scheduler.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(NotificationScheduler.class);
            ScheduledAnnotationBeanPostProcessor processor = context.getBean(
                    ScheduledAnnotationBeanPostProcessor.class);
            assertThat(processor.getScheduledTasks()).isEmpty();
        });
    }

    @Test
    void rejectsAnInvalidCron() {
        contextRunner.withPropertyValues("notify.scheduler.cron=invalid").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }
}
