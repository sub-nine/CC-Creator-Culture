package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagUpsertResult;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("HashtagCommandRepositoryImpl - 동시성 통합 테스트")
class HashtagCommandRepositoryImplConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private HashtagCommandRepository hashtagCommandRepository;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Test
    @DisplayName("같은 해시태그의 usage_count를 여러 스레드가 동시에 증가시켜도, 원자적 UPDATE라 충돌·예외 없이 전부 반영된다")
    void increaseUsageCount_concurrentCalls_noLostUpdates() throws Exception {
        Hashtag hashtag = hashtagCommandRepository.save(Hashtag.create("동시성태그" + UUID.randomUUID()));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Exception>> futures = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    try {
                        hashtagCommandRepository.increaseUsageCount(hashtag.getId());
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }))
                .collect(Collectors.toList());

        readyLatch.await();
        startLatch.countDown();

        List<Exception> failures = new ArrayList<>();
        for (Future<Exception> future : futures) {
            Exception failure = future.get(20, TimeUnit.SECONDS);
            if (failure != null) {
                failures.add(failure);
            }
        }
        executor.shutdown();

        assertThat(failures).isEmpty();
        Hashtag reloaded = hashtagJpaRepository.findByIdAndDeletedAtIsNull(hashtag.getId()).orElseThrow();
        assertThat(reloaded.getUsageCount()).isEqualTo(THREAD_COUNT);
    }

    @Test
    @DisplayName("같은 이름의 해시태그를 여러 스레드가 동시에 findOrCreateByName 해도 단 하나만 생성되고, 전부 같은 id를 참조한다")
    void findOrCreateByName_concurrentCalls_createsOnlyOne() throws Exception {
        String name = "동시생성태그" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<HashtagUpsertResult>> futures = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return hashtagCommandRepository.findOrCreateByName(name);
                }))
                .collect(Collectors.toList());

        readyLatch.await();
        startLatch.countDown();

        List<HashtagUpsertResult> results = new ArrayList<>();
        for (Future<HashtagUpsertResult> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }
        executor.shutdown();

        Set<UUID> distinctHashtagIds = results.stream()
                .map(result -> result.hashtag().getId())
                .collect(Collectors.toSet());
        long createdCount = results.stream().filter(HashtagUpsertResult::created).count();

        assertThat(distinctHashtagIds).hasSize(1);
        assertThat(createdCount).isEqualTo(1);
        assertThat(hashtagJpaRepository.findByNameAndDeletedAtIsNull(name)).isPresent();
    }
}
