package com.sub9.productservice.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import lombok.experimental.UtilityClass;

/** 동시성 테스트 유틸 클래스 */
@UtilityClass
public class ConcurrencyTestingUtil {

  private static final long TIMEOUT_SECONDS = 10;

  public static void run(int threadCount, Runnable task) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);

    List<Future<?>> futures = new ArrayList<>(threadCount);

    try {
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();

                  task.run();
                  return null;
                }));
      }

      if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new AssertionError("동시 요청 준비 실패");
      }

      start.countDown();

      for (Future<?> future : futures) {
        future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
