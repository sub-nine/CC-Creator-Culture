package com.sub9.productservice.review.presentaion.query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.review.application.port.in.ReviewQueryUseCase;
import com.sub9.productservice.review.application.query.dto.ReviewInfo;
import com.sub9.productservice.support.AbstractControllerTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ReviewQueryController.class)
@DisplayName("ReviewQueryController - 단위 테스트")
class ReviewQueryControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ReviewQueryUseCase reviewQueryUseCase;

  private final UUID productId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID reviewId = UUID.randomUUID();

  @Test
  @DisplayName("상품 리뷰 목록과 다음 페이지 여부를 반환한다")
  void getReviews_success() throws Exception {
    // given
    Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    ReviewInfo info =
        new ReviewInfo(
            reviewId, productId, userId, 5, "좋아요", Instant.parse("2026-01-01T00:00:00Z"));

    given(reviewQueryUseCase.getReviews(eq(productId), any()))
        .willReturn(new SliceImpl<>(List.of(info), pageable, true));

    // when % then
    mockMvc
        .perform(get("/api/v1/products/{productId}/reviews", productId).param("pageNum", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].reviewId").value(reviewId.toString()))
        .andExpect(jsonPath("$.data.content[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.data.content[0].userId").value(userId.toString()))
        .andExpect(jsonPath("$.data.content[0].rating").value(5))
        .andExpect(jsonPath("$.data.content[0].content").value("좋아요"))
        .andExpect(jsonPath("$.data.pageNumber").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(true));
    verify(reviewQueryUseCase).getReviews(productId, pageable);
  }

  @Test
  @DisplayName("자신의 리뷰 목록을 조회하고 없는 경우 빈 리스트를 반환한다.")
  void getMyReviews_success_when_empty() throws Exception {
    // given
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

    given(reviewQueryUseCase.getMyReviews(eq(userId), any()))
        .willReturn(new SliceImpl<>(List.of(), pageable, false));

    // when % then
    mockMvc
        .perform(
            get("/api/v1/reviews/me")
                .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.hasNext").value(false));
    verify(reviewQueryUseCase).getMyReviews(userId, pageable);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/reviews/me",
        "/api/v1/products/550e8400-e29b-41d4-a716-446655440000/reviews"
      })
  @DisplayName("음수 페이지로 조회하면 400을 반환한다")
  void getReviews_fails_when_page_negative(String endpoint) throws Exception {
    // when % then
    mockMvc
        .perform(
            get(endpoint)
                .param("pageNum", "-1")
                .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMER"))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(reviewQueryUseCase);
  }
}
