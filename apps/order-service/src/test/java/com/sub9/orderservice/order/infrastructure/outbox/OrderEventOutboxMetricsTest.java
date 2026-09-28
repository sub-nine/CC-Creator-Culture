package com.sub9.orderservice.order.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주문 이벤트 Outbox 지표")
class OrderEventOutboxMetricsTest {
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");

    @Test
    @DisplayName("남은 이벤트가 있으면 건수와 가장 오래된 이벤트의 경과 시간을 보고한다")
    void when_records_remain_pending_count_and_oldest_age_are_reported() {
        OrderEventOutboxJpaRepository outbox = mock(OrderEventOutboxJpaRepository.class);
        when(outbox.count()).thenReturn(3L);
        when(outbox.findOldestCreatedAt()).thenReturn(NOW.minusSeconds(7));
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new OrderEventOutboxMetrics(outbox, Clock.fixed(NOW, ZoneOffset.UTC), registry);
            metrics.succeeded();
            metrics.failed();
            metrics.failed();

            assertThat(registry.get("order.event.outbox.pending").gauge().value()).isEqualTo(3);
            assertThat(registry.get("order.event.outbox.oldest.age").gauge().value()).isEqualTo(7);
            assertThat(registry.get("order.event.outbox.publish").tag("result", "success").counter().count())
                    .isEqualTo(1);
            assertThat(registry.get("order.event.outbox.publish").tag("result", "failure").counter().count())
                    .isEqualTo(2);
        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("남은 이벤트가 없으면 가장 오래된 이벤트의 경과 시간을 0으로 보고한다")
    void when_no_record_remains_oldest_age_is_zero() {
        OrderEventOutboxJpaRepository outbox = mock(OrderEventOutboxJpaRepository.class);
        var registry = new SimpleMeterRegistry();
        try {
            new OrderEventOutboxMetrics(outbox, Clock.fixed(NOW, ZoneOffset.UTC), registry);

            assertThat(registry.get("order.event.outbox.oldest.age").gauge().value()).isZero();
        } finally {
            registry.close();
        }
    }
}
