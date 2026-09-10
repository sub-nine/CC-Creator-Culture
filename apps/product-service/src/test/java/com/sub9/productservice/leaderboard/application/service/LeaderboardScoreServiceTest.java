package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.category.presentation.query.dto.ProductCategoryIdsResponse;
import com.sub9.productservice.category.presentation.query.dto.ProductHashtagIdsResponse;
import com.sub9.productservice.leaderboard.application.model.ProductQuantity;
import com.sub9.productservice.leaderboard.application.model.ProductViewCount;
import com.sub9.productservice.leaderboard.application.port.out.CategoryQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.HashtagQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardEventType;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardScore;
import com.sub9.productservice.leaderboard.domain.model.SourceScore;
import com.sub9.productservice.leaderboard.domain.service.LeaderboardScoreDistributionDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaderboardScoreService 단위 테스트")
class LeaderboardScoreServiceTest {

    @Mock
    private CategoryQueryPort categoryQueryPort;
    @Mock
    private HashtagQueryPort hashtagQueryPort;
    @Mock
    private RedisRepository redisRepository;
    @Mock
    private LeaderboardScoreDistributionDomainService leaderboardScoreDistributionDomainService;

    private LeaderboardScoreService leaderboardScoreService;

    @BeforeEach
    void setUp() {
        leaderboardScoreService = new LeaderboardScoreService(
                categoryQueryPort, hashtagQueryPort, redisRepository, leaderboardScoreDistributionDomainService);
    }

    @Nested
    @DisplayName("recordOrderScore()")
    class RecordOrderScore {

        @Test
        @DisplayName("주문 상품들의 카테고리/해시태그 점수를 계산해 ORDER_PAID 가중치로 Redis에 반영한다")
        void recordOrderScore_success() {
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID categoryId = UUID.randomUUID();
            UUID hashtagId = UUID.randomUUID();

            List<ProductQuantity> productQuantities = List.of(new ProductQuantity(productId, 3L));
            List<SourceScore> expectedSourceScores = List.of(new SourceScore(productId, 3L));

            when(categoryQueryPort.getCategoryIdsByProductIds(List.of(productId)))
                    .thenReturn(List.of(new ProductCategoryIdsResponse(productId, List.of(categoryId))));
            when(hashtagQueryPort.getHashtagIdsByProductIds(List.of(productId)))
                    .thenReturn(List.of(new ProductHashtagIdsResponse(productId, List.of(hashtagId))));

            List<LeaderboardScore> categoryScores = List.of(new LeaderboardScore(categoryId, 4.5));
            List<LeaderboardScore> hashtagScores = List.of(new LeaderboardScore(hashtagId, 4.5));

            when(leaderboardScoreDistributionDomainService.distribute(
                    eq(expectedSourceScores), eq(LeaderboardEventType.ORDER_PAID.getWeight()), any()))
                    .thenReturn(categoryScores, hashtagScores);

            leaderboardScoreService.recordOrderScore(orderId, productQuantities);

            verify(redisRepository).incrementScoresIfNotProcessed(
                    LeaderboardEventType.ORDER_PAID, orderId, categoryScores, hashtagScores);
        }

        @Test
        @DisplayName("이미 처리된 주문이면 예외 없이 스킵한다")
        void recordOrderScore_alreadyProcessed_doesNotThrow() {
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            List<ProductQuantity> productQuantities = List.of(new ProductQuantity(productId, 1L));

            when(categoryQueryPort.getCategoryIdsByProductIds(any())).thenReturn(List.of());
            when(hashtagQueryPort.getHashtagIdsByProductIds(any())).thenReturn(List.of());
            when(leaderboardScoreDistributionDomainService.distribute(any(), anyDouble(), any()))
                    .thenReturn(List.of());
            when(redisRepository.incrementScoresIfNotProcessed(any(), any(), any(), any())).thenReturn(false);

            assertThatCode(() -> leaderboardScoreService.recordOrderScore(orderId, productQuantities))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("recordProductViewScore()")
    class RecordProductViewScore {

        @Test
        @DisplayName("조회수 이벤트의 카테고리/해시태그 점수를 계산해 PRODUCT_SYNC_VIEW 가중치로 Redis에 반영한다")
        void recordProductViewScore_success() {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID categoryId = UUID.randomUUID();
            UUID hashtagId = UUID.randomUUID();

            List<ProductViewCount> productViewCounts = List.of(new ProductViewCount(productId, 10L));
            List<SourceScore> expectedSourceScores = List.of(new SourceScore(productId, 10L));

            when(categoryQueryPort.getCategoryIdsByProductIds(List.of(productId)))
                    .thenReturn(List.of(new ProductCategoryIdsResponse(productId, List.of(categoryId))));
            when(hashtagQueryPort.getHashtagIdsByProductIds(List.of(productId)))
                    .thenReturn(List.of(new ProductHashtagIdsResponse(productId, List.of(hashtagId))));

            List<LeaderboardScore> categoryScores = List.of(new LeaderboardScore(categoryId, 10.0));
            List<LeaderboardScore> hashtagScores = List.of(new LeaderboardScore(hashtagId, 10.0));

            when(leaderboardScoreDistributionDomainService.distribute(
                    eq(expectedSourceScores), eq(LeaderboardEventType.PRODUCT_SYNC_VIEW.getWeight()), any()))
                    .thenReturn(categoryScores, hashtagScores);

            leaderboardScoreService.recordProductViewScore(eventId, productViewCounts);

            verify(redisRepository).incrementScoresIfNotProcessed(
                    LeaderboardEventType.PRODUCT_SYNC_VIEW, eventId, categoryScores, hashtagScores);
        }
    }
}
