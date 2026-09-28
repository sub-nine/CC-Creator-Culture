package com.sub9.orderservice.order.infrastructure.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OrderEventOutboxMetrics {
    private final Counter succeeded;
    private final Counter failed;

    public OrderEventOutboxMetrics(OrderEventOutboxJpaRepository outbox, Clock clock, MeterRegistry registry) {
        succeeded = registry.counter("order.event.outbox.publish", "result", "success");
        failed = registry.counter("order.event.outbox.publish", "result", "failure");
        // 조회할 때마다 DB에서 읽는다. 남은 이벤트가 늘거나 오래 머물면 발행이 막혀 있다는 신호다.
        Gauge.builder("order.event.outbox.pending", outbox, OrderEventOutboxJpaRepository::count)
                .register(registry);
        Gauge.builder("order.event.outbox.oldest.age", outbox, repository -> {
                    Instant oldest = repository.findOldestCreatedAt();
                    return oldest == null ? 0 : Duration.between(oldest, clock.instant()).toMillis() / 1000.0;
                })
                .baseUnit("seconds")
                .register(registry);
    }

    public void succeeded() {
        succeeded.increment();
    }

    public void failed() {
        failed.increment();
    }
}
