package com.sub9.orderservice.cart.application.port.in;

import java.util.UUID;

public interface CartCleanupUseCase {
  /**
   * 상품 삭제 이벤트 수신 시 해당 상품이 포함된 장바구니 항목을 삭제한다.
   *
   */
  void cleanupByProductId(UUID productId);

  /**
   * SKU 삭제 이벤트 수신 시 해당 SKU가 포함된 장바구니 항목을 삭제한다.
   * 장바구니 등록과 삭제가 동시에 이루어질 경우 Cart에 저장될 가능성 있음
   *
   * TODO : 추후 스케쥴러를 통한 삭제 항목 주기적 재정리 로직 구현 필요
   *
   */
  void cleanupBySkuId(UUID skuId);
}
