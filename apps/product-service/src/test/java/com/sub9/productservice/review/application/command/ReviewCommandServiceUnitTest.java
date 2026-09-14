package com.sub9.productservice.review.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.review.application.command.dto.*;
import com.sub9.productservice.review.application.port.out.ReviewOrderQueryPort;
import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;
import com.sub9.productservice.review.domain.exception.ReviewErrorCode;
import com.sub9.productservice.review.domain.model.Review;
import com.sub9.productservice.review.domain.repository.ReviewRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewCommandService - 단위 테스트")
class ReviewCommandServiceUnitTest {
  @Mock ReviewRepository reviewRepository;
  @Mock ReviewOrderQueryPort reviewOrderQueryPort;
  @InjectMocks ReviewCommandService reviewCommandService;

  private final UUID userId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID orderItemId = UUID.randomUUID();
  private final UUID reviewId = UUID.randomUUID();

  @Test
  @DisplayName("리뷰가 존재하면 중복 등록을 거절한다")
  void createReview_fails_when_review_already_exists() {
    // given
    given(reviewRepository.existsByOrderItemIdAndDeletedAtIsNull(orderItemId)).willReturn(true);

    // when & then
    assertThatThrownBy(
            () ->
                reviewCommandService.createReview(
                    new CreateReviewCommand(userId, orderItemId, 5, "좋아요")))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ReviewErrorCode.REVIEW_ALREADY_EXISTS.message());
    verify(reviewRepository, never()).save(any());
  }

  @Test
  @DisplayName("주문 이력이 없으면 PRODUCT_NOT_PURCHASED 예외가 발생해야한다.")
  void createReview_fails_when_product_not_purchased() {
    // given
    given(reviewOrderQueryPort.getPurchaseInfo(userId, orderItemId))
        .willReturn(new ProductPurchaseInfo(productId, false));

    // when & then
    assertThatThrownBy(
            () ->
                reviewCommandService.createReview(
                    new CreateReviewCommand(userId, orderItemId, 5, "좋아요")))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ReviewErrorCode.PRODUCT_NOT_PURCHASED.message());
    verify(reviewRepository, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 6})
  @DisplayName("평점 범위를 벗어나면 INVALID_RATTING 예외가 발생해야한다.")
  void createReview_fails_when_rating_invalid(int rating) {
    // given
    given(reviewOrderQueryPort.getPurchaseInfo(userId, orderItemId))
        .willReturn(new ProductPurchaseInfo(productId, true));

    // when & then
    assertThatThrownBy(
            () ->
                reviewCommandService.createReview(
                    new CreateReviewCommand(userId, orderItemId, rating, "좋아요")))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ReviewErrorCode.INVALID_RATTING.message());
    verify(reviewRepository, never()).save(any());
  }

  @Test
  @DisplayName("본인의 리뷰가 없으면 수정을 거절한다")
  void updateReview_fails_when_review_not_found() {
    // given
    given(reviewRepository.findByIdAndUserIdAndDeletedAtIsNull(reviewId, userId))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(
            () ->
                reviewCommandService.updateReview(
                    new UpdateReviewCommand(userId, reviewId, 4, "수정")))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ReviewErrorCode.REVIEW_NOT_FOUND.message());
  }

  @Test
  @DisplayName("본인의 리뷰가 없으면 삭제를 거절한다")
  void deleteReview_fails_when_review_not_found() {
    // given
    given(reviewRepository.findByIdAndUserIdAndDeletedAtIsNull(reviewId, userId))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(
            () -> reviewCommandService.deleteReview(new DeleteReviewCommand(userId, reviewId)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ReviewErrorCode.REVIEW_NOT_FOUND.message());
  }
}
