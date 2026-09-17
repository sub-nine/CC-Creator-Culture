package com.sub9.productservice.product.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum StockErrorCode implements ErrorCode {
  INVALID_STOCK_QUANTITY("STOCK_0001", HttpStatus.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다."),
  INSUFFICIENT_STOCK("STOCK_0002", HttpStatus.CONFLICT, "재고가 부족합니다."),
  INVALID_STOCK_ADJUSTMENT("STOCK_0003", HttpStatus.BAD_REQUEST, "재고 변경 수량은 0일 수 없습니다."),
  STOCK_NOT_FOUND("STOCK_0004", HttpStatus.NOT_FOUND, "존재하지 않는 재고입니다.");

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
