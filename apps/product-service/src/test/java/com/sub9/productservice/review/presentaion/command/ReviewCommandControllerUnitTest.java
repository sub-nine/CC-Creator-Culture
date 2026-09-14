package com.sub9.productservice.review.presentaion.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.review.application.command.dto.*;
import com.sub9.productservice.review.application.port.in.ReviewCommandUseCase;
import com.sub9.productservice.review.domain.exception.ReviewErrorCode;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ReviewCommandController.class)
@DisplayName("ReviewCommandController - 단위 테스트")
class ReviewCommandControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ReviewCommandUseCase reviewCommandUseCase;

  private final UUID userId = UUID.randomUUID();
  private final UUID orderItemId = UUID.randomUUID();
  private final UUID reviewId = UUID.randomUUID();
  private final String endPoint = "/api/v1/reviews";

  private RequestPostProcessor authUser() {
    return authentication(CustomAuthenticationToken.of(userId, "CUSTOMER"));
  }

  @Nested
  @DisplayName("리뷰 등록 테스트")
  class CreateReview {
    @Test
    @DisplayName("작성자의 리뷰를 등록하고 리뷰 ID와 201을 반환한다")
    void createReview_success() throws Exception {
      // given
      given(reviewCommandUseCase.createReview(any())).willReturn(reviewId);

      // when % then
      mockMvc
          .perform(
              post(endPoint)
                  .with(authUser())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      jsonMapper.writeValueAsBytes(
                          Map.of("orderItemId", orderItemId, "rating", 5, "content", "좋아요"))))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.data").value(reviewId.toString()));
      verify(reviewCommandUseCase)
          .createReview(new CreateReviewCommand(userId, orderItemId, 5, "좋아요"));
    }

    @ParameterizedTest
    @EnumSource(
        value = ReviewErrorCode.class,
        names = {"REVIEW_ALREADY_EXISTS", "PRODUCT_NOT_PURCHASED"})
    @DisplayName("중복 등록이나 구매 하지 않는 사용자의 경우 에러를 반환한다")
    void createReview_fails_when_business_error(ReviewErrorCode errorCode) throws Exception {
      // given
      given(reviewCommandUseCase.createReview(any())).willThrow(new BusinessException(errorCode));

      // when & then
      mockMvc
          .perform(
              post(endPoint)
                  .with(authUser())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      jsonMapper.writeValueAsBytes(
                          Map.of("orderItemId", orderItemId, "rating", 5))))
          .andExpect(status().is(errorCode.status().value()))
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }

    @Test
    @DisplayName("주문 항목 ID가 없으면 400을 반환한다")
    void createReview_fails_when_order_item_missing() throws Exception {
      // when & then
      mockMvc
          .perform(
              post(endPoint)
                  .with(authUser())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":5}"))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(reviewCommandUseCase);
    }
  }

  @Nested
  @DisplayName("리뷰 수정 테스트")
  class UpdateReview {
    @Test
    @DisplayName("리뷰 수정에 성공하면 200을 반환한다")
    void updateReview_success() throws Exception {
      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{reviewId}", reviewId)
                  .with(authUser())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":4,\"content\":\"수정\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("리뷰 수정에 성공했습니다."));
      verify(reviewCommandUseCase).updateReview(new UpdateReviewCommand(userId, reviewId, 4, "수정"));
    }

    @Test
    @DisplayName("수정할 본인 리뷰가 없으면 404를 반환한다")
    void updateReview_fails_when_review_not_found() throws Exception {
      // given
      willThrow(new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND))
          .given(reviewCommandUseCase)
          .updateReview(any());

      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{reviewId}", reviewId)
                  .with(authUser())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rating\":4}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value("REVIEW_0004"));
    }
  }

  @Nested
  @DisplayName("리뷰 삭제 테스트")
  class DeleteReview {
    @Test
    @DisplayName("리뷰 삭제에 성공하면 200을 반환한다")
    void deleteReview_success() throws Exception {
      // when & then
      mockMvc
          .perform(delete(endPoint + "/{reviewId}", reviewId).with(authUser()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("리뷰 삭제에 성공했습니다."));
      verify(reviewCommandUseCase).deleteReview(new DeleteReviewCommand(userId, reviewId));
    }

    @Test
    @DisplayName("삭제할 리뷰가 없으면 404를 반환한다")
    void deleteReview_fails_when_review_not_found() throws Exception {
      // given
      willThrow(new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND))
          .given(reviewCommandUseCase)
          .deleteReview(any());

      // when & then
      mockMvc
          .perform(delete(endPoint + "/{reviewId}", reviewId).with(authUser()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value("REVIEW_0004"));
    }
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("invalidRequests")
  @DisplayName("등록이나 수정 시 수정 요청의 평점과 내용이 유효하지 않으면 400을 반환한다")
  void writeReview_fails_when_request_invalid(String method, int rating, String content)
      throws Exception {
    // given
    var request =
        method.equals("POST") ? post(endPoint) : patch(endPoint + "/{reviewId}", reviewId);

    // when & then
    mockMvc
        .perform(
            request
                .with(authUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsBytes(
                        Map.of("orderItemId", orderItemId, "rating", rating, "content", content))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(reviewCommandUseCase);
  }
}
