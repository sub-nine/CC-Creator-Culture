package com.sub9.productservice.review.presentaion.command;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.review.application.port.in.ReviewCommandUseCase;
import com.sub9.productservice.review.presentaion.command.dto.CreateReviewRequest;
import com.sub9.productservice.review.application.command.dto.DeleteReviewCommand;
import com.sub9.productservice.review.presentaion.command.dto.UpdateReviewRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewCommandController {
  private final ReviewCommandUseCase reviewCommandUseCase;

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<UUID> createReview(
      @AuthenticationPrincipal(expression = "id()") UUID userId,
      @Valid @RequestBody CreateReviewRequest request) {
    UUID reviewId = reviewCommandUseCase.createReview(request.toCommand(userId));
    return ApiResponse.success("리뷰 등록에 성공했습니다.", reviewId);
  }

  @PatchMapping("/{reviewId}")
  public ApiResponse<Void> updateReview(
      @AuthenticationPrincipal(expression = "id()") UUID userId,
      @PathVariable UUID reviewId,
      @Valid @RequestBody UpdateReviewRequest request) {
    reviewCommandUseCase.updateReview(request.toCommand(userId, reviewId));
    return ApiResponse.success("리뷰 수정에 성공했습니다.", null);
  }

  @DeleteMapping("/{reviewId}")
  public ApiResponse<Void> deleteReview(
      @AuthenticationPrincipal(expression = "id()") UUID userId, @PathVariable UUID reviewId) {
    reviewCommandUseCase.deleteReview(new DeleteReviewCommand(userId, reviewId));
    return ApiResponse.success("리뷰 삭제에 성공했습니다.", null);
  }
}
