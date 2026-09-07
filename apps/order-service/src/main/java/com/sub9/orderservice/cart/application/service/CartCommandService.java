package com.sub9.orderservice.cart.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.dto.*;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CartCommandService {
  private static final int MAX_CART_ITEM_COUNT = 70;

  private final CartRepository cartRepository;
  private final CartProductPort cartProductPort;

  public void addCartItem(AddCartItemCommand command) {
    UUID cartId = new UuidV7Generator().generate();

    if (cartRepository.countByUserId(command.userId()) >= MAX_CART_ITEM_COUNT) {
      throw new BusinessException(CartErrorCode.CART_ITEM_LIMIT_EXCEEDED);
    }

    cartProductPort.validateSkuForCart(command.skuId());

    Cart cart = Cart.create(cartId, command.userId(), command.skuId(), command.quantity());

    try {
      cartRepository.saveAndFlush(cart);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(CartErrorCode.CART_ITEM_ALREADY_EXISTS);
    }
  }

  public void updateCartItem(UpdateCartItemCommand command) {
    Cart cartItem =
        cartRepository
            .findByIdAndUserId(command.cartId(), command.userId())
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));

    cartItem.changeQuantity(command.quantity());
  }

  // TODO : 상품 삭제 또는 SKU 삭제 시 Kafka로 전파하여 장바구니 항목 정리 필요
  public void removeCartItem(DeleteCartItemCommand command) {
    cartRepository.deleteAllByUserIdAndIdIn(command.userId(), command.cartIds());
  }
}
