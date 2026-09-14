package com.sub9.productservice.leaderboard.infrastructure.redis;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardEventType;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardScore;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

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
@DisplayName("RedisRepositoryImpl - 통합 테스트")
class RedisRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RedisRepositoryImpl redisRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        stringRedisTemplate.delete(LeaderboardRedisKey.current(LeaderboardType.CATEGORY));
        stringRedisTemplate.delete(LeaderboardRedisKey.current(LeaderboardType.HASHTAG));
    }

    @Test
    @DisplayName("동일 keyId로 두 번 반영을 시도하면 두 번째는 스킵되고 점수도 한 번만 반영된다")
    void incrementScoresIfNotProcessed_isIdempotentPerKeyId() {
        UUID categoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        List<LeaderboardScore> categoryScores = List.of(new LeaderboardScore(categoryId, 5.0));

        boolean firstApplied = redisRepository.incrementScoresIfNotProcessed(
                LeaderboardEventType.ORDER_PAID, orderId, categoryScores, List.of());
        boolean secondApplied = redisRepository.incrementScoresIfNotProcessed(
                LeaderboardEventType.ORDER_PAID, orderId, categoryScores, List.of());

        assertThat(firstApplied).isTrue();
        assertThat(secondApplied).isFalse();

        List<RankedMember> rankedMembers = redisRepository.getRankedMembers(LeaderboardType.CATEGORY);
        assertThat(rankedMembers).containsExactly(new RankedMember(1, categoryId, 5.0));
    }

    @Test
    @DisplayName("서로 다른 keyId의 반영은 각각 누적되고, 실시간 랭킹은 점수 내림차순으로 정렬된다")
    void getRankedMembers_returnsDescendingOrderWithRanking() {
        UUID categoryA = UUID.randomUUID();
        UUID categoryB = UUID.randomUUID();

        redisRepository.incrementScoresIfNotProcessed(
                LeaderboardEventType.ORDER_PAID, UUID.randomUUID(),
                List.of(new LeaderboardScore(categoryA, 3.0)), List.of());
        redisRepository.incrementScoresIfNotProcessed(
                LeaderboardEventType.PRODUCT_SYNC_VIEW, UUID.randomUUID(),
                List.of(new LeaderboardScore(categoryB, 10.0)), List.of());
        redisRepository.incrementScoresIfNotProcessed(
                LeaderboardEventType.ORDER_PAID, UUID.randomUUID(),
                List.of(new LeaderboardScore(categoryA, 4.0)), List.of());

        List<RankedMember> rankedMembers = redisRepository.getRankedMembers(LeaderboardType.CATEGORY);

        assertThat(rankedMembers).containsExactly(
                new RankedMember(1, categoryB, 10.0),
                new RankedMember(2, categoryA, 7.0)
        );
    }

    @Test
    @DisplayName("같은 keyId로 여러 스레드가 동시에 반영을 시도해도 SET NX 멱등성 마커 덕분에 단 한 번만 적용되고 점수도 한 번만 반영된다")
    void incrementScoresIfNotProcessed_concurrentCallsWithSameKeyId_appliesOnlyOnce() throws Exception {
        int threadCount = 20;
        UUID categoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        List<LeaderboardScore> categoryScores = List.of(new LeaderboardScore(categoryId, 5.0));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return redisRepository.incrementScoresIfNotProcessed(
                            LeaderboardEventType.ORDER_PAID, orderId, categoryScores, List.of());
                }))
                .collect(Collectors.toList());

        readyLatch.await();
        startLatch.countDown();

        long appliedCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(20, TimeUnit.SECONDS)) {
                appliedCount++;
            }
        }
        executor.shutdown();

        assertThat(appliedCount).isEqualTo(1);
        List<RankedMember> rankedMembers = redisRepository.getRankedMembers(LeaderboardType.CATEGORY);
        assertThat(rankedMembers).containsExactly(new RankedMember(1, categoryId, 5.0));
    }
}
