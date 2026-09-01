package com.sub9.productservice.product.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
  PRODUCT_NOT_FOUND("PRODUCT_0001", HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),

  SKU_REQUIRED("SKU_0001", HttpStatus.BAD_REQUEST, "상품에는 최소 하나의 옵션이 필요합니다."),
  INVALID_DEFAULT_SKU_COUNT("SKU_0002", HttpStatus.BAD_REQUEST, "대표 옵션은 하나여야 합니다."),
  INVALID_SKU_PRICE("SKU_0003", HttpStatus.BAD_REQUEST, "상품 가격은 0원 이상이어야 합니다."),

  INVALID_STOCK_QUANTITY("STOCK_0001", HttpStatus.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");

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
