package com.sub9.userservice.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresConcurrencySupport {

    private PostgresConcurrencySupport() {
    }

    public static void await(CountDownLatch latch) {
        try {
            // 잘못된 동기화나 교착으로 테스트가 무한히 멈추지 않도록 제한 시간을 둔다.
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("경쟁 요청의 잠금 해제 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            // 인터럽트 상태를 복구해 상위 실행 환경이 중단 사실을 확인할 수 있게 한다.
            Thread.currentThread().interrupt();
            throw new AssertionError("경쟁 요청 대기가 중단되었습니다.", exception);
        }
    }

    public static void awaitRowLock(
            JdbcTemplate jdbcTemplate, Future<?> contender, String tableName) throws Exception {
        // 잠금이 잘못 해제되지 않을 경우 테스트가 무한히 대기하지 않도록 제한 시간을 둔다.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String queryPattern = "%" + tableName + "%";

        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1
                          from pg_stat_activity
                         where datname = current_database()
                           and pid <> pg_backend_pid()
                           and wait_event_type = 'Lock'
                           and query ilike ?
                    )
                    """, Boolean.class, queryPattern);
            if (Boolean.TRUE.equals(waiting)) {
                assertThat(contender.isDone()).isFalse();
                return;
            }
            if (contender.isDone()) {
                contender.get(5, TimeUnit.SECONDS);
                throw new AssertionError(
                        "경쟁 요청이 " + tableName + " 행 잠금을 기다리지 않고 종료되었습니다.");
            }
            Thread.yield();
        }

        throw new AssertionError(tableName + " 행 잠금 대기를 확인하지 못했습니다.");
    }
}
