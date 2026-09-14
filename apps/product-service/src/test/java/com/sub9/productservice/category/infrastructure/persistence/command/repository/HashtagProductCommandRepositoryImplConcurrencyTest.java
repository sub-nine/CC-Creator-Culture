package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.HashtagProductCommandRepository;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagProductJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
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
@DisplayName("HashtagProductCommandRepositoryImpl - 동시성 통합 테스트")
class HashtagProductCommandRepositoryImplConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private HashtagProductCommandRepository hashtagProductCommandRepository;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Autowired
    private HashtagProductJpaRepository hashtagProductJpaRepository;

    @Test
    @DisplayName("같은 해시태그-상품 조합을 여러 스레드가 동시에 linkIfAbsent 해도 단 하나만 실제로 연결되고, 행도 하나만 생성된다")
    void linkIfAbsent_concurrentCalls_linksOnlyOnce() throws Exception {
        Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("동시링크태그" + UUID.randomUUID()));
        UUID productId = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return hashtagProductCommandRepository.linkIfAbsent(hashtag.getId(), productId);
                }))
                .collect(Collectors.toList());

        readyLatch.await();
        startLatch.countDown();

        long linkedCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(20, TimeUnit.SECONDS)) {
                linkedCount++;
            }
        }
        executor.shutdown();

        assertThat(linkedCount).isEqualTo(1);
        long persistedLinkCount = hashtagProductJpaRepository.findAll().stream()
                .filter(link -> link.getHashtag().getId().equals(hashtag.getId())
                        && link.getProductId().equals(productId))
                .count();
        assertThat(persistedLinkCount).isEqualTo(1);
    }
}
