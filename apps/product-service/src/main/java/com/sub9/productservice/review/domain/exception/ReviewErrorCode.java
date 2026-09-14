package com.sub9.productservice.review.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {
  REVIEW_ALREADY_EXISTS("REVIEW_0001", HttpStatus.CONFLICT, "이미 작성한 리뷰가 존재합니다."),
  PRODUCT_NOT_PURCHASED("REVIEW_0002", HttpStatus.FORBIDDEN, "구매한 상품만 리뷰를 작성할 수 있습니다."),
  INVALID_RATTING("REVIEW_0003", HttpStatus.BAD_REQUEST, "평점은 1점 이상 5점 이하로 입력해야합니다."),
  REVIEW_NOT_FOUND("REVIEW_0004", HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다.");

  private final String code;
  private final HttpStatus status;
  private final String message;

  @Override
  public String code() {
    return code;
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String message() {
    return message;
  }
}
