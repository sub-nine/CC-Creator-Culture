package com.sub9.productservice.review.presentaion.query;

import com.sub9.common.annotation.Customer;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.dto.response.PageResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.review.application.port.in.ReviewQueryUseCase;
import com.sub9.productservice.review.presentaion.query.dto.ReviewResponse;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReviewQueryController {
  private final ReviewQueryUseCase reviewQueryUseCase;

  // TODO : 사용자 명 출력은 User-Serivce에서 내부 통신 API 구현 완료 시 추가

  @GetMapping("/products/{productId}/reviews")
  public ApiResponse<PageResponse<ReviewResponse>> getReviews(
      @PathVariable UUID productId, @RequestParam(defaultValue = "0") @Min(0) int pageNum) {
    Pageable pageable =
        PageRequest.of(pageNum, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

    Slice<ReviewResponse> response =
        reviewQueryUseCase.getReviews(productId, pageable).map(ReviewResponse::from);

    return ApiResponse.success("리뷰 목록 조회 성공", PageResponse.of(response));
  }

  @Customer
  @GetMapping("/reviews/me")
  public ApiResponse<PageResponse<ReviewResponse>> getMyReviews(
      @AuthenticationPrincipal AuthUser authUser,
      @RequestParam(defaultValue = "0") @Min(0) int pageNum) {
    Pageable pageable =
        PageRequest.of(pageNum, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

    Slice<ReviewResponse> response =
        reviewQueryUseCase.getMyReviews(authUser.id(), pageable).map(ReviewResponse::from);

    return ApiResponse.success("리뷰 목록 조회 성공", PageResponse.of(response));
  }
}
