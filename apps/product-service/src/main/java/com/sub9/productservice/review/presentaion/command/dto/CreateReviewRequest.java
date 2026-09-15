package com.sub9.productservice.review.presentaion.command.dto;

import com.sub9.productservice.review.application.command.dto.CreateReviewCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateReviewRequest(
    @NotNull(message = "주문 항목 ID를 입력해주세요.") UUID orderItemId,
    @Min(value = 1, message = "평점은 최소 1점 이상이어야 합니다.")
        @Max(value = 5, message = "평점은 최대 5점 이하이어야 합니다.")
        int rating,
    @Size(max = 1000, message = "리뷰 내용은 1000자를 초과할 수 없습니다.") String content) {
  public CreateReviewCommand toCommand(UUID userId) {
    return new CreateReviewCommand(userId, orderItemId, rating, content);
  }
}
