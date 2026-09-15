package com.sub9.orderservice.cart.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import com.sub9.orderservice.cart.application.dto.UpdateCartItemCommand;
import com.sub9.orderservice.cart.application.port.in.CartCleanupUseCase;
import com.sub9.orderservice.cart.application.port.in.CartCommandUseCase;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
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
public class CartCommandService implements CartCommandUseCase, CartCleanupUseCase {
  private static final int MAX_CART_ITEM_COUNT = 70;

  private final CartRepository cartRepository;
  private final CartProductPort cartProductPort;

  @Override
  public UUID addCartItem(AddCartItemCommand command) {
    UUID cartId = new UuidV7Generator().generate();

    if (cartRepository.countByUserId(command.userId()) >= MAX_CART_ITEM_COUNT) {
      throw new BusinessException(CartErrorCode.CART_ITEM_LIMIT_EXCEEDED);
    }

    UUID productId = cartProductPort.getValidatedProductIdForCart(command.skuId());

    Cart cart =
        Cart.create(cartId, command.userId(), productId, command.skuId(), command.quantity());

    try {
      cartRepository.saveAndFlush(cart);
      return cartId;
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(CartErrorCode.CART_ITEM_ALREADY_EXISTS);
    }
  }

  @Override
  public void updateCartItem(UpdateCartItemCommand command) {
    Cart cartItem =
        cartRepository
            .findByIdAndUserId(command.cartId(), command.userId())
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));

    cartItem.changeQuantity(command.quantity());
  }

  @Override
  public void removeCartItem(DeleteCartItemCommand command) {
    cartRepository.deleteAllByUserIdAndIdIn(command.userId(), command.cartIds());
  }

  @Override
  public void cleanupByProductId(UUID productId) {
    cartRepository.deleteAllByProductId(productId);
  }

  @Override
  public void cleanupBySkuId(UUID skuId) {
    cartRepository.deleAllBySkuId(skuId);
  }
}
