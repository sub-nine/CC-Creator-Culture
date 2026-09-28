package com.sub9.productservice.review.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
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
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired EntityManager entityManager;
  @MockitoBean ReviewOrderQueryPort reviewOrderQueryPort;

  private final UUID userId = UUID.randomUUID();
  private final UUID orderItemId = UUID.randomUUID();
  private Product product;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(userId, "CUSTOMER"));
    product = productRepository.save(Product.create(UUID.randomUUID(), "상품", "설명"));
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
        .willReturn(new ProductPurchaseInfo(product.getId(), true));
    // when
    UUID reviewId =
        reviewCommandService.createReview(new CreateReviewCommand(userId, orderItemId, 5, "좋아요"));

    entityManager.flush();
    entityManager.clear();

    // then
    Review saved = reviewRepository.findById(reviewId).orElseThrow();

    assertThat(saved.getProductId()).isEqualTo(product.getId());
    assertThat(saved.getOrderItemId()).isEqualTo(orderItemId);
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getRating()).isEqualTo(5);
    assertThat(saved.getContent()).isEqualTo("좋아요");
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getDeletedAt()).isNull();

    Product savedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(savedProduct.getReviewCount()).isEqualTo(1);
    assertThat(savedProduct.getRatingSum()).isEqualTo(5);
  }

  @Test
  @DisplayName("리뷰 수정에 성공한다.")
  void updateReview_success() {
    // given
    Review dummyReview =
        reviewRepository.save(Review.create(orderItemId, product.getId(), userId, 5, "원본"));
    productRepository.addReviewStats(product.getId(), 5);

    entityManager.flush();
    entityManager.clear();

    // when
    reviewCommandService.updateReview(
        new UpdateReviewCommand(userId, dummyReview.getId(), 3, "수정"));

    entityManager.flush();
    entityManager.clear();

    // then
    Review saved = reviewRepository.findById(dummyReview.getId()).orElseThrow();

    assertThat(saved.getRating()).isEqualTo(3);
    assertThat(saved.getContent()).isEqualTo("수정");
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getProductId()).isEqualTo(product.getId());
    assertThat(saved.getOrderItemId()).isEqualTo(orderItemId);

    Product savedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(savedProduct.getReviewCount()).isEqualTo(1);
    assertThat(savedProduct.getRatingSum()).isEqualTo(3);
  }

  @Test
  @DisplayName("리뷰 삭제 시 리뷰를 다시 작성 할 수 있다")
  void deleteReview_success_and_allows_recreation() {
    // given
    Review dummyReview =
        reviewRepository.save(Review.create(orderItemId, product.getId(), userId, 5, "원본"));
    productRepository.addReviewStats(product.getId(), 5);

    // when
    reviewCommandService.deleteReview(new DeleteReviewCommand(userId, dummyReview.getId()));

    entityManager.flush();
    entityManager.clear();

    Review deleted = reviewRepository.findById(dummyReview.getId()).orElseThrow();

    assertThat(deleted.getDeletedAt()).isNotNull();
    assertThat(deleted.getDeletedBy()).isEqualTo(userId);
    assertThat(reviewRepository.findByIdAndUserIdAndDeletedAtIsNull(deleted.getId(), userId))
        .isEmpty();

    Product deletedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(deletedProduct.getReviewCount()).isZero();
    assertThat(deletedProduct.getRatingSum()).isZero();

    given(reviewOrderQueryPort.getPurchaseInfo(userId, orderItemId))
        .willReturn(new ProductPurchaseInfo(product.getId(), true));

    UUID recreatedId =
        reviewCommandService.createReview(new CreateReviewCommand(userId, orderItemId, 4, "재작성"));

    entityManager.flush();
    entityManager.clear();

    // then
    assertThat(recreatedId).isNotEqualTo(deleted.getId());
    assertThat(reviewRepository.findById(recreatedId).orElseThrow().getDeletedAt()).isNull();

    Product recreatedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(recreatedProduct.getReviewCount()).isEqualTo(1);
    assertThat(recreatedProduct.getRatingSum()).isEqualTo(4);
  }
}
