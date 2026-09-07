package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.leaderboard.application.port.in.RecordOrderScoreUseCase;
import com.sub9.productservice.leaderboard.application.port.in.RecordProductViewScoreUseCase;
import com.sub9.productservice.leaderboard.application.port.out.CategoryQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.HashtagQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardScoreService implements RecordOrderScoreUseCase, RecordProductViewScoreUseCase {

    // TODO: 주문 1건당 점수 가중치 - 임시값
    private static final double ORDER_SCORE_WEIGHT = 1.0;
    private static final Duration ORDER_PROCESSED_TTL = Duration.ofDays(1);
    private static final Duration PRODUCT_VIEW_PROCESSED_TTL = Duration.ofDays(1);

    private final CategoryQueryPort categoryQueryPort;
    private final HashtagQueryPort hashtagQueryPort;
    private final RedisRepository redisRepository;

    @Override
    public void recordOrderScore(UUID orderId, UUID productId) {
        // 카테고리와 해시태그 리더보드에 반영될 Score 계산
        Map<UUID, Double> categoryScores = resolveCategoryScoresByTarget(Map.of(productId, ORDER_SCORE_WEIGHT));
        Map<UUID, Double> hashtagScores = resolveHashtagScoresByTarget(Map.of(productId, ORDER_SCORE_WEIGHT));

        // 계산된 Score 멱등적으로 반영
        boolean applied = redisRepository.incrementScoresIfNotProcessedForOrder(
                orderId, ORDER_PROCESSED_TTL, categoryScores, hashtagScores);

        if (!applied) {
            log.info("[LEADERBOARD] 이미 처리된 주문이라 점수 반영 스킵 - orderId: {}", orderId);
        }
    }

    @Override
    public void recordProductViewScore(UUID eventId, LocalDate localDate, Map<UUID, Long> productViewCounts) {
        // 새로 추가되는 productId에 대한 viewCounts
        Map<UUID, Double> scoreByProductId = new HashMap<>();
        productViewCounts.forEach((productId, viewCount) -> scoreByProductId.put(productId, (double) viewCount));

        // 카테고리와 해시태그 리더보드에 반영될 Score 계산
        Map<UUID, Double> categoryScores = resolveCategoryScoresByTarget(scoreByProductId);
        Map<UUID, Double> hashtagScores = resolveHashtagScoresByTarget(scoreByProductId);

        // 계산된 Score 멱등적으로 반영
        boolean applied = redisRepository.incrementScoresIfNotProcessedForProductView(
                eventId, PRODUCT_VIEW_PROCESSED_TTL, categoryScores, hashtagScores);

        if (!applied) {
            log.info("[LEADERBOARD] 이미 처리된 조회 이벤트라 점수 반영 스킵 - eventId: {}", eventId);
        }
    }

    private Map<UUID, Double> resolveCategoryScoresByTarget(Map<UUID, Double> scoreByProductId) {
        List<UUID> productIds = List.copyOf(scoreByProductId.keySet());

        // 조회된 product.id에 대한 category.id 조회
        Map<UUID, UUID> categoryIdByProductId = categoryQueryPort.getCategoryIdsByProductIds(productIds);

        // Score가 추가될 Category에 대한 점수 계산
        Map<UUID, Double> categoryScores = new HashMap<>();
        scoreByProductId.forEach((productId, score) -> {
            UUID categoryId = categoryIdByProductId.get(productId);
            if (categoryId != null) {
                categoryScores.merge(categoryId, score, Double::sum);
            }
        });

        return categoryScores;
    }

    private Map<UUID, Double> resolveHashtagScoresByTarget(Map<UUID, Double> scoreByProductId) {
        List<UUID> productIds = List.copyOf(scoreByProductId.keySet());

        // 조회된 Product에 대한 hastag.id 조회
        Map<UUID, List<UUID>> hashtagIdsByProductId = hashtagQueryPort.getHashtagIdsByProductIds(productIds);
        Map<UUID, Double> hashtagScores = new HashMap<>();

        // Score가 추가될 Hashtag에 대한 점수 계산
        scoreByProductId.forEach((productId, score) -> {
            hashtagIdsByProductId.getOrDefault(productId, List.of())
                    .forEach(hashtagId -> hashtagScores.merge(hashtagId, score, Double::sum));
        });

        return hashtagScores;
    }
}
