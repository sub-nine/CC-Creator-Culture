package com.sub9.orderservice.cart.application.port.in;

import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.presentation.response.CartItemResponse;
import java.util.List;
import java.util.UUID;

public interface CartQueryUseCase {
  /**
   * 회원의 장바구니 목록을 상품 정보와 함께 조회
   * 판매 종료 상태거나 삭제된 상품은 결과에서 제외한다.
   *
   * @return 장바구니 상품 목록
   */
  List<CartItemResponse> getCart(UUID userId);

  /**
   * 주문 생성을 위해 장바구니 선택 항목을 상품 정보와 함께 조회
   * 장바구니 항목이 존재하지 않거나 상품 정보 조회 불가시 예외 처리
   *
   * @return 주문에 사용될 장바구니 상품 정보 목록
   */
  List<CartItemInfo> getCartItems(UUID customerId, List<UUID> cartItemIds);
}
