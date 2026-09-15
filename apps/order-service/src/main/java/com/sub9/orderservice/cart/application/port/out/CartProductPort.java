package com.sub9.orderservice.cart.application.port.out;

import com.sub9.orderservice.cart.application.dto.CartProductInfo;

import java.util.List;
import java.util.UUID;

public interface CartProductPort {
    /**
     * SKU의 판매 상태와 재고를 검증하고 상품 ID를 반환한다.
     *
     * @return SKU가 속한 상품 ID
     */
    UUID getValidatedProductIdForCart(UUID skuId);

    /**
     * 장바구니에 담긴 SKU들의 상품 정보를 조회한다.
     *
     * @return 조회된 SKU 별 상품 정보 목록
     */
    List<CartProductInfo> getCartItemProducts(List<UUID> skuIds);
}
