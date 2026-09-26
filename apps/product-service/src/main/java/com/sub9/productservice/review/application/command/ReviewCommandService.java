package com.sub9.productservice.review.application.command;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.review.application.command.dto.CreateReviewCommand;
import com.sub9.productservice.review.application.command.dto.DeleteReviewCommand;
import com.sub9.productservice.review.application.command.dto.UpdateReviewCommand;
import com.sub9.productservice.review.application.port.in.ReviewCommandUseCase;
import com.sub9.productservice.review.application.port.out.ReviewOrderQueryPort;
import com.sub9.productservice.review.application.port.out.ReviewProductPort;
import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;
import com.sub9.productservice.review.domain.exception.ReviewErrorCode;
import com.sub9.productservice.review.domain.model.Review;
import com.sub9.productservice.review.domain.repository.ReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewCommandService implements ReviewCommandUseCase {
  private final ReviewOrderQueryPort reviewOrderQueryPort;
  private final ReviewProductPort reviewProductPort;
  private final ReviewRepository reviewRepository;

  @Override
  public UUID createReview(CreateReviewCommand command) {
    if (reviewRepository.existsByOrderItemIdAndDeletedAtIsNull(command.orderItemId())) {
      throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
    }

    ProductPurchaseInfo productPurchaseInfo =
        reviewOrderQueryPort.getPurchaseInfo(command.userId(), command.orderItemId());

    if (!productPurchaseInfo.purchased()) {
      throw new BusinessException(ReviewErrorCode.PRODUCT_NOT_PURCHASED);
    }

    Review review =
        Review.create(
            command.orderItemId(),
            productPurchaseInfo.productId(),
            command.userId(),
            command.rating(),
            command.content());

    try {
      reviewProductPort.addReviewStats(productPurchaseInfo.productId(), command.rating());
      return reviewRepository.save(review).getId();
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
    }
  }

  @Override
  public void updateReview(UpdateReviewCommand command) {
    Review review = getReview(command.reviewId(), command.userId());
    reviewProductPort.updateReviewRating(
        review.getProductId(), review.getRating(), command.rating());
    review.update(command.rating(), command.content());
  }

  @Override
  public void deleteReview(DeleteReviewCommand command) {
    Review review = getReview(command.reviewId(), command.userId());
    reviewProductPort.removeReviewStats(review.getProductId(), review.getRating());
    review.delete(command.userId());
  }

  // ============================== Helper Method ====================================
  private Review getReview(UUID reviewId, UUID userId) {
    return reviewRepository
        .findByIdAndUserIdAndDeletedAtIsNull(reviewId, userId)
        .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
  }
}
