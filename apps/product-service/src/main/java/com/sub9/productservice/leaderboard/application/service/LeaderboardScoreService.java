package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.category.presentation.query.dto.ProductCategoryIdsResponse;
import com.sub9.productservice.category.presentation.query.dto.ProductHashtagIdsResponse;
import com.sub9.productservice.leaderboard.application.model.ProductQuantity;
import com.sub9.productservice.leaderboard.application.model.ProductViewCount;
import com.sub9.productservice.leaderboard.application.port.in.RecordOrderScoreUseCase;
import com.sub9.productservice.leaderboard.application.port.in.RecordProductViewScoreUseCase;
import com.sub9.productservice.leaderboard.application.port.out.CategoryQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.HashtagQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardEventType;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardScore;
import com.sub9.productservice.leaderboard.domain.model.SourceScore;
import com.sub9.productservice.leaderboard.domain.service.LeaderboardScoreDistributionDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardScoreService implements RecordOrderScoreUseCase, RecordProductViewScoreUseCase {
    private final CategoryQueryPort categoryQueryPort;
    private final HashtagQueryPort hashtagQueryPort;
    private final RedisRepository redisRepository;
    private final LeaderboardScoreDistributionDomainService leaderboardScoreDistributionDomainService;

    @Override
    public void recordOrderScore(UUID orderId, List<ProductQuantity> productQuantities) {
        // ProductQuantity에 대한 점수 일반화
        List<SourceScore> productOrderScores = productQuantities.stream().map((quantity) ->
                new SourceScore(quantity.productId(), quantity.quantity())
        ).toList();

        // 점수 가중치 계산 및 Redis 리더보드에 저장
        boolean applied = calcScoresAndSaveToRedis(
                LeaderboardEventType.ORDER_PAID, productOrderScores, orderId
        );

        if (!applied) {
            log.info("[LEADERBOARD] 이미 처리된 주문이라 점수 반영 스킵 - orderId: {}", orderId);
        }
    }

    @Override
    public void recordProductViewScore(UUID eventId, List<ProductViewCount> productViewCounts) {
        // ProductViewCount에 대한 점수 일반화
        List<SourceScore> productViewScores = productViewCounts.stream().map((viewCount) ->
                new SourceScore(viewCount.productId(), viewCount.viewCount())
        ).toList();

        // 점수 가중치 계산 및 Redis 리더보드에 저장
        boolean applied = calcScoresAndSaveToRedis(
                LeaderboardEventType.PRODUCT_SYNC_VIEW, productViewScores, eventId
        );

        if (!applied) {
            log.info("[LEADERBOARD] 이미 처리된 조회 이벤트라 점수 반영 스킵 - eventId: {}", eventId);
        }
    }

    private boolean calcScoresAndSaveToRedis(LeaderboardEventType type, List<SourceScore> sourceScores, UUID keyId) {
        // 카테고리와 해시태그 리더보드에 반영될 Score 계산
        double weight = type.getWeight();
        List<LeaderboardScore> categoryScores = resolveCategoryScoresByTarget(sourceScores, weight);
        List<LeaderboardScore> hashtagScores = resolveHashtagScoresByTarget(sourceScores, weight);

        // 계산된 Score 멱등적으로 반영
        return redisRepository.incrementScoresIfNotProcessed(
                type, keyId, categoryScores, hashtagScores);
    }

    private List<LeaderboardScore> resolveCategoryScoresByTarget(List<SourceScore> sourceScores, double weight) {
        List<UUID> productIds = sourceScores.stream().map(SourceScore::sourceId).toList();

        // 조회된 product.id에 대한 category.id 조회
        List<ProductCategoryIdsResponse> productCategoryIds = categoryQueryPort.getCategoryIdsByProductIds(productIds);

        // product별 category.id 목록 맵핑
        Map<UUID, List<UUID>> categoryIdsByProductId = productCategoryIds.stream().collect(
                Collectors.toMap(ProductCategoryIdsResponse::productId, ProductCategoryIdsResponse::categoryIds));

        // 가중치를 적용하여 리더보드 Score로 합산하여 반환
        return leaderboardScoreDistributionDomainService.distribute(sourceScores, weight, categoryIdsByProductId);
    }

    private List<LeaderboardScore> resolveHashtagScoresByTarget(List<SourceScore> sourceScores, double weight) {
        List<UUID> productIds = sourceScores.stream().map(SourceScore::sourceId).toList();

        // 조회된 product.id에 대한 hashtag.id 조회
        List<ProductHashtagIdsResponse> productHashtagIds = hashtagQueryPort.getHashtagIdsByProductIds(productIds);

        // product별 hashtag.id 목록 맵핑
        Map<UUID, List<UUID>> hashtagIdsByProductId = productHashtagIds.stream().collect(
                Collectors.toMap(ProductHashtagIdsResponse::productId, ProductHashtagIdsResponse::hashtagIds));

        // 가중치를 적용하여 리더보드 Score로 합산하여 반환
        return leaderboardScoreDistributionDomainService.distribute(sourceScores, weight, hashtagIdsByProductId);
    }
}
