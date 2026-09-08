package com.sub9.orderservice.cart.infrastructure.client.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CartProductClientErrorCode implements ErrorCode {
  INVALID_CART_PRODUCT("CART_0001", HttpStatus.BAD_REQUEST, "유효하지 않은 상품입니다.");

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
