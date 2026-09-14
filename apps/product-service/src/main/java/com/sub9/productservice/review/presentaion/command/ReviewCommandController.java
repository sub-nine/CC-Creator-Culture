package com.sub9.productservice.review.presentaion.command;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.review.application.port.in.ReviewCommandUserCase;
import com.sub9.productservice.review.presentaion.command.dto.CreateReviewRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewCommandController {
  private final ReviewCommandUserCase reviewCommandUserCase;

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<UUID> createReview(
      @AuthenticationPrincipal(expression = "id()") UUID userId,
      @Valid @RequestBody CreateReviewRequest request) {
    UUID reviewId = reviewCommandUserCase.createReview(request.toCommand(userId));
    return ApiResponse.success("리뷰 등록에 성공했습니다.", reviewId);
  }

  // @PatchMapping("/reviews/{reviewId}")


  // @DeleteMapping("/review/{reviewId}")
}
