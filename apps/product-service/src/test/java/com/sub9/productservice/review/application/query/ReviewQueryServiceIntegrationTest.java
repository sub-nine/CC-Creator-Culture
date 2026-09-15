package com.sub9.productservice.review.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.review.domain.model.Review;
import com.sub9.productservice.review.infrastructure.persistence.command.ReviewJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ReviewQueryService - 통합 테스트")
class ReviewQueryServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired ReviewQueryService reviewQueryService;
  @Autowired ReviewJpaRepository reviewRepository;
  @Autowired EntityManager entityManager;

  private final UUID productId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  private Review first;
  private Review second;
  private Review otherProduct;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(userId, "CUSTOMER"));

    first = saveReview(productId, userId, 1, false);
    second = saveReview(productId, UUID.randomUUID(), 2, false);
    otherProduct = saveReview(UUID.randomUUID(), userId, 3, false);

    saveReview(productId, userId, 4, true);

    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("상품의 리뷰를 최신순으로 조회하고 다음 페이지를 반환한다")
  void getReviews_success() {
    var page = reviewQueryService.getReviews(productId, PageRequest.of(0, 1));

    assertThat(page.getContent()).extracting("reviewId").containsExactly(second.getId());
    assertThat(page.hasNext()).isTrue();

    var last = reviewQueryService.getReviews(productId, PageRequest.of(1, 1));

    assertThat(last.getContent()).extracting("reviewId").containsExactly(first.getId());
    assertThat(last.hasNext()).isFalse();
    assertThat(reviewQueryService.getReviews(productId, PageRequest.of(2, 1))).isEmpty();
  }

  @Test
  @DisplayName("자신의 리뷰 목록 조회에 성공한다.")
  void getMyReviews_success() {
    var page = reviewQueryService.getMyReviews(userId, PageRequest.of(0, 10));

    assertThat(page.getContent())
        .extracting("reviewId")
        .containsExactly(otherProduct.getId(), first.getId());
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  @DisplayName("등록된 리뷰가 없으면 빈 목록을 반환한다")
  void getReviews_success_when_empty() {
    assertThat(reviewQueryService.getReviews(UUID.randomUUID(), PageRequest.of(0, 10))).isEmpty();
    assertThat(reviewQueryService.getMyReviews(UUID.randomUUID(), PageRequest.of(0, 10))).isEmpty();
  }

  private Review saveReview(UUID productId, UUID authorId, int second, boolean deleted) {
    Review review = Review.create(UUID.randomUUID(), productId, authorId, 5, "리뷰 내용");

    if (deleted) review.delete(authorId);

    reviewRepository.saveAndFlush(review);

    entityManager
        .createNativeQuery("update p_reviews set created_at = :createdAt where id = :id")
        .setParameter("createdAt", Instant.parse("2026-01-01T00:00:00Z").plusSeconds(second))
        .setParameter("id", review.getId())
        .executeUpdate();
    return review;
  }
}
