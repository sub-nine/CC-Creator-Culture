package com.sub9.productservice.review.application.port.in;

import com.sub9.productservice.review.application.command.dto.CreateReviewCommand;

import java.util.UUID;

public interface ReviewCommandUserCase {
  /**
   * 사용자가 구매한 상품에 대한 리뷰를 등록한다.
   * 리뷰는 하나의 주문 상품에 대해 한건의 리뷰만 등록 가능하다.
   *
   * @return 생성된 리뷰 Id
   */
  UUID createReview(CreateReviewCommand command);
}
