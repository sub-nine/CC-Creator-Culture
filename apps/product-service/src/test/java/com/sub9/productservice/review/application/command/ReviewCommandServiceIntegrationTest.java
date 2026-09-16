package com.sub9.productservice.review.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.review.application.command.dto.*;
import com.sub9.productservice.review.application.port.out.ReviewOrderQueryPort;
import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;
import com.sub9.productservice.review.domain.model.Review;
import com.sub9.productservice.review.infrastructure.persistence.command.ReviewJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ReviewCommandService - 통합 테스트")
class ReviewCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired ReviewCommandService reviewCommandService;
  @Autowired ReviewJpaRepository reviewRepository;
  @Autowired EntityManager entityManager;
  @MockitoBean ReviewOrderQueryPort reviewOrderQueryPort;

  private final UUID userId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID orderItemId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(userId, "CUSTOMER"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("구매한 주문 상품에 대한 리뷰 등록에 성공한다.")
  void createReview_success() {
    // given
    given(reviewOrderQueryPort.getPurchaseInfo(userId, orderItemId))
        .willReturn(new ProductPurchaseInfo(productId, true));
    // when
    UUID reviewId =
        reviewCommandService.createReview(new CreateReviewCommand(userId, orderItemId, 5, "좋아요"));

    entityManager.flush();
    entityManager.clear();

    // then
    Review saved = reviewRepository.findById(reviewId).orElseThrow();

    assertThat(saved.getProductId()).isEqualTo(productId);
    assertThat(saved.getOrderItemId()).isEqualTo(orderItemId);
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getRating()).isEqualTo(5);
    assertThat(saved.getContent()).isEqualTo("좋아요");
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("리뷰 수정에 성공한다.")
  void updateReview_success() {
    Review dummyReview =
        reviewRepository.save(Review.create(orderItemId, productId, userId, 5, "원본"));

    entityManager.flush();
    entityManager.clear();

    reviewCommandService.updateReview(
        new UpdateReviewCommand(userId, dummyReview.getId(), 3, "수정"));

    entityManager.flush();
    entityManager.clear();

    Review saved = reviewRepository.findById(dummyReview.getId()).orElseThrow();

    assertThat(saved.getRating()).isEqualTo(3);
    assertThat(saved.getContent()).isEqualTo("수정");
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getProductId()).isEqualTo(productId);
    assertThat(saved.getOrderItemId()).isEqualTo(orderItemId);
  }

  @Test
  @DisplayName("리뷰 삭제 시 리뷰를 다시 작성 할 수 있다")
  void deleteReview_success_and_allows_recreation() {
    Review dummyReview =
        reviewRepository.save(Review.create(orderItemId, productId, userId, 5, "원본"));

    reviewCommandService.deleteReview(new DeleteReviewCommand(userId, dummyReview.getId()));

    entityManager.flush();
    entityManager.clear();

    Review deleted = reviewRepository.findById(dummyReview.getId()).orElseThrow();

    assertThat(deleted.getDeletedAt()).isNotNull();
    assertThat(deleted.getDeletedBy()).isEqualTo(userId);
    assertThat(reviewRepository.findByIdAndUserIdAndDeletedAtIsNull(deleted.getId(), userId))
        .isEmpty();

    given(reviewOrderQueryPort.getPurchaseInfo(userId, orderItemId))
        .willReturn(new ProductPurchaseInfo(productId, true));

    UUID recreatedId =
        reviewCommandService.createReview(new CreateReviewCommand(userId, orderItemId, 4, "재작성"));

    entityManager.flush();
    entityManager.clear();

    assertThat(recreatedId).isNotEqualTo(deleted.getId());
    assertThat(reviewRepository.findById(recreatedId).orElseThrow().getDeletedAt()).isNull();
  }
}
