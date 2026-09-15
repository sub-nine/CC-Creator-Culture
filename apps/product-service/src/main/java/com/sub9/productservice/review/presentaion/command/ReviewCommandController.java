package com.sub9.productservice.review.presentaion.command;

import com.sub9.common.annotation.Customer;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.review.application.command.dto.DeleteReviewCommand;
import com.sub9.productservice.review.application.port.in.ReviewCommandUseCase;
import com.sub9.productservice.review.presentaion.command.dto.CreateReviewRequest;
import com.sub9.productservice.review.presentaion.command.dto.UpdateReviewRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Customer
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewCommandController {
  private final ReviewCommandUseCase reviewCommandUseCase;

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<UUID> createReview(
      @AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody CreateReviewRequest request) {
    UUID reviewId = reviewCommandUseCase.createReview(request.toCommand(authUser.id()));
    return ApiResponse.success("리뷰 등록에 성공했습니다.", reviewId);
  }

  @PatchMapping("/{reviewId}")
  public ApiResponse<Void> updateReview(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID reviewId,
      @Valid @RequestBody UpdateReviewRequest request) {
    reviewCommandUseCase.updateReview(request.toCommand(authUser.id(), reviewId));
    return ApiResponse.success("리뷰 수정에 성공했습니다.", null);
  }

  @DeleteMapping("/{reviewId}")
  public ApiResponse<Void> deleteReview(
      @AuthenticationPrincipal AuthUser authUser, @PathVariable UUID reviewId) {
    reviewCommandUseCase.deleteReview(new DeleteReviewCommand(authUser.id(), reviewId));
    return ApiResponse.success("리뷰 삭제에 성공했습니다.", null);
  }
}
