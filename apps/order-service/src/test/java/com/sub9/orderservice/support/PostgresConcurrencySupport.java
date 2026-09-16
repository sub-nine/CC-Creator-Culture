package com.sub9.orderservice.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresConcurrencySupport {
    private PostgresConcurrencySupport() {}

    public static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("경쟁 요청의 잠금 해제 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("경쟁 요청 대기가 중단되었습니다.", failure);
        }
    }

    public static void awaitOrderLock(JdbcTemplate jdbc, Future<?> contender) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean waiting = jdbc.queryForObject("""
                    select exists (
                        select 1 from pg_stat_activity
                        where datname = current_database() and wait_event_type = 'Lock'
                          and query like '%p_orders%'
                    )
                    """, Boolean.class);
            if (waiting) {
                assertThat(contender.isDone()).isFalse();
                return;
            }
            if (contender.isDone()) {
                contender.get(5, TimeUnit.SECONDS);
                throw new AssertionError("경쟁 요청이 주문 잠금을 기다리지 않고 종료되었습니다.");
            }
            Thread.yield();
        }
        throw new AssertionError("PostgreSQL의 주문 잠금 대기를 확인하지 못했습니다.");
    }
}
