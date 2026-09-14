package com.sub9.productservice.wishlist.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum WishlistErrorCode implements ErrorCode {
  PRODUCT_NOT_AVAILABLE("WISHLIST_0001", HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
  WISHLIST_NOT_FOUND("WISHLIST_0002", HttpStatus.NOT_FOUND, "관심상품을 찾을 수 없습니다.");

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
