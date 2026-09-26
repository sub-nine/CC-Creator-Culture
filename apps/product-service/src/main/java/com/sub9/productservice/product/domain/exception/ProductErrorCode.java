package com.sub9.productservice.product.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
  PRODUCT_NOT_FOUND("PRODUCT_0001", HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
  PRODUCT_ACCESS_DENIED("PRODUCT_0002", HttpStatus.FORBIDDEN, "상품에 대한 접근 권한이 없습니다."),
  INVALID_PRODUCT_STATUS_TRANSITION(
      "PRODUCT_0003", HttpStatus.BAD_REQUEST, "유효하지 않은 상품 상태 변경 요청입니다."),
  PRODUCT_NOT_FOR_SALE("PRODUCT_004", HttpStatus.BAD_REQUEST, "현재 판매 중인 상품이 아닙니다."),
  PRODUCT_SOLD_OUT("PRODUCT_0005", HttpStatus.CONFLICT, "품절된 상품입니다."),
  PRODUCT_IMAGE_NOT_FOUND("PRODUCT_006", HttpStatus.NOT_FOUND, "유효하지 않은 상품 이미지 입니다."),
  INVALID_PRODUCT_IMAGE_INFO("PRODUCT_0007", HttpStatus.BAD_REQUEST, "요청한 상품 이미지 정보가 올바르지 않습니다"),
  IMAGE_UPLOAD_NOT_FOUND("PRODUCT_0008", HttpStatus.NOT_FOUND, "업로드된 이미지를 찾을 수 없습니다.");

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
