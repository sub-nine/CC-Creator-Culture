package com.sub9.productservice.review.application.command;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.review.application.command.dto.CreateReviewCommand;
import com.sub9.productservice.review.application.port.in.ReviewCommandUserCase;
import com.sub9.productservice.review.application.port.out.ReviewOrderQueryPort;
import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;
import com.sub9.productservice.review.domain.exception.ReviewErrorCode;
import com.sub9.productservice.review.domain.model.Review;
import com.sub9.productservice.review.domain.repository.ReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewCommandService implements ReviewCommandUserCase {
  private final ReviewRepository reviewRepository;
  private final ReviewOrderQueryPort reviewOrderQueryPort;

  @Override
  public UUID createReview(CreateReviewCommand command) {
    if (reviewRepository.existsByOrderItemIdAndDeletedAtIsNull(command.orderItemId())) {
      throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
    }

    ProductPurchaseInfo info =
        reviewOrderQueryPort.hasPurchasedProduct(command.userId(), command.orderItemId());

    if (!info.purchased()) {
      throw new BusinessException(ReviewErrorCode.PRODUCT_NOT_PURCHASED);
    }

    Review review =
        Review.create(
            command.orderItemId(),
            info.productId(),
            command.userId(),
            command.rating(),
            command.content());

    // TODO : 리뷰 Product 테이블에 반영, 목록 읽기 모델 반영 시 함꼐 작업 예정

    return reviewRepository.save(review).getId();
  }
}
