package com.sub9.productservice.category.application.command.service;

import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryHashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
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
@DisplayName("CategoryHashtagLinkService - 동시성 통합 테스트")
class CategoryHashtagLinkServiceConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private CategoryHashtagLinkService categoryHashtagLinkService;

    @Autowired
    private CategoryCommandRepository categoryCommandRepository;

    @Autowired
    private HashtagCommandRepository hashtagCommandRepository;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private CategoryHashtagJpaRepository categoryHashtagJpaRepository;

    @Test
    @DisplayName("같은 해시태그에 대해 여러 스레드가 동시에 tryLink()를 호출해도, " +
            "이미 존재하는 매칭 카테고리에는 CategoryHashtag가 단 하나만 생성된다")
    void tryLink_concurrentCallsWithExistingMatch_createsOnlyOneLink() throws Exception {
        String name = "동시성카테고리" + UUID.randomUUID();
        Category category = categoryCommandRepository.save(Category.create(name, null));
        Hashtag hashtag = hashtagCommandRepository.save(Hashtag.create(name));

        List<Exception> failures = runConcurrently(hashtag.getId());

        // DB unique 제약(category_id+hashtag_id+unique_version) 덕분에 진 쪽은 예외를 던질 수 있지만,
        // 그 예외 때문에 중복 행이 생기진 않는다는 게 핵심 - 예외 발생 자체는 실패로 보지 않음
        List<CategoryHashtag> links = categoryHashtagJpaRepository.findAll().stream()
                .filter(link -> link.getCategory().getId().equals(category.getId())
                        && link.getHashtag().getId().equals(hashtag.getId()))
                .toList();
        assertThat(links).hasSize(1);

        logIfAnyFailures(failures);
    }

    @Test
    @DisplayName("같은 해시태그에 대해 여러 스레드가 동시에 tryLink()를 호출해도, " +
            "매칭되는 카테고리가 없으면 신규 카테고리가 단 하나만 생성된다")
    void tryLink_concurrentCallsWithNoMatch_promotesOnlyOneNewCategory() throws Exception {
        String name = "신규동시성카테고리" + UUID.randomUUID();
        Hashtag hashtag = hashtagCommandRepository.save(Hashtag.create(name));

        List<Exception> failures = runConcurrently(hashtag.getId());

        List<Category> createdCategories = categoryJpaRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .toList();
        assertThat(createdCategories).hasSize(1);

        List<CategoryHashtag> links = categoryHashtagJpaRepository.findAll().stream()
                .filter(link -> link.getHashtag().getId().equals(hashtag.getId()))
                .toList();
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getCategory().getId()).isEqualTo(createdCategories.get(0).getId());

        logIfAnyFailures(failures);
    }

    private List<Exception> runConcurrently(UUID hashtagId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Exception>> futures = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    try {
                        categoryHashtagLinkService.tryLink(hashtagId);
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
        return failures;
    }

    // 진 쪽이 unique 제약 위반 예외를 던지는 것 자체는 정상(재전달로 재시도되는 것을 전제) -
    // 최종 데이터가 중복 없이 하나로 수렴하는지가 이 테스트의 핵심이라 예외는 로그로만 남김
    private void logIfAnyFailures(List<Exception> failures) {
        failures.forEach(e -> System.out.println("[동시성 테스트] 예상된 경합 실패: " + e));
    }
}
