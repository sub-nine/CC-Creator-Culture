package com.sub9.orderservice.order.infrastructure.scheduling;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class CartCleanupMetrics {
    private final Clock clock;
    private final Counter succeeded;
    private final Counter failed;
    private final Timer completion;
    private final Timer execution;

    public CartCleanupMetrics(Clock clock, MeterRegistry registry) {
        this.clock = clock;
        succeeded = registry.counter("order.cart.cleanup.tasks", "result", "success");
        failed = registry.counter("order.cart.cleanup.tasks", "result", "failure");
        completion = Timer.builder("order.cart.cleanup.completion")
                .publishPercentileHistogram()
                .maximumExpectedValue(Duration.ofMinutes(10)).register(registry);
        execution = Timer.builder("order.cart.cleanup.execution").register(registry);
    }

    public void succeeded(Instant createdAt) {
        succeeded.increment();
        completion.record(Duration.ofMillis(Math.max(0, clock.millis() - createdAt.toEpochMilli())));
    }

    public void failed() {
        failed.increment();
    }

    public void execution(long nanos) {
        execution.record(Duration.ofNanos(nanos));
    }
}
