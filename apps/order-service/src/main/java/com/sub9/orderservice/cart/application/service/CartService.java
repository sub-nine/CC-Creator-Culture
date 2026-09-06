package com.sub9.orderservice.cart.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.dto.CartItemProducInfo;
import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CartService implements CartSnapshotPort {
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

  public List<CartItemProducInfo> getCartItemProducts(UUID skuId) {
    throw new UnsupportedOperationException("개발 중 입니다.");
  }

  @Override
  @Transactional(readOnly = true)
  public List<CartItemSnapshot> getCartItems(UUID customerId, List<UUID> cartItemIds) {
    throw new UnsupportedOperationException("개발 중 입니다.");
    //    List<Cart> carts = cartRepository.findAllByUserIdAndIdIn(customerId, cartItemIds);
    //
    //    if (carts.size() != cartItemIds.size()) throw new
    // BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
    //
    //    return cartProductPort.getCartItemProducts(cartItemIds)
    //            .stream()
    //            .map(this::toSnapshot)
    //            .toList();
  }

  private CartItemSnapshot toSnapshot(CartItemProducInfo item) {
    throw new UnsupportedOperationException("개발 중 입니다.");

    //    return new CartItemSnapshot(
    //            item.cartItemId(),
    //            item.creatorId(),
    //            item.productId(),
    //            item.skuId(),
    //            item.productName(),
    //            item.skuName(),
    //            item.price(),
    //            item.quantity()
    //    );

  }

  public void removeCartItem(DeleteCartItemCommand command) {
    cartRepository.deleteAllByUserIdAndIdIn(command.userId(), command.cartIds());
  }
}
