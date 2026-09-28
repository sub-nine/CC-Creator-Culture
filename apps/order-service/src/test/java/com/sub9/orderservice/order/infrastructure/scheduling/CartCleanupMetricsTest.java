package com.sub9.orderservice.order.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("장바구니 정리 지표")
class CartCleanupMetricsTest {
    @Test
    @DisplayName("완료 지연은 생성 시각부터 집계하고 실패는 완료 건수에 포함하지 않는다")
    void when_cleanup_completes_latency_and_failure_are_separate() {
        Instant now = Instant.parse("2026-09-20T00:00:00Z");
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new CartCleanupMetrics(Clock.fixed(now, ZoneOffset.UTC), registry);
            metrics.succeeded(now.minusSeconds(7));
            metrics.failed();
            metrics.execution(TimeUnit.SECONDS.toNanos(2));
            assertThat(registry.get("order.cart.cleanup.completion").timer().count()).isEqualTo(1);
            assertThat(registry.get("order.cart.cleanup.completion").timer().totalTime(TimeUnit.SECONDS)).isEqualTo(7);
            assertThat(registry.get("order.cart.cleanup.tasks").tag("result", "failure").counter().count()).isEqualTo(1);
            assertThat(registry.get("order.cart.cleanup.execution").timer().totalTime(TimeUnit.SECONDS)).isEqualTo(2);
        } finally {
            registry.close();
        }
    }
}
