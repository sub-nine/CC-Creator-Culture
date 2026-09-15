package com.sub9.orderservice.cart.application.port.in;

import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import com.sub9.orderservice.cart.application.dto.UpdateCartItemCommand;

import java.util.UUID;

public interface CartCommandUseCase {
  /**
   * 회원의 장바구니에 물품을 추가한다.
   *
   * 장바구니에는 최대 70개의 물품만 담을 수 있으며
   * 상품 정보를 조회하여 장바구니에 추가 가능한 상태인지 검증한다.
   *
   * 장바구니 최대 개수에 대해서는 별도의 동시성 제어를 하지 않는다.
   *
   * @return 생성된 장바구니 아이템 ID
   */
  UUID addCartItem(AddCartItemCommand command);

  /**
   * 회원의 장바구니 상품 수량을 수정한다.
   *
   * 장바구니에서 변경할 물품의 수량(절대값)을 입력 받아 기존 수량을 수정한다.
   * 실제 재고 수량이 아닌 사용자 희망 수량이므로 별도의 동시성 제어를 하지 않는다.
   *
   */
  void updateCartItem(UpdateCartItemCommand command);

  /**
   * 회원의 장바구니 물품을 선택하여 삭제한다.
   *
   * 이력 추적이 필요한 정보가 아니므로 Hard Delete를 처리한다.
   * 단건, 다건 삭제를 모두 지원한다.
   *
   */
  void removeCartItem(DeleteCartItemCommand command);
}
