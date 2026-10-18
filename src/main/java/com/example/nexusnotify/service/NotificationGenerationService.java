package com.example.nexusnotify.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.IntSupplier;

@Service
public class NotificationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationGenerationService.class);
    private final ProductNotificationGenerationService products;
    private final EventNotificationGenerationService events;
    private final NearbyStoreNotificationGenerationService stores;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final Timer successTimer;
    private final Timer failureTimer;

    public NotificationGenerationService(ProductNotificationGenerationService products,
                                         EventNotificationGenerationService events,
                                         NearbyStoreNotificationGenerationService stores,
                                         Clock clock, MeterRegistry metrics) {
        this.products = products;
        this.events = events;
        this.stores = stores;
        this.clock = clock;
        this.metrics = metrics;
        this.successTimer = metrics.timer("notify.generation.duration", "result", "success");
        this.failureTimer = metrics.timer("notify.generation.duration", "result", "failure");
    }

    public int generate() {
        Timer.Sample sample = Timer.start(metrics);
        Timer timer = failureTimer;
        try {
            LocalDate today = LocalDate.now(clock);
            List<Flow> flows = List.of(
                    new Flow("products", () -> products.generate(today)),
                    new Flow("events", () -> events.generate(today)),
                    new Flow("stores", () -> stores.generate(today))
            );
            int created = 0;
            RuntimeException failure = null;
            for (Flow flow : flows) {
                try {
                    created += generateFlow(flow);
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else if (failure != exception) {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
            timer = successTimer;
            return created;
        } finally {
            sample.stop(timer);
        }
    }

    private int generateFlow(Flow flow) {
        Timer.Sample sample = Timer.start(metrics);
        String result = "failure";
        try {
            int created = flow.generation().getAsInt();
            result = "success";
            logger.info("Fluxo de notificacoes concluido flow={} criadas={}", flow.name(), created);
            return created;
        } catch (RuntimeException exception) {
            logger.error("Falha no fluxo de notificacoes flow={} error_type={}",
                    flow.name(), exception.getClass().getSimpleName());
            throw exception;
        } finally {
            sample.stop(metrics.timer("notify.generation.flow.duration", "flow", flow.name(), "result", result));
        }
    }

    private record Flow(String name, IntSupplier generation) {}
}
