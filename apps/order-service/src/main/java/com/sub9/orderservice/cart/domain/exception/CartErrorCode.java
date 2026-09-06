package com.sub9.orderservice.cart.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CartErrorCode implements ErrorCode {
  CART_ITEM_NOT_FOUND("CART_0001", HttpStatus.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다."),
  CART_ITEM_LIMIT_EXCEEDED("CART_0002", HttpStatus.BAD_REQUEST, "장바구니에 더 이상 상품을 추가할 수 없습니다."),
  CART_ITEM_ALREADY_EXISTS("CART_0003", HttpStatus.BAD_REQUEST, "이미 장바구니에 등록된 상품입니다.");

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
