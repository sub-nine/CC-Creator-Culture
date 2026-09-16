package com.sub9.productservice.product.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum SkuErrorCode implements ErrorCode {
  SKU_REQUIRED("SKU_0001", HttpStatus.BAD_REQUEST, "상품에는 최소 하나의 옵션이 필요합니다."),
  INVALID_DEFAULT_SKU_COUNT("SKU_0002", HttpStatus.BAD_REQUEST, "대표 옵션은 하나여야 합니다."),
  INVALID_SKU_PRICE("SKU_0003", HttpStatus.BAD_REQUEST, "상품 가격은 0원 이상이어야 합니다."),
  SKU_NOT_FOUND("SKU_0004", HttpStatus.NOT_FOUND, "존재하지 않는 옵션입니다."),
  DEFAULT_SKU_CANNOT_DELETED("SKU_0005", HttpStatus.BAD_REQUEST, "대표 옵션은 삭제할 수 없습니다."),
  DEFAULT_SKU_CANNOT_UNSET("SKU_0006", HttpStatus.BAD_REQUEST, "대표 옵션은 대표 설정을 직접 해제할 수 없습니다."),
  DEFAULT_SKU_NOT_FOUND("SKU_0007", HttpStatus.NOT_FOUND, "대표 옵션이 존재하지 않습니다."),
  SKU_SOLD_OUT("SKU_0008", HttpStatus.CONFLICT, "품절된 옵션입니다.");

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
