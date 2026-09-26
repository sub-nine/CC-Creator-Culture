package com.sub9.productservice.review.application.port.in;

import com.sub9.productservice.review.application.command.dto.CreateReviewCommand;
import com.sub9.productservice.review.application.command.dto.UpdateReviewCommand;
import com.sub9.productservice.review.application.command.dto.DeleteReviewCommand;

import java.util.UUID;

public interface ReviewCommandUseCase {
  /**
   * 사용자가 구매한 상품에 대한 리뷰를 등록한다.
   * 주문 이력을 확인하여 리뷰 작성 가능 여부를 검증한다.
   * 리뷰는 하나의 주문 상품에 대해 한건의 리뷰만 등록 가능하다.
   *
   * @return 생성된 리뷰 Id
   */
  UUID createReview(CreateReviewCommand command);

  /**
   * 사용자가 등록한 리뷰를 수정한다.
   *
   */
  void updateReview(UpdateReviewCommand command);

  /**
   * 사용자가 등록한 리뷰를 삭제한다.
   *
   */
  void deleteReview(DeleteReviewCommand command);
}
